package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.entity.BillingStripePrice;
import com.broadnet.billing.repository.BillingStripePricesRepository;
import com.broadnet.billing.service.CompanyBillingService;
import com.broadnet.billing.service.EntitlementService;
import com.broadnet.billing.service.SubscriptionManagementService;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.SubscriptionUpdateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of SubscriptionManagementService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionManagementServiceImpl implements SubscriptionManagementService {

    private final CompanyBillingService companyBillingService;
    private final EntitlementService entitlementService;
    private final BillingStripePricesRepository stripePricesRepository;

    @Override
    public SubscriptionDto getCurrentSubscription(Long companyId) {
        log.info("Fetching subscription for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            throw new RuntimeException("No active subscription found");
        }

        try {
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            return SubscriptionDto.builder()
                    .stripeSubscriptionId(subscription.getId())
                    .status(subscription.getStatus())
                    .planCode(billing.getActivePlanCode())
                    .billingInterval(billing.getBillingInterval())
                    .periodStart(LocalDateTime.ofEpochSecond(
                            subscription.getCurrentPeriodStart(), 0, java.time.ZoneOffset.UTC))
                    .periodEnd(LocalDateTime.ofEpochSecond(
                            subscription.getCurrentPeriodEnd(), 0, java.time.ZoneOffset.UTC))
                    .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
                    .activeAddonCodes(billing.getActiveAddonCodes())
                    .build();

        } catch (StripeException e) {
            log.error("Failed to fetch subscription from Stripe", e);
            throw new RuntimeException("Failed to fetch subscription: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionDto changePlan(Long companyId, PlanChangeRequest request) {
        log.info("Changing plan for company {} to {}", companyId, request.getNewPlanCode());

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            throw new RuntimeException("No active subscription to change");
        }

        try {
            // Get new plan price
            BillingStripePrice newPrice = stripePricesRepository
                    .findByPlanCodeAndInterval(request.getNewPlanCode(), request.getBillingInterval())
                    .orElseThrow(() -> new RuntimeException("Price not found for plan"));

            // Retrieve current subscription
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Find the plan item (not addon items)
            SubscriptionItem planItem = subscription.getItems().getData().stream()
                    .filter(item -> {
                        Product product = item.getPrice().getProductObject();
                        return product != null && "plan".equals(product.getMetadata().get("type"));
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Plan item not found"));

            // Update subscription with new plan
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(planItem.getId())
                            .setPrice(newPrice.getStripePriceId())
                            .build())
                    .setProrationBehavior(
                            Boolean.TRUE.equals(request.getProrationBehavior())
                                    ? SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS
                                    : SubscriptionUpdateParams.ProrationBehavior.NONE)
                    .build();

            Subscription updatedSubscription = subscription.update(params);

            // Update local state
            billing.setActivePlanCode(request.getNewPlanCode());
            billing.setBillingInterval(request.getBillingInterval());
            companyBillingService.updateCompanyBilling(billing);

            // Recompute entitlements
            EntitlementsDto entitlements = entitlementService.computeEntitlements(
                    request.getNewPlanCode(),
                    billing.getActiveAddonCodes(),
                    request.getBillingInterval());

            entitlementService.updateCompanyEntitlements(
                    companyId, entitlements, "api", null);

            log.info("Plan changed successfully for company {}", companyId);

            return getCurrentSubscription(companyId);

        } catch (StripeException e) {
            log.error("Failed to change plan for company {}", companyId, e);
            throw new RuntimeException("Failed to change plan: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionDto addAddon(Long companyId, String addonCode) {
        log.info("Adding addon {} to company {}", addonCode, companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            throw new RuntimeException("No active subscription");
        }

        try {
            // Get addon price
            BillingStripePrice addonPrice = stripePricesRepository
                    .findByAddonCodeAndInterval(addonCode, billing.getBillingInterval())
                    .orElseThrow(() -> new RuntimeException("Price not found for addon"));

            // Add addon to subscription
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setPrice(addonPrice.getStripePriceId())
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();

            subscription.update(params);

            // Update local state
            List<String> addonCodes = billing.getActiveAddonCodes();
            if (addonCodes == null) addonCodes = new ArrayList<>();
            addonCodes.add(addonCode);
            billing.setActiveAddonCodes(addonCodes);
            companyBillingService.updateCompanyBilling(billing);

            // Recompute entitlements
            EntitlementsDto entitlements = entitlementService.computeEntitlements(
                    billing.getActivePlanCode(), addonCodes, billing.getBillingInterval());

            entitlementService.updateCompanyEntitlements(
                    companyId, entitlements, "api", null);

            log.info("Addon added successfully for company {}", companyId);

            return getCurrentSubscription(companyId);

        } catch (StripeException e) {
            log.error("Failed to add addon for company {}", companyId, e);
            throw new RuntimeException("Failed to add addon: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionDto removeAddon(Long companyId, String addonCode) {
        log.info("Removing addon {} from company {}", addonCode, companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Find addon item
            SubscriptionItem addonItem = subscription.getItems().getData().stream()
                    .filter(item -> {
                        Product product = item.getPrice().getProductObject();
                        return product != null && addonCode.equals(
                                product.getMetadata().get("addon_code"));
                    })
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Addon not found in subscription"));

            // Remove addon item
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(addonItem.getId())
                            .setDeleted(true)
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();

            subscription.update(params);

            // Update local state
            List<String> addonCodes = billing.getActiveAddonCodes();
            if (addonCodes != null) {
                addonCodes.remove(addonCode);
                billing.setActiveAddonCodes(addonCodes);
                companyBillingService.updateCompanyBilling(billing);
            }

            // Recompute entitlements
            EntitlementsDto entitlements = entitlementService.computeEntitlements(
                    billing.getActivePlanCode(), addonCodes, billing.getBillingInterval());

            entitlementService.updateCompanyEntitlements(
                    companyId, entitlements, "api", null);

            return getCurrentSubscription(companyId);

        } catch (StripeException e) {
            log.error("Failed to remove addon for company {}", companyId, e);
            throw new RuntimeException("Failed to remove addon: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionDto upgradeAddon(Long companyId, String currentAddonCode, String newAddonCode) {
        log.info("Upgrading addon from {} to {} for company {}",
                currentAddonCode, newAddonCode, companyId);

        // Remove current addon and add new one
        removeAddon(companyId, currentAddonCode);
        return addAddon(companyId, newAddonCode);
    }

    @Override
    @Transactional
    public SubscriptionDto cancelSubscription(Long companyId) {
        log.info("Canceling subscription for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Cancel at period end
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build();

            subscription.update(params);

            // Update local state
            billing.setCancelAtPeriodEnd(true);
            companyBillingService.updateCompanyBilling(billing);

            log.info("Subscription will cancel at period end for company {}", companyId);

            return getCurrentSubscription(companyId);

        } catch (StripeException e) {
            log.error("Failed to cancel subscription for company {}", companyId, e);
            throw new RuntimeException("Failed to cancel subscription: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public SubscriptionDto reactivateSubscription(Long companyId) {
        log.info("Reactivating subscription for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Reactivate by removing cancel_at_period_end
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(false)
                    .build();

            subscription.update(params);

            // Update local state
            billing.setCancelAtPeriodEnd(false);
            companyBillingService.updateCompanyBilling(billing);

            log.info("Subscription reactivated for company {}", companyId);

            return getCurrentSubscription(companyId);

        } catch (StripeException e) {
            log.error("Failed to reactivate subscription for company {}", companyId, e);
            throw new RuntimeException("Failed to reactivate subscription: " + e.getMessage());
        }
    }

    @Override
    public SubscriptionPreviewDto previewPlanChange(Long companyId, String newPlanCode,
                                                    String billingInterval) {
        log.info("Previewing plan change for company {} to {}", companyId, newPlanCode);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            // Get new plan price
            BillingStripePrice newPrice = stripePricesRepository
                    .findByPlanCodeAndInterval(newPlanCode, billingInterval)
                    .orElseThrow(() -> new RuntimeException("Price not found"));

            // Get current subscription
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Get upcoming invoice for preview
            com.stripe.param.InvoiceUpcomingParams params =
                    com.stripe.param.InvoiceUpcomingParams.builder()
                            .setCustomer(billing.getStripeCustomerId())
                            .setSubscription(billing.getStripeSubscriptionId())
                            .build();

            Invoice upcomingInvoice = Invoice.upcoming(params);

            // Compute new entitlements
            EntitlementsDto currentEntitlements = entitlementService.getCurrentEntitlements(companyId);
            EntitlementsDto newEntitlements = entitlementService.computeEntitlements(
                    newPlanCode, billing.getActiveAddonCodes(), billingInterval);

            return SubscriptionPreviewDto.builder()
                    .currentPlanCode(billing.getActivePlanCode())
                    .newPlanCode(newPlanCode)
                    .currentAmountCents((int) (long) subscription.getItems().getData().get(0)
                            .getPrice().getUnitAmount())
                    .newAmountCents(newPrice.getAmountCents())
                    .prorationAmountCents(upcomingInvoice.getAmountDue() != null ? (int) (long) upcomingInvoice.getAmountDue() : 0)
                    .amountDueTodayCents(upcomingInvoice.getAmountDue() != null ? upcomingInvoice.getAmountDue().intValue() : 0)
                    .currency(upcomingInvoice.getCurrency())
                    .effectiveDate(LocalDateTime.now())
                    .changeType("immediate")
                    .currentEntitlements(currentEntitlements)
                    .newEntitlements(newEntitlements)
                    .description("Plan change from " + billing.getActivePlanCode() +
                            " to " + newPlanCode)
                    .build();

        } catch (StripeException e) {
            log.error("Failed to preview plan change for company {}", companyId, e);
            throw new RuntimeException("Failed to preview plan change: " + e.getMessage());
        }
    }
}