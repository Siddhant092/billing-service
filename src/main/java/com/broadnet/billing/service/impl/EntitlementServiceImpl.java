package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EntitlementsDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.OptimisticLockingException;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.EntitlementService;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementServiceImpl implements EntitlementService {

    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingPlansRepository billingPlansRepository;
    private final BillingPlanLimitsRepository planLimitsRepository;
    private final BillingAddonsRepository addonsRepository;
    private final BillingAddonDeltasRepository addonDeltasRepository;
    private final BillingEntitlementHistoryRepository entitlementHistoryRepository;
    private final BillingStripePricesRepository stripePricesRepository;

    // -------------------------------------------------------------------------
    // Core computation — reads from billing_plan_limits + billing_addon_deltas
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public EntitlementsDto computeEntitlements(String planCode, List<String> addonCodes,
                                               String billingInterval) {
        LocalDateTime now = LocalDateTime.now();
        BillingPlanLimit.BillingInterval interval = BillingPlanLimit.BillingInterval.valueOf(billingInterval);

        // 1. Load plan
        BillingPlan plan = billingPlansRepository.findByPlanCodeAndIsActiveTrue(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "planCode", planCode));

        // 2. Load active limits for plan
        List<BillingPlanLimit> limits = planLimitsRepository.findActiveLimitsByPlanId(plan.getId(), now);

        int answersLimit = 0, kbPagesLimit = 0, agentsLimit = 0, usersLimit = 0;

        for (BillingPlanLimit limit : limits) {
            if (limit.getBillingInterval() != interval) continue;
            switch (limit.getLimitType()) {
                case answers_per_period -> answersLimit = limit.getLimitValue();
                case kb_pages           -> kbPagesLimit = limit.getLimitValue();
                case agents             -> agentsLimit  = limit.getLimitValue();
                case users              -> usersLimit   = limit.getLimitValue();
            }
        }

        // 3. Add addon deltas
        if (addonCodes != null && !addonCodes.isEmpty()) {
            List<BillingAddon> addons = addonsRepository.findByAddonCodesAndActive(addonCodes);
            for (BillingAddon addon : addons) {
                List<BillingAddonDelta> deltas =
                        addonDeltasRepository.findActiveDeltasByAddonId(addon.getId(), now);
                for (BillingAddonDelta delta : deltas) {
                    if (delta.getBillingInterval() != BillingAddonDelta.BillingInterval.valueOf(billingInterval))
                        continue;
                    switch (delta.getDeltaType()) {
                        case answers_per_period -> answersLimit += delta.getDeltaValue();
                        case kb_pages           -> kbPagesLimit += delta.getDeltaValue();
                    }
                }
            }
        }

        return EntitlementsDto.builder()
                .planCode(planCode)
                .billingInterval(billingInterval)
                .answersLimit(answersLimit)
                .kbPagesLimit(kbPagesLimit)
                .agentsLimit(agentsLimit)
                .usersLimit(usersLimit)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EntitlementsDto computeEntitlementsForCompany(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        String planCode = billing.getActivePlanCode();
        if (planCode == null) {
            return EntitlementsDto.builder().planCode(null).answersLimit(0)
                    .kbPagesLimit(0).agentsLimit(0).usersLimit(0).build();
        }

        List<String> addonCodes = billing.getActiveAddonCodes() != null
                ? billing.getActiveAddonCodes() : List.of();
        String interval = billing.getBillingInterval() != null
                ? billing.getBillingInterval().name() : "month";

        return computeEntitlements(planCode, addonCodes, interval);
    }

    // -------------------------------------------------------------------------
    // Update from Stripe subscription object
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void updateEntitlementsFromSubscription(Long companyId,
                                                   Subscription stripeSubscription,
                                                   BillingEntitlementHistory.TriggeredBy triggeredBy,
                                                   String stripeEventId) {
        // Extract plan lookup key from subscription items
        String planLookupKey = extractPlanLookupKey(stripeSubscription);
        List<String> addonLookupKeys = extractAddonLookupKeys(stripeSubscription);

        // Resolve plan code from stripe price lookup key
        String planCode = resolvePlanCode(planLookupKey);
        List<String> addonCodes = resolveAddonCodes(addonLookupKeys);

        String interval = stripeSubscription.getItems().getData().stream()
                .filter(item -> item.getPrice().getLookupKey() != null
                        && item.getPrice().getLookupKey().startsWith("plan_"))
                .map(item -> item.getPrice().getRecurring().getInterval())
                .findFirst().orElse("month");

        EntitlementsDto entitlements = computeEntitlements(planCode, addonCodes, interval);
        updateCompanyEntitlements(companyId, entitlements, triggeredBy, stripeEventId);
    }

    // -------------------------------------------------------------------------
    // Persist computed entitlements — optimistic locking with retry
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void updateCompanyEntitlements(Long companyId, EntitlementsDto entitlements,
                                          BillingEntitlementHistory.TriggeredBy triggeredBy,
                                          String stripeEventId) {
        int attempt = 0;
        while (attempt < MAX_OPTIMISTIC_RETRIES) {
            try {
                attempt++;
                CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "CompanyBilling", "companyId", companyId));

                // Snapshot old values for history
                Integer oldAnswers = billing.getEffectiveAnswersLimit();
                Integer oldKb     = billing.getEffectiveKbPagesLimit();
                Integer oldAgents = billing.getEffectiveAgentsLimit();
                Integer oldUsers  = billing.getEffectiveUsersLimit();
                String  oldPlan   = billing.getActivePlanCode();

                // Apply new entitlements
                billing.setActivePlanCode(entitlements.getPlanCode());
                billing.setEffectiveAnswersLimit(entitlements.getAnswersLimit());
                billing.setEffectiveKbPagesLimit(entitlements.getKbPagesLimit());
                billing.setEffectiveAgentsLimit(entitlements.getAgentsLimit());
                billing.setEffectiveUsersLimit(entitlements.getUsersLimit());
                billing.setLastSyncAt(LocalDateTime.now());

                // Unblock answers if new limit > current usage
                if (billing.getAnswersBlocked()
                        && billing.getAnswersUsedInPeriod() < entitlements.getAnswersLimit()) {
                    billing.setAnswersBlocked(false);
                }

                companyBillingRepository.save(billing);

                // Log to entitlement history
                logEntitlementHistory(companyId, oldPlan, entitlements.getPlanCode(),
                        oldAnswers, entitlements.getAnswersLimit(),
                        oldKb, entitlements.getKbPagesLimit(),
                        oldAgents, entitlements.getAgentsLimit(),
                        oldUsers, entitlements.getUsersLimit(),
                        triggeredBy, stripeEventId);

                log.info("Updated entitlements for companyId={}: answers={}, kb={}, agents={}, users={}",
                        companyId, entitlements.getAnswersLimit(), entitlements.getKbPagesLimit(),
                        entitlements.getAgentsLimit(), entitlements.getUsersLimit());
                return;

            } catch (OptimisticLockingFailureException e) {
                if (attempt >= MAX_OPTIMISTIC_RETRIES) {
                    throw new OptimisticLockingException(
                            "Failed to update entitlements for companyId " + companyId
                                    + " after " + MAX_OPTIMISTIC_RETRIES + " retries", e);
                }
                log.warn("Optimistic lock conflict updating entitlements for companyId={}, attempt {}",
                        companyId, attempt);
                // small back-off before retry
                try { Thread.sleep(50L * attempt); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Recompute for all companies on a plan (after admin limit change)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public int recomputeEntitlementsForPlan(String planCode) {
        List<CompanyBilling> billings = companyBillingRepository.findByActivePlanCode(planCode);
        int count = 0;
        for (CompanyBilling billing : billings) {
            try {
                EntitlementsDto entitlements = computeEntitlementsForCompany(billing.getCompanyId());
                updateCompanyEntitlements(billing.getCompanyId(), entitlements,
                        BillingEntitlementHistory.TriggeredBy.admin, null);
                count++;
            } catch (Exception e) {
                log.error("Failed to recompute entitlements for companyId={}: {}",
                        billing.getCompanyId(), e.getMessage());
            }
        }
        log.info("Recomputed entitlements for {} companies on plan {}", count, planCode);
        return count;
    }

    // -------------------------------------------------------------------------
    // Read current snapshot (fast path — no re-computation)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public EntitlementsDto getCurrentEntitlements(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        return EntitlementsDto.builder()
                .planCode(billing.getActivePlanCode())
                .billingInterval(billing.getBillingInterval() != null
                        ? billing.getBillingInterval().name() : null)
                .answersLimit(billing.getEffectiveAnswersLimit())
                .kbPagesLimit(billing.getEffectiveKbPagesLimit())
                .agentsLimit(billing.getEffectiveAgentsLimit())
                .usersLimit(billing.getEffectiveUsersLimit())
                .build();
    }

    // -------------------------------------------------------------------------
    // Preview (no DB write)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public EntitlementsDto previewEntitlements(String planCode, List<String> addonCodes,
                                               String billingInterval) {
        return computeEntitlements(planCode, addonCodes, billingInterval);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractPlanLookupKey(Subscription subscription) {
        return subscription.getItems().getData().stream()
                .filter(item -> item.getPrice().getLookupKey() != null
                        && item.getPrice().getLookupKey().startsWith("plan_"))
                .map(item -> item.getPrice().getLookupKey())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No plan found in subscription: " + subscription.getId()));
    }

    private List<String> extractAddonLookupKeys(Subscription subscription) {
        return subscription.getItems().getData().stream()
                .filter(item -> item.getPrice().getLookupKey() != null
                        && item.getPrice().getLookupKey().startsWith("addon_"))
                .map(item -> item.getPrice().getLookupKey())
                .collect(Collectors.toList());
    }

    private String resolvePlanCode(String lookupKey) {
        // lookup_key format: "plan_{planCode}_{interval}" e.g. "plan_professional_month"
        return stripePricesRepository.findByLookupKey(lookupKey)
                .map(price -> price.getPlan() != null ? price.getPlan().getPlanCode() : null)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice", "lookupKey", lookupKey));
    }

    private List<String> resolveAddonCodes(List<String> lookupKeys) {
        List<String> codes = new ArrayList<>();
        for (String lk : lookupKeys) {
            stripePricesRepository.findByLookupKey(lk).ifPresent(price -> {
                if (price.getAddon() != null) codes.add(price.getAddon().getAddonCode());
            });
        }
        return codes;
    }

    private void logEntitlementHistory(Long companyId,
                                       String oldPlanCode, String newPlanCode,
                                       Integer oldAnswers, Integer newAnswers,
                                       Integer oldKb, Integer newKb,
                                       Integer oldAgents, Integer newAgents,
                                       Integer oldUsers, Integer newUsers,
                                       BillingEntitlementHistory.TriggeredBy triggeredBy,
                                       String stripeEventId) {
        BillingEntitlementHistory.ChangeType changeType =
                (oldPlanCode != null && !oldPlanCode.equals(newPlanCode))
                        ? BillingEntitlementHistory.ChangeType.plan_change
                        : BillingEntitlementHistory.ChangeType.limit_update;

        BillingEntitlementHistory history = BillingEntitlementHistory.builder()
                .companyId(companyId)
                .changeType(changeType)
                .oldPlanCode(oldPlanCode)
                .newPlanCode(newPlanCode)
                .oldAnswersLimit(oldAnswers)
                .newAnswersLimit(newAnswers)
                .oldKbPagesLimit(oldKb)
                .newKbPagesLimit(newKb)
                .oldAgentsLimit(oldAgents)
                .newAgentsLimit(newAgents)
                .oldUsersLimit(oldUsers)
                .newUsersLimit(newUsers)
                .triggeredBy(triggeredBy)
                .stripeEventId(stripeEventId)
                .effectiveDate(LocalDateTime.now())
                .build();

        entitlementHistoryRepository.save(history);
    }
}