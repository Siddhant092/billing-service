package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.CheckoutSessionRequest;
import com.broadnet.billing.dto.CheckoutSessionResponse;
import com.broadnet.billing.entity.BillingStripePrice;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.repository.BillingStripePricesRepository;
import com.broadnet.billing.service.CheckoutService;
import com.broadnet.billing.service.CompanyBillingService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of CheckoutService for Stripe checkout operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CompanyBillingService companyBillingService;
    private final BillingStripePricesRepository stripePricesRepository;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Override
    public CheckoutSessionResponse createCheckoutSession(Long companyId, CheckoutSessionRequest request) {
        log.info("Creating checkout session for company {} with plan {}",
                companyId, request.getPlanCode());

        try {
            CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

            // Get Stripe price ID
            BillingStripePrice price = stripePricesRepository
                    .findByPlanCodeAndInterval(request.getPlanCode(), request.getBillingInterval())
                    .orElseThrow(() -> new RuntimeException(
                            "Price not found for plan: " + request.getPlanCode()));

            // Create metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("company_id", String.valueOf(companyId));
            metadata.put("plan_code", request.getPlanCode());
            metadata.put("billing_interval", request.getBillingInterval());

            // Build line item
            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                    .setPrice(price.getStripePriceId())
                    .setQuantity(1L)
                    .build();

            // Create checkout session
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(billing.getStripeCustomerId())
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(lineItem)
                    .setSuccessUrl(request.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(request.getCancelUrl())
                    .putAllMetadata(metadata)
                    .setAllowPromotionCodes(true)
                    .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
                    .build();

            Session session = Session.create(params);

            log.info("Checkout session created: {} for company {}", session.getId(), companyId);

            return CheckoutSessionResponse.builder()
                    .checkoutSessionId(session.getId())
                    .url(session.getUrl())
                    .success(true)
                    .message("Checkout session created successfully")
                    .build();

        } catch (StripeException e) {
            log.error("Failed to create checkout session for company {}", companyId, e);
            return CheckoutSessionResponse.builder()
                    .success(false)
                    .message("Failed to create checkout session: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public CheckoutSessionResponse createAddonCheckoutSession(Long companyId, String addonCode,
                                                              String billingInterval) {
        log.info("Creating addon checkout session for company {} with addon {}",
                companyId, addonCode);

        try {
            CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

            if (billing.getStripeSubscriptionId() == null) {
                return CheckoutSessionResponse.builder()
                        .success(false)
                        .message("No active subscription found. Please subscribe to a plan first.")
                        .build();
            }

            // Get Stripe price ID for addon
            BillingStripePrice price = stripePricesRepository
                    .findByAddonCodeAndInterval(addonCode, billingInterval)
                    .orElseThrow(() -> new RuntimeException(
                            "Price not found for addon: " + addonCode));

            // Create metadata
            Map<String, String> metadata = new HashMap<>();
            metadata.put("company_id", String.valueOf(companyId));
            metadata.put("addon_code", addonCode);
            metadata.put("billing_interval", billingInterval);
            metadata.put("subscription_id", billing.getStripeSubscriptionId());

            // Build line item
            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                    .setPrice(price.getStripePriceId())
                    .setQuantity(1L)
                    .build();

            // Create checkout session in payment mode for addon
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(billing.getStripeCustomerId())
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .addLineItem(lineItem)
                    .setSuccessUrl("https://app.broadnet.ai/billing/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("https://app.broadnet.ai/billing")
                    .putAllMetadata(metadata)
                    .build();

            Session session = Session.create(params);

            log.info("Addon checkout session created: {} for company {}", session.getId(), companyId);

            return CheckoutSessionResponse.builder()
                    .checkoutSessionId(session.getId())
                    .url(session.getUrl())
                    .success(true)
                    .message("Addon checkout session created successfully")
                    .build();

        } catch (StripeException e) {
            log.error("Failed to create addon checkout session for company {}", companyId, e);
            return CheckoutSessionResponse.builder()
                    .success(false)
                    .message("Failed to create addon checkout session: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public boolean handleCheckoutSuccess(String sessionId) {
        log.info("Handling checkout success for session: {}", sessionId);

        try {
            Session session = Session.retrieve(sessionId);

            // Extract metadata
            String companyIdStr = session.getMetadata().get("company_id");
            if (companyIdStr == null) {
                log.warn("No company_id in session metadata: {}", sessionId);
                return false;
            }

            Long companyId = Long.parseLong(companyIdStr);
            String subscriptionId = session.getSubscription();

            if (subscriptionId == null) {
                log.warn("No subscription created for session: {}", sessionId);
                return false;
            }

            // Update company billing with subscription ID
            CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);
            billing.setStripeSubscriptionId(subscriptionId);
            billing.setSubscriptionStatus("active");
            companyBillingService.updateCompanyBilling(billing);

            log.info("Checkout success handled for company {}, subscription {}",
                    companyId, subscriptionId);

            return true;

        } catch (StripeException e) {
            log.error("Failed to handle checkout success for session {}", sessionId, e);
            return false;
        }
    }
}