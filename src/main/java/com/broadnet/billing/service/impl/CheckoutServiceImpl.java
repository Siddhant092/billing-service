package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.CheckoutSessionRequest;
import com.broadnet.billing.dto.CheckoutSessionResponse;
import com.broadnet.billing.entity.BillingAddon;
import com.broadnet.billing.entity.BillingPlanLimit;
import com.broadnet.billing.entity.BillingStripePrice;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.exception.BillingStateException;
import com.broadnet.billing.exception.DuplicateResourceException;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.exception.StripeIntegrationException;
import com.broadnet.billing.repository.BillingAddonsRepository;
import com.broadnet.billing.repository.BillingStripePricesRepository;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.CheckoutService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingStripePricesRepository stripePricesRepository;
    private final BillingAddonsRepository addonsRepository;

    // -------------------------------------------------------------------------
    // Plan checkout session
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionResponse createCheckoutSession(Long companyId,
                                                          CheckoutSessionRequest request) {
        // 1. Load company billing to get stripe_customer_id
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        // 2. Look up Stripe price by plan_code + billing_interval
        BillingStripePrice price = stripePricesRepository
                .findByPlanCodeAndInterval(
                        request.getPlanCode(),
                        BillingStripePrice.BillingInterval.valueOf(request.getBillingInterval()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice",
                        "planCode+interval",
                        request.getPlanCode() + "_" + request.getBillingInterval()));

        // 3. Create Stripe Checkout Session
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(billing.getStripeCustomerId())
                    .setSuccessUrl(request.getSuccessUrl())
                    .setCancelUrl(request.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(price.getStripePriceId())
                            .setQuantity(1L)
                            .build())
                    .putMetadata("company_id", String.valueOf(companyId))
                    .putMetadata("plan_code", request.getPlanCode())
                    .build();

            Session session = Session.create(params);

            log.info("Created checkout session {} for companyId={} plan={}",
                    session.getId(), companyId, request.getPlanCode());

            return CheckoutSessionResponse.builder()
                    .checkoutSessionId(session.getId())
                    .url(session.getUrl())
                    .build();

        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to create checkout session for companyId=" + companyId
                            + " plan=" + request.getPlanCode(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Addon checkout session
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CheckoutSessionResponse createAddonCheckoutSession(Long companyId,
                                                               String addonCode,
                                                               BillingPlanLimit.BillingInterval billingInterval) {
        // 1. Validate addon exists and is active
        BillingAddon addon = addonsRepository.findByAddonCodeAndIsActiveTrue(addonCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingAddon", "addonCode", addonCode));

        // 2. Load company billing
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        // 3. Check addon not already purchased
        List<String> activeAddons = billing.getActiveAddonCodes();
        if (activeAddons != null && activeAddons.contains(addonCode)) {
            throw new DuplicateResourceException(
                    "Addon already purchased", "addonCode", addonCode);
        }

        // 4. Look up Stripe price for addon
        BillingStripePrice price = stripePricesRepository
                .findByAddonCodeAndInterval(
                        addonCode,
                        BillingStripePrice.BillingInterval.valueOf(billingInterval.name()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingStripePrice",
                        "addonCode+interval",
                        addonCode + "_" + billingInterval));

        // 5. Verify company has an active subscription to attach addon to
        if (billing.getStripeSubscriptionId() == null) {
            throw new BillingStateException("NO_ACTIVE_SUBSCRIPTION",
                    "Cannot purchase addon: company has no active subscription");
        }

        // 6. Create Checkout Session
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomer(billing.getStripeCustomerId())
                    .setSuccessUrl("https://app.broadnet.ai/billing?addon_success=true")
                    .setCancelUrl("https://app.broadnet.ai/billing?addon_canceled=true")
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(price.getStripePriceId())
                            .setQuantity(1L)
                            .build())
                    .putMetadata("company_id", String.valueOf(companyId))
                    .putMetadata("addon_code", addonCode)
                    .build();

            Session session = Session.create(params);

            log.info("Created addon checkout session {} for companyId={} addon={}",
                    session.getId(), companyId, addonCode);

            return CheckoutSessionResponse.builder()
                    .checkoutSessionId(session.getId())
                    .url(session.getUrl())
                    .addonCode(addonCode)
                    .addonName(addon.getAddonName())
                    .build();

        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to create addon checkout session for addon=" + addonCode, e);
        }
    }

    // -------------------------------------------------------------------------
    // Post-checkout success
    // -------------------------------------------------------------------------

    @Override
    public boolean handleCheckoutSuccess(String sessionId) {
        // Architecture Plan: "Most subscription updates come via webhooks.
        // This is just for immediate UI feedback."
        // Simply verify the session is complete — webhook will handle entitlements.
        try {
            Session session = Session.retrieve(sessionId);
            boolean complete = "complete".equals(session.getStatus());
            log.info("Checkout session {} status: {}", sessionId, session.getStatus());
            return complete;
        } catch (StripeException e) {
            log.warn("Could not retrieve checkout session {}: {}", sessionId, e.getMessage());
            // Return true — webhook will handle the actual state update
            return true;
        }
    }
}
