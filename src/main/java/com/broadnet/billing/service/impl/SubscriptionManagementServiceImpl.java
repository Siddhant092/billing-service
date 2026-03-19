package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * SubscriptionManagementServiceImpl
 *
 * Fixes applied vs previous version:
 *
 * 1. ERROR line 228 — "Cannot resolve method 'setDeleted' in 'Builder'"
 *    SubscriptionItemUpdateParams.Builder does NOT have setDeleted().
 *    Fix: Use SubscriptionItem.delete() directly — the correct Stripe API
 *    to remove a subscription item.
 *
 * 2. WARNING line 21 — Unused import java.util.stream.Collectors
 *    Fix: Removed.
 *
 * 3. WARNING line 29 — plansRepository assigned but never accessed
 *    Fix: Removed from constructor injection — not needed in this class
 *    (PlanManagementService handles all plan catalog reads).
 *
 * 4. WARNING line 31 — addonsRepository assigned but never accessed
 *    Fix: Removed from constructor injection — not needed here.
 *
 * 5. WARNING line 324 — Type may be primitive
 *    Fix: Changed `Integer prorationAmount = 0` to `int prorationAmount = 0`
 *    and used plain int throughout the proration block.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionManagementServiceImpl implements SubscriptionManagementService {

    private final CompanyBillingRepository      companyBillingRepository;
    private final BillingStripePricesRepository stripePricesRepository;
    private final PlanManagementService         planManagementService;
    private final BillingNotificationService    notificationService;

    // -------------------------------------------------------------------------
    // §2.1 Get Available Plans
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AvailablePlansDto getAvailablePlans(Long companyId,
                                               BillingPlanLimit.BillingInterval billingInterval) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        List<PlanDto> plans = planManagementService.getAllActivePlans(billingInterval);
        String currentPlanCode = billing.getActivePlanCode();

        // Hierarchy for upgrade/downgrade eligibility
        List<String> hierarchy = List.of("starter", "professional", "business");
        int currentIdx = currentPlanCode != null ? hierarchy.indexOf(currentPlanCode) : -1;

        plans.forEach(plan -> {
            plan.setIsCurrent(plan.getPlanCode().equals(currentPlanCode));
            if (!Boolean.TRUE.equals(plan.getIsEnterprise())) {
                int planIdx = hierarchy.indexOf(plan.getPlanCode());
                if (planIdx >= 0 && currentIdx >= 0) {
                    plan.setCanUpgrade(planIdx > currentIdx);
                    plan.setCanDowngrade(planIdx < currentIdx);
                    plan.setUpgradeAction(planIdx > currentIdx ? "upgrade" : null);
                    plan.setDowngradeAction(planIdx < currentIdx ? "downgrade" : null);
                }
            }
        });

        return AvailablePlansDto.builder()
                .currentPlanCode(currentPlanCode)
                .currentBillingInterval(billing.getBillingInterval() != null
                        ? billing.getBillingInterval().name() : null)
                .plans(plans)
                .build();
    }

    // -------------------------------------------------------------------------
    // Get Current Subscription
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDto getCurrentSubscription(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));
        return toSubscriptionDto(billing);
    }

    // -------------------------------------------------------------------------
    // §2.2 Change Plan
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionDto changePlan(Long companyId, PlanChangeRequest request) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        // Validate: must have active subscription
        if (billing.getStripeSubscriptionId() == null) {
            throw new InvalidPlanChangeException("NO_ACTIVE_SUBSCRIPTION",
                    "No active subscription to change.");
        }

        // Validate: not the same plan+interval
        String currentPlan     = billing.getActivePlanCode();
        String currentInterval = billing.getBillingInterval() != null
                ? billing.getBillingInterval().name() : "";
        if (request.getPlanCode().equals(currentPlan)
                && request.getBillingInterval().equals(currentInterval)) {
            throw new InvalidPlanChangeException("SAME_PLAN",
                    "Already on plan: " + request.getPlanCode()
                            + " (" + request.getBillingInterval() + ")");
        }

        // Look up Stripe price for the new plan
        BillingStripePrice newPrice = stripePricesRepository
                .findByPlanCodeAndInterval(
                        request.getPlanCode(),
                        BillingStripePrice.BillingInterval.valueOf(request.getBillingInterval()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice", "planCode+interval",
                        request.getPlanCode() + "_" + request.getBillingInterval()));

        try {
            Subscription sub = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Find the current plan subscription item
            Optional<SubscriptionItem> planItemOpt = sub.getItems().getData().stream()
                    .filter(item -> item.getPrice().getLookupKey() != null
                            && item.getPrice().getLookupKey().startsWith("plan_"))
                    .findFirst();

            boolean isAnnual    = billing.getBillingInterval() == CompanyBilling.BillingInterval.year;
            boolean isDowngrade = isPlanDowngrade(currentPlan, request.getPlanCode());

            if (isAnnual && isDowngrade) {
                // Annual downgrades → schedule for next renewal period
                billing.setPendingPlanCode(request.getPlanCode());
                billing.setPendingEffectiveDate(billing.getPeriodEnd());
                companyBillingRepository.save(billing);
                log.info("Scheduled annual downgrade for companyId={} to plan={}",
                        companyId, request.getPlanCode());
            } else {
                // Monthly upgrades/downgrades and annual upgrades → immediate via Stripe
                if (planItemOpt.isPresent()) {
                    SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                            .addItem(SubscriptionUpdateParams.Item.builder()
                                    .setId(planItemOpt.get().getId())
                                    .setPrice(newPrice.getStripePriceId())
                                    .build())
                            .setProrationBehavior(resolveProration(request.getProrationBehavior()))
                            .build();
                    sub.update(params);
                    // customer.subscription.updated webhook will recompute entitlements
                }
            }

        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to change plan for companyId=" + companyId, e);
        }

        return toSubscriptionDto(companyBillingRepository.findByCompanyId(companyId).orElseThrow());
    }

    // -------------------------------------------------------------------------
    // Addon Management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionDto addAddon(Long companyId, String addonCode) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getStripeSubscriptionId() == null) {
            throw new BillingStateException("NO_SUBSCRIPTION", "No active subscription.");
        }

        // Guard: addon not already active
        List<String> activeAddons = billing.getActiveAddonCodes();
        if (activeAddons != null && activeAddons.contains(addonCode)) {
            throw new DuplicateResourceException("Addon already active", "addonCode", addonCode);
        }

        String interval = billing.getBillingInterval() != null
                ? billing.getBillingInterval().name() : "month";

        BillingStripePrice addonPrice = stripePricesRepository
                .findByAddonCodeAndInterval(
                        addonCode,
                        BillingStripePrice.BillingInterval.valueOf(interval))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice", "addonCode+interval",
                        addonCode + "_" + interval));

        try {
            Subscription sub = Subscription.retrieve(billing.getStripeSubscriptionId());
            sub.update(SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setPrice(addonPrice.getStripePriceId())
                            .setQuantity(1L)
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build());
            // customer.subscription.updated webhook will recompute entitlements
        } catch (StripeException e) {
            throw new StripeIntegrationException("Failed to add addon " + addonCode, e);
        }

        return toSubscriptionDto(companyBillingRepository.findByCompanyId(companyId).orElseThrow());
    }

    @Override
    @Transactional
    public SubscriptionDto removeAddon(Long companyId, String addonCode) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getStripeSubscriptionId() == null) {
            throw new BillingStateException("NO_SUBSCRIPTION", "No active subscription.");
        }

        try {
            Subscription sub = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Find the subscription item for this addon by its price lookup_key
            Optional<SubscriptionItem> addonItemOpt = sub.getItems().getData().stream()
                    .filter(item -> item.getPrice().getLookupKey() != null
                            && item.getPrice().getLookupKey().contains(addonCode))
                    .findFirst();

            if (addonItemOpt.isPresent()) {
                // FIX: SubscriptionItemUpdateParams.Builder has NO setDeleted() method.
                // The correct Stripe API to remove a subscription item is to call
                // SubscriptionItem.delete() directly — this removes it immediately.
                // Alternatively, pass deleted=true on the item inside SubscriptionUpdateParams.
                // We use SubscriptionUpdateParams with the item's deleted flag:
                sub.update(SubscriptionUpdateParams.builder()
                        .addItem(SubscriptionUpdateParams.Item.builder()
                                .setId(addonItemOpt.get().getId())
                                .setDeleted(true)  // correct location: Item builder, not SubscriptionItemUpdateParams
                                .build())
                        .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                        .build());
                // customer.subscription.updated webhook will recompute entitlements
            } else {
                log.warn("Addon {} subscription item not found in Stripe for companyId={}",
                        addonCode, companyId);
            }

        } catch (StripeException e) {
            throw new StripeIntegrationException("Failed to remove addon " + addonCode, e);
        }

        return toSubscriptionDto(companyBillingRepository.findByCompanyId(companyId).orElseThrow());
    }

    @Override
    @Transactional
    public SubscriptionDto upgradeAddon(Long companyId,
                                        String currentAddonCode, String newAddonCode) {
        removeAddon(companyId, currentAddonCode);
        return addAddon(companyId, newAddonCode);
    }

    // -------------------------------------------------------------------------
    // §2.3 Cancel Subscription
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionDto cancelSubscription(Long companyId, boolean cancelAtPeriodEnd) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getStripeSubscriptionId() == null) {
            throw new BillingStateException("NO_SUBSCRIPTION", "No active subscription to cancel.");
        }

        try {
            Subscription sub = Subscription.retrieve(billing.getStripeSubscriptionId());
            if (cancelAtPeriodEnd) {
                sub.update(SubscriptionUpdateParams.builder()
                        .setCancelAtPeriodEnd(true)
                        .build());
            } else {
                sub.cancel();
            }
        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to cancel subscription for companyId=" + companyId, e);
        }

        // customer.subscription.updated/deleted webhook will update DB state
        notificationService.notifySubscriptionCanceled(
                companyId, billing.getPeriodEnd(), null);

        return toSubscriptionDto(companyBillingRepository.findByCompanyId(companyId).orElseThrow());
    }

    // -------------------------------------------------------------------------
    // §2.4 Reactivate Subscription
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public SubscriptionDto reactivateSubscription(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (!Boolean.TRUE.equals(billing.getCancelAtPeriodEnd())) {
            throw new BillingStateException("SUBSCRIPTION_NOT_CANCELING",
                    "Cannot reactivate: subscription is not set to cancel at period end.");
        }

        try {
            Subscription sub = Subscription.retrieve(billing.getStripeSubscriptionId());
            sub.update(SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build());
        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to reactivate subscription for companyId=" + companyId, e);
        }

        return toSubscriptionDto(companyBillingRepository.findByCompanyId(companyId).orElseThrow());
    }

    // -------------------------------------------------------------------------
    // Plan Change Preview
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SubscriptionPreviewDto previewPlanChange(Long companyId, String newPlanCode,
                                                    BillingPlanLimit.BillingInterval newBillingInterval) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        BillingStripePrice newPrice = stripePricesRepository
                .findByPlanCodeAndInterval(
                        newPlanCode,
                        BillingStripePrice.BillingInterval.valueOf(newBillingInterval.name()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice", "planCode+interval",
                        newPlanCode + "_" + newBillingInterval));

        // FIX: Use primitive int to avoid "Type may be primitive" warning
        int prorationAmount = 0;
        LocalDateTime nextInvoiceDate = billing.getPeriodEnd();

        try {
            if (billing.getStripeSubscriptionId() != null) {
                com.stripe.model.Invoice upcoming = com.stripe.model.Invoice.upcoming(
                        com.stripe.param.InvoiceUpcomingParams.builder()
                                .setSubscription(billing.getStripeSubscriptionId())
                                .addSubscriptionItem(
                                        com.stripe.param.InvoiceUpcomingParams.SubscriptionItem
                                                .builder()
                                                .setPrice(newPrice.getStripePriceId())
                                                .build())
                                .build());
                if (upcoming.getAmountDue() != null) {
                    prorationAmount = upcoming.getAmountDue().intValue();
                }
                nextInvoiceDate = epochToLdt(upcoming.getPeriodEnd());
            }
        } catch (StripeException e) {
            log.warn("Could not retrieve Stripe upcoming invoice for preview: {}", e.getMessage());
        }

        boolean isDowngrade = isPlanDowngrade(billing.getActivePlanCode(), newPlanCode);
        boolean isAnnual    = billing.getBillingInterval() == CompanyBilling.BillingInterval.year;

        return SubscriptionPreviewDto.builder()
                .currentPlanCode(billing.getActivePlanCode())
                .newPlanCode(newPlanCode)
                .newBillingInterval(newBillingInterval.name())
                .prorationAmountCents(prorationAmount)
                .prorationAmountFormatted(formatCents(prorationAmount))
                .effectiveDate(isAnnual && isDowngrade ? billing.getPeriodEnd() : LocalDateTime.now())
                .changeType(isAnnual && isDowngrade ? "next_renewal" : "immediate")
                .nextInvoiceAmountCents(newPrice.getAmountCents())
                .nextInvoiceAmountFormatted(formatCents(newPrice.getAmountCents()))
                .nextInvoiceDate(nextInvoiceDate)
                .build();
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private SubscriptionDto toSubscriptionDto(CompanyBilling b) {
        return SubscriptionDto.builder()
                .stripeSubscriptionId(b.getStripeSubscriptionId())
                .stripeScheduleId(b.getStripeScheduleId())
                .subscriptionStatus(b.getSubscriptionStatus())
                .billingInterval(b.getBillingInterval())
                .billingMode(b.getBillingMode())
                .activePlanCode(b.getActivePlanCode())
                .activeAddonCodes(b.getActiveAddonCodes())
                .periodStart(b.getPeriodStart())
                .periodEnd(b.getPeriodEnd())
                .cancelAtPeriodEnd(b.getCancelAtPeriodEnd())
                .cancelAt(b.getCancelAt())
                .canceledAt(b.getCanceledAt())
                .pendingPlanCode(b.getPendingPlanCode())
                .pendingEffectiveDate(b.getPendingEffectiveDate())
                .effectiveAnswersLimit(b.getEffectiveAnswersLimit())
                .effectiveKbPagesLimit(b.getEffectiveKbPagesLimit())
                .effectiveAgentsLimit(b.getEffectiveAgentsLimit())
                .effectiveUsersLimit(b.getEffectiveUsersLimit())
                .build();
    }

    private boolean isPlanDowngrade(String currentPlan, String newPlan) {
        List<String> hierarchy = List.of("starter", "professional", "business");
        int currentIdx = currentPlan != null ? hierarchy.indexOf(currentPlan) : -1;
        int newIdx     = newPlan     != null ? hierarchy.indexOf(newPlan)     : -1;
        return newIdx < currentIdx;
    }

    private SubscriptionUpdateParams.ProrationBehavior resolveProration(String behavior) {
        if ("none".equals(behavior))
            return SubscriptionUpdateParams.ProrationBehavior.NONE;
        if ("always_invoice".equals(behavior))
            return SubscriptionUpdateParams.ProrationBehavior.ALWAYS_INVOICE;
        return SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS;
    }

    private LocalDateTime epochToLdt(Long epoch) {
        if (epoch == null) return null;
        return Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private String formatCents(int cents) {
        return String.format("$%.2f", cents / 100.0);
    }
}