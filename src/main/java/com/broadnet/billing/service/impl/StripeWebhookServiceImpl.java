package com.broadnet.billing.service.impl;

import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.exception.WebhookProcessingException;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StripeWebhookServiceImpl implements StripeWebhookService {

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    private final BillingWebhookEventRepository webhookEventRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final EntitlementService entitlementService;
    private final BillingInvoiceServiceImpl invoiceService;
    private final BillingNotificationService notificationService;
    private final CompanyBillingService companyBillingService;

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public boolean processWebhook(String signature, String payload) {
        // 1. Verify Stripe signature
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new WebhookProcessingException(
                    WebhookProcessingException.Reason.INVALID_SIGNATURE,
                    "Invalid Stripe signature", e);
        } catch (Exception e) {
            throw new WebhookProcessingException(
                    WebhookProcessingException.Reason.PAYLOAD_PARSE_ERROR,
                    "Failed to parse webhook payload", e);
        }

        // 2. Idempotency check
        if (webhookEventRepository.existsByStripeEventId(event.getId())) {
            log.info("Webhook {} already processed — skipping", event.getId());
            return true;
        }

        // 3. Store event immediately (full payload)
        BillingWebhookEvent stored = storeWebhookEvent(event, payload);

        // 4. Route and process
        try {
            routeEvent(event);
            stored.setProcessed(true);
            stored.setProcessedAt(LocalDateTime.now());
            log.info("Processed webhook {} type={}", event.getId(), event.getType());
        } catch (WebhookProcessingException wpe) {
            stored.setErrorMessage(wpe.getMessage());
            stored.setRetryCount(stored.getRetryCount() + 1);
            log.error("Webhook processing failed for {}: {}", event.getId(), wpe.getMessage(), wpe);
        } catch (Exception e) {
            stored.setErrorMessage(e.getMessage());
            stored.setRetryCount(stored.getRetryCount() + 1);
            log.error("Unexpected error processing webhook {}: {}", event.getId(), e.getMessage(), e);
        }

        webhookEventRepository.save(stored);
        // Always return true — Stripe re-sends on 5xx, we handle retries internally
        return true;
    }

    private void routeEvent(Event event) {
        switch (event.getType()) {
            case "customer.subscription.created"      -> handleSubscriptionCreated(event);
            case "customer.subscription.updated"      -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted"      -> handleSubscriptionDeleted(event);
            case "invoice.payment_succeeded"          -> handlePaymentSucceeded(event);
            case "invoice.payment_failed"             -> handlePaymentFailed(event);
            case "invoice.created"                    -> handleInvoiceCreated(event);
            case "invoice.finalized"                  -> handleInvoiceFinalized(event);
            case "invoice.voided"                     -> handleInvoiceVoided(event);
            case "subscription_schedule.created"      -> handleScheduleCreated(event);
            case "subscription_schedule.updated"      -> handleScheduleUpdated(event);
            case "subscription_schedule.released"     -> handleScheduleReleased(event);
            case "subscription_schedule.completed"    -> handleScheduleCompleted(event);
            case "payment_method.attached"            -> handlePaymentMethodAttached(event);
            case "payment_method.updated"             -> handlePaymentMethodUpdated(event);
            case "payment_method.detached"            -> handlePaymentMethodDetached(event);
            case "invoice.payment_action_required"    -> handlePaymentFailed(event);   // same flow as payment failed
            case "invoice.marked_uncollectible"       -> handleInvoiceMarkedUncollectible(event);
            case "customer.subscription.trial_will_end" -> handleTrialWillEnd(event);
            default -> log.debug("Unhandled webhook event type: {}", event.getType());
        }
    }


    @Transactional
    public void handleInvoiceMarkedUncollectible(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        invoiceService.updateInvoiceStatus(inv.getId(), BillingInvoice.InvoiceStatus.uncollectible);
    }

    @Transactional
    public void handleTrialWillEnd(Event event) {
        Subscription sub = extractObject(event, Subscription.class);
        CompanyBilling billing = resolveBillingByCustomer(sub.getCustomer());
        notificationService.createNotification(
                billing.getCompanyId(),
                BillingNotification.NotificationType.limit_warning,
                "Trial Ending Soon",
                "Your trial ends in 3 days. Add a payment method to continue.",
                BillingNotification.Severity.warning);
    }

    // -------------------------------------------------------------------------
    // Subscription handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void handleSubscriptionCreated(Event event) {
        Subscription sub = extractObject(event, Subscription.class);
        CompanyBilling billing = resolveBillingByCustomer(sub.getCustomer());

        billing.setStripeSubscriptionId(sub.getId());
        billing.setSubscriptionStatus(mapStatus(sub.getStatus()));
        billing.setBillingInterval(mapInterval(sub));
        billing.setPeriodStart(epochToLdt(sub.getCurrentPeriodStart()));
        billing.setPeriodEnd(epochToLdt(sub.getCurrentPeriodEnd()));
        billing.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        // Compute and persist entitlements
        entitlementService.updateEntitlementsFromSubscription(
                billing.getCompanyId(), sub,
                BillingEntitlementHistory.TriggeredBy.webhook, event.getId());

        // Notify
        notificationService.notifySubscriptionActive(
                billing.getCompanyId(),
                billing.getActivePlanCode(),
                billing.getPeriodEnd(),
                event.getId());

        log.info("Handled subscription.created for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleSubscriptionUpdated(Event event) {
        Subscription sub = extractObject(event, Subscription.class);
        CompanyBilling billing = resolveBillingByCustomer(sub.getCustomer());

        String oldPlanCode = billing.getActivePlanCode();
        List<String> oldAddonCodes = billing.getActiveAddonCodes() != null
                ? new ArrayList<>(billing.getActiveAddonCodes()) : List.of();

        CompanyBilling.SubscriptionStatus newStatus = mapStatus(sub.getStatus());
        billing.setSubscriptionStatus(newStatus);
        billing.setBillingInterval(mapInterval(sub));
        billing.setPeriodStart(epochToLdt(sub.getCurrentPeriodStart()));
        billing.setPeriodEnd(epochToLdt(sub.getCurrentPeriodEnd()));
        billing.setCancelAtPeriodEnd(sub.getCancelAtPeriodEnd());
        if (sub.getCancelAt() != null) billing.setCancelAt(epochToLdt(sub.getCancelAt()));
        if (sub.getCanceledAt() != null) billing.setCanceledAt(epochToLdt(sub.getCanceledAt()));

        // Handle payment failure date
        if (newStatus == CompanyBilling.SubscriptionStatus.past_due
                && billing.getPaymentFailureDate() == null) {
            billing.setPaymentFailureDate(LocalDateTime.now());
        }

        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        // Recompute entitlements
        entitlementService.updateEntitlementsFromSubscription(
                billing.getCompanyId(), sub,
                BillingEntitlementHistory.TriggeredBy.webhook, event.getId());

        // Reload billing after entitlement update
        CompanyBilling updated = companyBillingRepository.findByCompanyId(billing.getCompanyId())
                .orElseThrow();
        String newPlanCode = updated.getActivePlanCode();
        List<String> newAddonCodes = updated.getActiveAddonCodes() != null
                ? updated.getActiveAddonCodes() : List.of();

        // Notifications based on what changed
        if (oldPlanCode != null && !oldPlanCode.equals(newPlanCode)) {
            notificationService.notifyPlanChanged(billing.getCompanyId(),
                    oldPlanCode, newPlanCode, event.getId());
        }

        // Detect added/removed addons
        List<String> added = newAddonCodes.stream()
                .filter(a -> !oldAddonCodes.contains(a)).collect(Collectors.toList());
        List<String> removed = oldAddonCodes.stream()
                .filter(a -> !newAddonCodes.contains(a)).collect(Collectors.toList());
        added.forEach(a -> notificationService.notifyAddonAdded(billing.getCompanyId(), a, event.getId()));
        removed.forEach(a -> notificationService.notifyAddonRemoved(billing.getCompanyId(), a, event.getId()));

        if (Boolean.TRUE.equals(sub.getCancelAtPeriodEnd())) {
            notificationService.notifySubscriptionCanceled(billing.getCompanyId(),
                    billing.getPeriodEnd(), event.getId());
        }

        log.info("Handled subscription.updated for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        Subscription sub = extractObject(event, Subscription.class);
        CompanyBilling billing = resolveBillingByCustomer(sub.getCustomer());

        billing.setSubscriptionStatus(CompanyBilling.SubscriptionStatus.canceled);
        billing.setCanceledAt(LocalDateTime.now());
        billing.setServiceRestrictedAt(LocalDateTime.now());
        billing.setRestrictionReason(CompanyBilling.RestrictionReason.canceled);
        billing.setAnswersBlocked(true);
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        notificationService.notifySubscriptionCanceled(billing.getCompanyId(),
                billing.getPeriodEnd(), event.getId());
        log.info("Handled subscription.deleted for companyId={}", billing.getCompanyId());
    }

    // -------------------------------------------------------------------------
    // Invoice handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void handlePaymentSucceeded(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        CompanyBilling billing = resolveBillingByCustomer(inv.getCustomer());

        // Update invoice record
        BillingInvoice saved = null;
        try {
            invoiceService.upsertFromStripeInvoice(billing.getCompanyId(), inv);
        } catch (Exception e) {
            log.warn("Failed to upsert invoice record: {}", e.getMessage());
        }

        // Clear payment failure state
        if (billing.getPaymentFailureDate() != null) {
            billing.setPaymentFailureDate(null);
            billing.setServiceRestrictedAt(null);
            billing.setRestrictionReason(null);
            billing.setAnswersBlocked(false);
        }
        // Restore active status if was past_due
        if (billing.getSubscriptionStatus() == CompanyBilling.SubscriptionStatus.past_due) {
            billing.setSubscriptionStatus(CompanyBilling.SubscriptionStatus.active);
        }
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        // Notify
        Integer amount = inv.getAmountPaid() != null ? inv.getAmountPaid().intValue() : 0;
        notificationService.notifyPaymentSucceeded(billing.getCompanyId(), null, amount, event.getId());
        log.info("Handled invoice.payment_succeeded for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handlePaymentFailed(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        CompanyBilling billing = resolveBillingByCustomer(inv.getCustomer());

        try {
            invoiceService.upsertFromStripeInvoice(billing.getCompanyId(), inv);
        } catch (Exception e) {
            log.warn("Failed to upsert invoice on payment failed: {}", e.getMessage());
        }

        // Set payment failure date (COALESCE — only if not already set)
        if (billing.getPaymentFailureDate() == null) {
            billing.setPaymentFailureDate(LocalDateTime.now());
        }
        billing.setSubscriptionStatus(CompanyBilling.SubscriptionStatus.past_due);
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        Integer amount = inv.getAmountDue() != null ? inv.getAmountDue().intValue() : 0;
        notificationService.notifyPaymentFailed(billing.getCompanyId(), null, amount, event.getId());
        log.info("Handled invoice.payment_failed for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleInvoiceCreated(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        if (inv.getCustomer() == null) return;
        CompanyBilling billing = resolveBillingByCustomer(inv.getCustomer());

        try {
            invoiceService.upsertFromStripeInvoice(billing.getCompanyId(), inv);
        } catch (Exception e) {
            log.warn("Failed to upsert invoice on invoice.created: {}", e.getMessage());
        }

        Integer amount = inv.getTotal() != null ? inv.getTotal().intValue() : 0;
        LocalDateTime dueDate = epochToLdt(inv.getDueDate());
        notificationService.notifyInvoiceCreated(billing.getCompanyId(), null, amount, dueDate, event.getId());
        log.info("Handled invoice.created for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleInvoiceFinalized(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        if (inv.getCustomer() == null) return;
        CompanyBilling billing = resolveBillingByCustomer(inv.getCustomer());
        try {
            invoiceService.upsertFromStripeInvoice(billing.getCompanyId(), inv);
        } catch (Exception e) {
            log.warn("Failed to upsert invoice on finalized: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleInvoiceVoided(Event event) {
        Invoice inv = extractObject(event, Invoice.class);
        try {
            invoiceService.updateInvoiceStatus(inv.getId(), BillingInvoice.InvoiceStatus.void_);
        } catch (ResourceNotFoundException e) {
            log.warn("Invoice {} not found locally for void update", inv.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Subscription schedule handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void handleScheduleCreated(Event event) {
        SubscriptionSchedule schedule = extractObject(event, SubscriptionSchedule.class);
        CompanyBilling billing = resolveBillingBySubscription(schedule.getSubscription());

        billing.setStripeScheduleId(schedule.getId());

        // Extract pending plan from schedule phases (phase[1] is the upcoming phase)
        if (schedule.getPhases() != null && schedule.getPhases().size() > 1) {
            SubscriptionSchedule.Phase nextPhase = schedule.getPhases().get(1);
            if (nextPhase.getItems() != null && !nextPhase.getItems().isEmpty()) {
                String priceId = nextPhase.getItems().get(0).getPrice();
                resolvePendingPlanFromPrice(billing, priceId, nextPhase.getStartDate());
            }
        }
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);
        log.info("Handled subscription_schedule.created for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleScheduleUpdated(Event event) {
        SubscriptionSchedule schedule = extractObject(event, SubscriptionSchedule.class);
        CompanyBilling billing = resolveBillingBySubscription(schedule.getSubscription());
        billing.setStripeScheduleId(schedule.getId());
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);
    }

    @Override
    @Transactional
    public void handleScheduleReleased(Event event) {
        SubscriptionSchedule schedule = extractObject(event, SubscriptionSchedule.class);
        CompanyBilling billing = resolveBillingBySubscription(schedule.getSubscription());
        // Release clears the schedule and pending changes
        billing.setStripeScheduleId(null);
        billing.setPendingPlan(null);
        billing.setPendingPlanCode(null);
        billing.setPendingAddonCodes(null);
        billing.setPendingEffectiveDate(null);
        billing.setLastWebhookAt(LocalDateTime.now());
        companyBillingRepository.save(billing);
        log.info("Handled subscription_schedule.released for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handleScheduleCompleted(Event event) {
        SubscriptionSchedule schedule = extractObject(event, SubscriptionSchedule.class);
        CompanyBilling billing = resolveBillingBySubscription(schedule.getSubscription());

        String oldPlanCode = billing.getActivePlanCode();
        String newPlanCode = billing.getPendingPlanCode();

        // Apply pending changes
        if (billing.getPendingPlan() != null) {
            billing.setActivePlan(billing.getPendingPlan());
        }
        if (newPlanCode != null) {
            billing.setActivePlanCode(newPlanCode);
        }
        if (billing.getPendingAddonCodes() != null) {
            billing.setActiveAddonCodes(billing.getPendingAddonCodes());
        }

        // Clear pending + schedule fields
        billing.setStripeScheduleId(null);
        billing.setPendingPlan(null);
        billing.setPendingPlanCode(null);
        billing.setPendingAddonCodes(null);
        billing.setPendingEffectiveDate(null);
        billing.setLastWebhookAt(LocalDateTime.now());
        billing.setLastSyncAt(LocalDateTime.now());
        companyBillingRepository.save(billing);

        // Recompute entitlements for new plan
        if (newPlanCode != null) {
            String interval = billing.getBillingInterval() != null
                    ? billing.getBillingInterval().name() : "month";
            List<String> addonCodes = billing.getActiveAddonCodes() != null
                    ? billing.getActiveAddonCodes() : List.of();
            com.broadnet.billing.dto.EntitlementsDto ent =
                    entitlementService.computeEntitlements(newPlanCode, addonCodes, interval);
            entitlementService.updateCompanyEntitlements(billing.getCompanyId(), ent,
                    BillingEntitlementHistory.TriggeredBy.webhook, event.getId());
        }

        notificationService.notifyPlanChanged(billing.getCompanyId(),
                oldPlanCode, newPlanCode, event.getId());
        log.info("Handled subscription_schedule.completed for companyId={}, plan {} → {}",
                billing.getCompanyId(), oldPlanCode, newPlanCode);
    }

    // -------------------------------------------------------------------------
    // Payment method handlers
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void handlePaymentMethodAttached(Event event) {
        PaymentMethod pm = extractObject(event, PaymentMethod.class);
        if (pm.getCustomer() == null) return;

        CompanyBilling billing = resolveBillingByCustomer(pm.getCustomer());

        boolean isFirst = paymentMethodRepository.countByCompanyId(billing.getCompanyId()) == 0;

        // Clear existing defaults if setting new default
        if (isFirst) {
            paymentMethodRepository.clearDefaultForCompany(billing.getCompanyId());
        }

        BillingPaymentMethod entity = paymentMethodRepository
                .findByStripePaymentMethodId(pm.getId())
                .orElseGet(BillingPaymentMethod::new);

        entity.setCompanyId(billing.getCompanyId());
        entity.setStripePaymentMethodId(pm.getId());
        entity.setType(BillingPaymentMethod.PaymentMethodType.valueOf(
                pm.getType() != null ? pm.getType() : "card"));
        entity.setIsDefault(isFirst);

        if (pm.getCard() != null) {
            entity.setCardBrand(pm.getCard().getBrand());
            entity.setCardLast4(pm.getCard().getLast4());
            entity.setCardExpMonth(pm.getCard().getExpMonth() != null
                    ? pm.getCard().getExpMonth().intValue() : null);
            entity.setCardExpYear(pm.getCard().getExpYear() != null
                    ? pm.getCard().getExpYear().intValue() : null);
            entity.setIsExpired(false);
        }

        if (pm.getBillingDetails() != null) {
            entity.setBillingDetails(String.format(
                    "{\"name\":\"%s\",\"email\":\"%s\"}",
                    pm.getBillingDetails().getName() != null ? pm.getBillingDetails().getName() : "",
                    pm.getBillingDetails().getEmail() != null ? pm.getBillingDetails().getEmail() : ""));
        }

        paymentMethodRepository.save(entity);
        log.info("Handled payment_method.attached for companyId={}", billing.getCompanyId());
    }

    @Override
    @Transactional
    public void handlePaymentMethodUpdated(Event event) {
        PaymentMethod pm = extractObject(event, PaymentMethod.class);

        paymentMethodRepository.findByStripePaymentMethodId(pm.getId()).ifPresent(entity -> {
            if (pm.getCard() != null) {
                entity.setCardExpMonth(pm.getCard().getExpMonth() != null
                        ? pm.getCard().getExpMonth().intValue() : entity.getCardExpMonth());
                entity.setCardExpYear(pm.getCard().getExpYear() != null
                        ? pm.getCard().getExpYear().intValue() : entity.getCardExpYear());
                entity.setIsExpired(false);
            }
            paymentMethodRepository.save(entity);

            // Check expiry — notify if within 30 days
            if (entity.getCardExpYear() != null && entity.getCardExpMonth() != null) {
                LocalDateTime expiry = LocalDateTime.of(
                        entity.getCardExpYear(), entity.getCardExpMonth(), 1, 0, 0)
                        .plusMonths(1).minusDays(1);
                long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDateTime.now(), expiry);
                if (daysLeft <= 30 && daysLeft > 0) {
                    notificationService.notifyPaymentMethodExpiring(
                            entity.getCompanyId(), (int) daysLeft, entity.getCardLast4());
                }
            }
        });
    }

    @Override
    @Transactional
    public void handlePaymentMethodDetached(Event event) {
        PaymentMethod pm = extractObject(event, PaymentMethod.class);
        paymentMethodRepository.deleteByStripePaymentMethodId(pm.getId());
        log.info("Handled payment_method.detached: {}", pm.getId());
    }

    // -------------------------------------------------------------------------
    // Retry
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public int retryFailedWebhooks(int maxRetries) {
        List<BillingWebhookEvent> failed =
                webhookEventRepository.findFailedWebhooks(maxRetries);
        int successCount = 0;
        for (BillingWebhookEvent webhookEvent : failed) {
            try {
                Event event = Event.GSON.fromJson(webhookEvent.getPayload(), Event.class);
                routeEvent(event);
                webhookEvent.setProcessed(true);
                webhookEvent.setProcessedAt(LocalDateTime.now());
                webhookEvent.setErrorMessage(null);
                successCount++;
            } catch (Exception e) {
                webhookEventRepository.incrementRetryCount(webhookEvent.getId(), e.getMessage());
                log.warn("Retry failed for webhook {}: {}", webhookEvent.getId(), e.getMessage());
            }
            webhookEventRepository.save(webhookEvent);
        }
        log.info("Retried {} failed webhooks, {} succeeded", failed.size(), successCount);
        return successCount;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getWebhookStatistics() {
        return webhookEventRepository.countByEventType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private BillingWebhookEvent storeWebhookEvent(Event event, String payload) {
        BillingWebhookEvent we = BillingWebhookEvent.builder()
                .stripeEventId(event.getId())
                .eventType(event.getType())
                .stripeCustomerId(extractCustomerIdSafe(event))
                .stripeSubscriptionId(extractSubscriptionIdSafe(event))
                .payload(payload)
                .processed(false)
                .retryCount(0)
                .build();
        return webhookEventRepository.save(we);
    }

    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T extractObject(Event event, Class<T> type) {
        return (T) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new WebhookProcessingException(
                        WebhookProcessingException.Reason.PAYLOAD_PARSE_ERROR,
                        "Cannot deserialize " + type.getSimpleName()
                                + " from event: " + event.getId()));
    }

    private CompanyBilling resolveBillingByCustomer(String customerId) {
        return companyBillingRepository.findByStripeCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "stripeCustomerId", customerId));
    }

    private CompanyBilling resolveBillingBySubscription(String subscriptionId) {
        return companyBillingRepository.findByStripeSubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "stripeSubscriptionId", subscriptionId));
    }

    private CompanyBilling.SubscriptionStatus mapStatus(String status) {
        if (status == null) return null;
        return CompanyBilling.SubscriptionStatus.valueOf(status.replace("-", "_"));
    }

    private CompanyBilling.BillingInterval mapInterval(Subscription sub) {
        if (sub.getItems() == null || sub.getItems().getData().isEmpty()) return null;
        String interval = sub.getItems().getData().get(0).getPrice().getRecurring().getInterval();
        return CompanyBilling.BillingInterval.valueOf(interval);
    }

    private LocalDateTime epochToLdt(Long epoch) {
        if (epoch == null) return null;
        return Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private String extractCustomerIdSafe(Event event) {
        try {
            Object obj = event.getDataObjectDeserializer().getObject().orElse(null);
            if (obj instanceof Subscription s) return s.getCustomer();
            if (obj instanceof Invoice i) return i.getCustomer();
            if (obj instanceof PaymentMethod pm) return pm.getCustomer();
        } catch (Exception ignored) {}
        return null;
    }

    private String extractSubscriptionIdSafe(Event event) {
        try {
            Object obj = event.getDataObjectDeserializer().getObject().orElse(null);
            if (obj instanceof Subscription s) return s.getId();
            if (obj instanceof Invoice i) return i.getSubscription();
        } catch (Exception ignored) {}
        return null;
    }

    private void resolvePendingPlanFromPrice(CompanyBilling billing, String priceId, Long startDate) {
        try {
            com.stripe.model.Price price = com.stripe.model.Price.retrieve(priceId);
            if (price.getLookupKey() != null && price.getLookupKey().startsWith("plan_")) {
                // Extract plan code from lookup key "plan_{code}_{interval}"
                String[] parts = price.getLookupKey().split("_", 3);
                if (parts.length >= 2) {
                    String planCode = parts[1];
                    billing.setPendingPlanCode(planCode);
                    billing.setPendingEffectiveDate(epochToLdt(startDate));
                }
            }
        } catch (StripeException e) {
            log.warn("Could not resolve pending plan from priceId={}: {}", priceId, e.getMessage());
        }
    }
}
