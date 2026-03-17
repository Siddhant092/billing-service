package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EntitlementsDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.EntitlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final BillingPlansRepository plansRepository;
    private final BillingPlanLimitsRepository planLimitsRepository;
    private final BillingAddonsRepository addonsRepository;
    private final BillingAddonDeltasRepository addonDeltasRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingEntitlementHistoryRepository entitlementHistoryRepository;

    @Override
    public EntitlementsDto computeEntitlements(String planCode, List<String> addonCodes,
                                               String billingInterval) {
        log.debug("Computing entitlements for plan {} with addons {}", planCode, addonCodes);

        // Get plan
        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planCode));

        // Get plan limits
        LocalDateTime now = LocalDateTime.now();
        List<BillingPlanLimit> limits = planLimitsRepository.findActiveLimitsByPlanIdAndInterval(
                plan.getId(), billingInterval, now);

        int answersLimit = getLimitValue(limits, "answers_per_period");
        int kbPagesLimit = getLimitValue(limits, "kb_pages");
        int agentsLimit = getLimitValue(limits, "agents");
        int usersLimit = getLimitValue(limits, "users");

        // Add addon deltas
        if (addonCodes != null && !addonCodes.isEmpty()) {
            List<BillingAddon> addons = addonsRepository.findByAddonCodesAndActive(addonCodes);
            List<Long> addonIds = addons.stream().map(BillingAddon::getId).collect(Collectors.toList());

            List<BillingAddonDelta> deltas = addonDeltasRepository.findActiveDeltasByAddonIds(
                    addonIds, now);

            for (BillingAddonDelta delta : deltas) {
                if (delta.getBillingInterval().equals(billingInterval)) {
                    if ("answers_per_period".equals(delta.getDeltaType())) {
                        answersLimit += delta.getDeltaValue();
                    } else if ("kb_pages".equals(delta.getDeltaType())) {
                        kbPagesLimit += delta.getDeltaValue();
                    }
                }
            }
        }

        return EntitlementsDto.builder()
                .planCode(planCode)
                .planName(plan.getPlanName())
                .addonCodes(addonCodes != null ? addonCodes : new ArrayList<>())
                .billingInterval(billingInterval)
                .answersLimit(answersLimit)
                .kbPagesLimit(kbPagesLimit)
                .agentsLimit(agentsLimit)
                .usersLimit(usersLimit)
                .build();
    }

    @Override
    @Transactional
    public void updateCompanyEntitlements(Long companyId, EntitlementsDto entitlements,
                                          String triggeredBy, String stripeEventId) {
        log.info("Updating entitlements for company {}", companyId);

        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                        .orElseThrow(() -> new RuntimeException("Company billing not found"));

                // Store old values for history
                Integer oldAnswersLimit = billing.getEffectiveAnswersLimit();
                Integer oldKbPagesLimit = billing.getEffectiveKbPagesLimit();
                Integer oldAgentsLimit = billing.getEffectiveAgentsLimit();
                Integer oldUsersLimit = billing.getEffectiveUsersLimit();
                String oldPlanCode = billing.getActivePlanCode();
                List<String> oldAddonCodes = billing.getActiveAddonCodes();

                // Update entitlements
                billing.setActivePlanCode(entitlements.getPlanCode());
                billing.setEffectiveAnswersLimit(entitlements.getAnswersLimit());
                billing.setEffectiveKbPagesLimit(entitlements.getKbPagesLimit());
                billing.setEffectiveAgentsLimit(entitlements.getAgentsLimit());
                billing.setEffectiveUsersLimit(entitlements.getUsersLimit());
                billing.setActiveAddonCodes(entitlements.getAddonCodes());
                billing.setLastSyncAt(LocalDateTime.now());

                companyBillingRepository.save(billing);

                // Log entitlement history
                logEntitlementChange(companyId, oldPlanCode, entitlements.getPlanCode(),
                        oldAddonCodes, entitlements.getAddonCodes(),
                        oldAnswersLimit, entitlements.getAnswersLimit(),
                        oldKbPagesLimit, entitlements.getKbPagesLimit(),
                        oldAgentsLimit, entitlements.getAgentsLimit(),
                        oldUsersLimit, entitlements.getUsersLimit(),
                        triggeredBy, stripeEventId);

                return;
            } catch (Exception e) {
                if (i == maxRetries - 1) throw e;
                log.warn("Retry {} for updating entitlements", i + 1);
            }
        }
    }

    @Override
    public int recomputeEntitlementsForPlan(String planCode) {
        log.info("Recomputing entitlements for all companies on plan: {}", planCode);

        List<CompanyBilling> companies = companyBillingRepository.findByActivePlanCode(planCode);

        for (CompanyBilling billing : companies) {
            EntitlementsDto entitlements = computeEntitlements(
                    billing.getActivePlanCode(),
                    billing.getActiveAddonCodes(),
                    billing.getBillingInterval()
            );

            updateCompanyEntitlements(billing.getCompanyId(), entitlements, "admin", null);
        }

        return companies.size();
    }

    @Override
    public EntitlementsDto getCurrentEntitlements(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        EntitlementsDto dto = EntitlementsDto.builder()
                .planCode(billing.getActivePlanCode())
                .addonCodes(billing.getActiveAddonCodes())
                .billingInterval(billing.getBillingInterval())
                .answersLimit(billing.getEffectiveAnswersLimit())
                .kbPagesLimit(billing.getEffectiveKbPagesLimit())
                .agentsLimit(billing.getEffectiveAgentsLimit())
                .usersLimit(billing.getEffectiveUsersLimit())
                .answersUsed(billing.getAnswersUsedInPeriod())
                .kbPagesUsed(billing.getKbPagesTotal())
                .agentsUsed(billing.getAgentsTotal())
                .usersUsed(billing.getUsersTotal())
                .answersBlocked(billing.getAnswersBlocked())
                .build();

        return dto;
    }

    @Override
    public EntitlementsDto previewEntitlements(String planCode, List<String> addonCodes,
                                               String billingInterval) {
        return computeEntitlements(planCode, addonCodes, billingInterval);
    }

    private int getLimitValue(List<BillingPlanLimit> limits, String limitType) {
        return limits.stream()
                .filter(l -> l.getLimitType().equals(limitType))
                .findFirst()
                .map(BillingPlanLimit::getLimitValue)
                .orElse(0);
    }

    private void logEntitlementChange(Long companyId, String oldPlanCode, String newPlanCode,
                                      List<String> oldAddonCodes, List<String> newAddonCodes,
                                      Integer oldAnswersLimit, Integer newAnswersLimit,
                                      Integer oldKbPagesLimit, Integer newKbPagesLimit,
                                      Integer oldAgentsLimit, Integer newAgentsLimit,
                                      Integer oldUsersLimit, Integer newUsersLimit,
                                      String triggeredBy, String stripeEventId) {

        String changeType = determineChangeType(oldPlanCode, newPlanCode, oldAddonCodes, newAddonCodes);

        BillingEntitlementHistory history = BillingEntitlementHistory.builder()
                .companyId(companyId)
                .changeType(changeType)
                .oldPlanCode(oldPlanCode)
                .newPlanCode(newPlanCode)
                .oldAddonCodes(oldAddonCodes)
                .newAddonCodes(newAddonCodes)
                .oldAnswersLimit(oldAnswersLimit)
                .newAnswersLimit(newAnswersLimit)
                .oldKbPagesLimit(oldKbPagesLimit)
                .newKbPagesLimit(newKbPagesLimit)
                .oldAgentsLimit(oldAgentsLimit)
                .newAgentsLimit(newAgentsLimit)
                .oldUsersLimit(oldUsersLimit)
                .newUsersLimit(newUsersLimit)
                .triggeredBy(triggeredBy)
                .stripeEventId(stripeEventId)
                .effectiveDate(LocalDateTime.now())
                .build();

        entitlementHistoryRepository.save(history);
    }

    private String determineChangeType(String oldPlan, String newPlan,
                                       List<String> oldAddons, List<String> newAddons) {
        if (oldPlan == null || !oldPlan.equals(newPlan)) {
            return "plan_change";
        }

        if (oldAddons == null) oldAddons = new ArrayList<>();
        if (newAddons == null) newAddons = new ArrayList<>();

        if (newAddons.size() > oldAddons.size()) {
            return "addon_added";
        } else if (newAddons.size() < oldAddons.size()) {
            return "addon_removed";
        }

        return "limit_update";
    }

    @Override
    public EntitlementsDto computeEntitlementsForCompany(Long companyId) {
        log.debug("Computing entitlements for company {}", companyId);

        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        return computeEntitlements(
                billing.getActivePlanCode(),
                billing.getActiveAddonCodes(),
                billing.getBillingInterval()
        );
    }

    @Override
    @Transactional
    public void updateEntitlementsFromSubscription(Long companyId,
                                                   com.stripe.model.Subscription subscription,
                                                   String triggeredBy,
                                                   String stripeEventId) {
        log.info("Updating entitlements from Stripe subscription for company {}", companyId);

        // Extract plan code and addon codes from subscription items
        String planCode = null;
        List<String> addonCodes = new ArrayList<>();
        String billingInterval = null;

        for (com.stripe.model.SubscriptionItem item : subscription.getItems().getData()) {
            com.stripe.model.Price price = item.getPrice();
            if (price.getProductObject() != null && price.getProductObject().getMetadata() != null) {
                String type = price.getProductObject().getMetadata().get("type");

                if ("plan".equals(type)) {
                    planCode = price.getProductObject().getMetadata().get("plan_code");
                    billingInterval = price.getRecurring().getInterval();
                } else if ("addon".equals(type)) {
                    String addonCode = price.getProductObject().getMetadata().get("addon_code");
                    if (addonCode != null) {
                        addonCodes.add(addonCode);
                    }
                }
            }
        }

        if (planCode != null && billingInterval != null) {
            EntitlementsDto entitlements = computeEntitlements(planCode, addonCodes, billingInterval);
            updateCompanyEntitlements(companyId, entitlements, triggeredBy, stripeEventId);
        } else {
            log.error("Failed to extract plan code or billing interval from subscription");
        }
    }

}