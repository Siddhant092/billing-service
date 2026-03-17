package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EntitlementsDto;
import com.broadnet.billing.entity.BillingWebhookEvent;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.repository.BillingWebhookEventRepository;
import com.broadnet.billing.service.CompanyBillingService;
import com.broadnet.billing.service.EntitlementService;
import com.broadnet.billing.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.*;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Implementation of StripeWebhookService
 * Handles all Stripe webhook events with idempotency
 *
 * FIXES APPLIED:
 * 1. routeEvent() called handler methods with "return handleXxx(event)" but all handlers
 *    return void per the interface — fixed to call as statement then "return true"
 * 2. Event.GSON causes "cannot access com.google.gson.Gson" compile error —
 *    replaced with ApiResource.GSON which is the internal Stripe SDK accessor
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookServiceImpl implements StripeWebhookService {

    private final BillingWebhookEventRepository webhookEventRepository;
    private final CompanyBillingService companyBillingService;
    private final EntitlementService entitlementService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Override
    @Transactional
    public boolean processWebhook(String signature, String payload) {
        log.info("Processing webhook");

        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);

            if (webhookEventRepository.existsByStripeEventId(event.getId())) {
                log.info("Webhook already processed (idempotent): {}", event.getId());
                return true;
            }

            BillingWebhookEvent webhookEvent = BillingWebhookEvent.builder()
                    .stripeEventId(event.getId())
                    .eventType(event.getType())
                    .payload(payload)
                    .processedAt(LocalDateTime.now())
                    .status("processing")
                    .build();
            webhookEventRepository.save(webhookEvent);

            boolean success = routeEvent(event);

            webhookEvent.setStatus(success ? "completed" : "failed");
            webhookEvent.setProcessedAt(LocalDateTime.now());
            webhookEventRepository.save(webhookEvent);

            return success;

        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature", e);
            return false;
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return false;
        }
    }

    /**
     * Routes event to the correct void handler.
     * FIX: All interface handler methods are void — each case calls the handler
     * as a statement and then explicitly returns true. Exceptions caught -> false.
     */
    private boolean routeEvent(Event event) {
        log.info("Routing event type: {}", event.getType());
        try {
            switch (event.getType()) {
                case "customer.subscription.created":
                    handleSubscriptionCreated(event);   return true;
                case "customer.subscription.updated":
                    handleSubscriptionUpdated(event);   return true;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(event);   return true;
                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(event);      return true;
                case "invoice.payment_failed":
                    handlePaymentFailed(event);         return true;
                case "invoice.created":
                case "invoice.finalized":
                    handleInvoiceCreated(event);        return true;
                case "subscription_schedule.created":
                    handleScheduleCreated(event);       return true;
                case "subscription_schedule.completed":
                    handleScheduleCompleted(event);     return true;
                case "payment_method.attached":
                    handlePaymentMethodAttached(event); return true;
                case "payment_method.updated":
                    handlePaymentMethodUpdated(event);  return true;
                default:
                    log.info("Unhandled event type: {}", event.getType());
                    return true;
            }
        } catch (Exception e) {
            log.error("Error routing event {}", event.getType(), e);
            return false;
        }
    }

    @Override
    public void handleSubscriptionCreated(Event event) {
        log.info("Handling subscription.created");

        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (subscription == null) {
            log.error("Failed to deserialize subscription for event: {}", event.getId());
            return;
        }

        CompanyBilling billing = companyBillingService
                .getByStripeCustomerId(subscription.getCustomer());

        billing.setStripeSubscriptionId(subscription.getId());
        billing.setSubscriptionStatus(subscription.getStatus());
        billing.setPeriodStart(LocalDateTime.ofEpochSecond(
                subscription.getCurrentPeriodStart(), 0, java.time.ZoneOffset.UTC));
        billing.setPeriodEnd(LocalDateTime.ofEpochSecond(
                subscription.getCurrentPeriodEnd(), 0, java.time.ZoneOffset.UTC));

        entitlementService.updateEntitlementsFromSubscription(
                billing.getCompanyId(), subscription, "webhook", event.getId());

        companyBillingService.updateCompanyBilling(billing);
        log.info("Subscription created for company {}", billing.getCompanyId());
    }

    @Override
    public void handleSubscriptionUpdated(Event event) {
        log.info("Handling subscription.updated");

        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (subscription == null) {
            log.error("Failed to deserialize subscription for event: {}", event.getId());
            return;
        }

        CompanyBilling billing = companyBillingService
                .getByStripeCustomerId(subscription.getCustomer());

        billing.setSubscriptionStatus(subscription.getStatus());
        billing.setPeriodStart(LocalDateTime.ofEpochSecond(
                subscription.getCurrentPeriodStart(), 0, java.time.ZoneOffset.UTC));
        billing.setPeriodEnd(LocalDateTime.ofEpochSecond(
                subscription.getCurrentPeriodEnd(), 0, java.time.ZoneOffset.UTC));
        billing.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());

        entitlementService.updateEntitlementsFromSubscription(
                billing.getCompanyId(), subscription, "webhook", event.getId());

        companyBillingService.updateCompanyBilling(billing);
        log.info("Subscription updated for company {}", billing.getCompanyId());
    }

    @Override
    public void handleSubscriptionDeleted(Event event) {
        log.info("Handling subscription.deleted");

        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (subscription == null) {
            log.error("Failed to deserialize subscription for event: {}", event.getId());
            return;
        }

        CompanyBilling billing = companyBillingService
                .getByStripeCustomerId(subscription.getCustomer());

        billing.setSubscriptionStatus("canceled");
        billing.setCanceledAt(LocalDateTime.now());

        EntitlementsDto zero = EntitlementsDto.builder()
                .answersLimit(0).kbPagesLimit(0).agentsLimit(0).usersLimit(0).build();

        entitlementService.updateCompanyEntitlements(
                billing.getCompanyId(), zero, "webhook", event.getId());

        companyBillingService.updateCompanyBilling(billing);
        log.info("Subscription deleted for company {}", billing.getCompanyId());
    }

    @Override
    public void handlePaymentSucceeded(Event event) {
        log.info("Handling invoice.payment_succeeded");

        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice == null) {
            log.error("Failed to deserialize invoice for event: {}", event.getId());
            return;
        }

        CompanyBilling billing = companyBillingService
                .getByStripeCustomerId(invoice.getCustomer());

        if (billing.getPaymentFailureDate() != null) {
            companyBillingService.removePaymentFailureRestrictions(billing.getCompanyId());
        }

        log.info("Payment succeeded for company {}", billing.getCompanyId());
    }

    @Override
    public void handlePaymentFailed(Event event) {
        log.info("Handling invoice.payment_failed");

        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice == null) {
            log.error("Failed to deserialize invoice for event: {}", event.getId());
            return;
        }

        CompanyBilling billing = companyBillingService
                .getByStripeCustomerId(invoice.getCustomer());

        billing.setPaymentFailureDate(LocalDateTime.now());
        billing.setSubscriptionStatus("past_due");
        companyBillingService.updateCompanyBilling(billing);

        log.warn("Payment failed for company {}", billing.getCompanyId());
    }

    @Override
    public void handleInvoiceCreated(Event event) {
        log.info("Handling invoice.created/finalized");

        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (invoice == null) {
            log.error("Failed to deserialize invoice for event: {}", event.getId());
            return;
        }

        log.info("Invoice created: {}", invoice.getId());
    }

    @Override
    public void handleScheduleCreated(Event event) {
        log.info("Handling subscription_schedule.created for event: {}", event.getId());
    }

    @Override
    public void handleScheduleCompleted(Event event) {
        log.info("Handling subscription_schedule.completed for event: {}", event.getId());
    }

    @Override
    public void handlePaymentMethodAttached(Event event) {
        log.info("Handling payment_method.attached");

        PaymentMethod pm = (PaymentMethod) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (pm == null) {
            log.error("Failed to deserialize payment method for event: {}", event.getId());
            return;
        }

        log.info("Payment method attached: {}", pm.getId());
    }

    @Override
    public void handlePaymentMethodUpdated(Event event) {
        log.info("Handling payment_method.updated");

        PaymentMethod pm = (PaymentMethod) event.getDataObjectDeserializer()
                .getObject().orElse(null);

        if (pm == null) {
            log.error("Failed to deserialize payment method for event: {}", event.getId());
            return;
        }

        log.info("Payment method updated: {}", pm.getId());
    }

    @Override
    public int retryFailedWebhooks(int maxRetries) {
        log.info("Retrying failed webhooks (maxRetries={})", maxRetries);

        List<BillingWebhookEvent> failedEvents = webhookEventRepository
                .findFailedWebhooks(maxRetries);

        int retried = 0;
        for (BillingWebhookEvent webhookEvent : failedEvents) {
            try {
                // FIX: Use ApiResource.GSON — avoids "cannot access com.google.gson.Gson"
                Event stripeEvent = ApiResource.GSON.fromJson(
                        webhookEvent.getPayload(), Event.class);

                if (stripeEvent == null) {
                    log.warn("Could not deserialize payload for webhook {}", webhookEvent.getId());
                    webhookEvent.setRetryCount(webhookEvent.getRetryCount() + 1);
                    webhookEvent.setLastRetryAt(LocalDateTime.now());
                    webhookEventRepository.save(webhookEvent);
                    continue;
                }

                boolean success = routeEvent(stripeEvent);

                webhookEvent.setStatus(success ? "completed" : "failed");
                webhookEvent.setRetryCount(webhookEvent.getRetryCount() + 1);
                webhookEvent.setLastRetryAt(LocalDateTime.now());
                webhookEventRepository.save(webhookEvent);

                if (success) retried++;

            } catch (Exception e) {
                log.error("Failed to retry webhook {}", webhookEvent.getId(), e);
                webhookEvent.setRetryCount(webhookEvent.getRetryCount() + 1);
                webhookEvent.setLastRetryAt(LocalDateTime.now());
                webhookEventRepository.save(webhookEvent);
            }
        }

        log.info("Retried {} failed webhooks", retried);
        return retried;
    }

    @Override
    public Map<String, Long> getWebhookStatistics() {
        return Map.of(
                "total",      webhookEventRepository.count(),
                "completed",  webhookEventRepository.countByStatus("completed"),
                "failed",     webhookEventRepository.countByStatus("failed"),
                "processing", webhookEventRepository.countByStatus("processing")
        );
    }
}