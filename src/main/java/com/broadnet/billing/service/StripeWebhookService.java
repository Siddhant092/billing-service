package com.broadnet.billing.service;

import com.stripe.model.Event;
import java.util.Map;

/**
 * Service for processing Stripe webhook events.
 *
 * Architecture Plan: Webhook Processing — Complete Guide
 * Handles all 12 webhook event types defined in the architecture.
 *
 * CHANGES FROM ORIGINAL:
 * - handleInvoiceFinalized: ADDED — architecture plan table shows invoice.finalized
 *   must update billing_invoices.status
 * - handleInvoiceVoided: ADDED — architecture plan table shows invoice.voided
 *   must update billing_invoices.status
 * - handleSubscriptionScheduleUpdated: ADDED — architecture plan table shows
 *   subscription_schedule.updated must be captured
 * - handleSubscriptionScheduleReleased: ADDED — architecture plan table shows
 *   subscription_schedule.released must be captured
 * - handlePaymentMethodDetached: ADDED — architecture plan defines payment_method.detached
 *   should delete from billing_payment_methods
 * - getWebhookStatistics: import fixed (was inline java.util.Map)
 * - All existing methods confirmed correct against architecture webhook event table.
 */
public interface StripeWebhookService {

    /**
     * Main webhook processing entry point.
     * Architecture Plan Webhook Flow:
     * 1. Verify Stripe signature via Stripe.constructEvent()
     * 2. Check idempotency: existsByStripeEventId(event.getId())
     * 3. If already processed → return 200 immediately
     * 4. Store event in billing_webhook_events (full payload)
     * 5. Route to specific handler based on event.getType()
     * 6. Mark webhook as processed (processed=true, processed_at=now)
     * 7. Return 200 OK
     *
     * Endpoint: POST /webhooks/stripe
     *
     * @param signature The Stripe-Signature header
     * @param payload   The raw webhook payload body
     * @return true if processed successfully (return 200 to Stripe regardless)
     */
    boolean processWebhook(String signature, String payload);

    /**
     * Handle customer.subscription.created
     * Architecture Plan: Critical — fires on checkout completion.
     *
     * Actions:
     * 1. Extract subscription data (status, period dates, plan/addon items)
     * 2. Update company_billing (stripe_subscription_id, subscription_status, period_start/end)
     * 3. Compute and store entitlements via EntitlementService
     * 4. Log to billing_entitlement_history (change_type=plan_change, triggered_by=webhook)
     * 5. Create notification: subscription_active
     */
    void handleSubscriptionCreated(Event event);

    /**
     * Handle customer.subscription.updated
     * Architecture Plan: Critical — fires on any subscription change.
     *
     * Actions:
     * 1. Extract status, period dates, cancel flags, plan+addon items
     * 2. Update company_billing with all changed fields
     * 3. Recompute entitlements if plan/addons changed
     * 4. Log entitlement history
     * 5. Create notifications: plan_changed / addon_added / addon_removed /
     *    subscription_canceled (if cancel_at_period_end=true) / subscription_inactive
     */
    void handleSubscriptionUpdated(Event event);

    /**
     * Handle customer.subscription.deleted
     * Architecture Plan: Critical — fires when subscription is fully canceled.
     *
     * Actions:
     * 1. Update company_billing: subscription_status=canceled, canceled_at=now,
     *    service_restricted_at=now, restriction_reason=canceled, answers_blocked=true
     * 2. Log entitlement history (change_type=plan_change)
     * 3. Create notification: subscription_canceled
     */
    void handleSubscriptionDeleted(Event event);

    /**
     * Handle invoice.payment_succeeded
     * Architecture Plan: Critical — fires on every successful payment.
     *
     * Actions:
     * 1. Upsert billing_invoices (status=paid, amount_paid, paid_at)
     * 2. Clear company_billing: payment_failure_date=null, service_restricted_at=null,
     *    restriction_reason=null, answers_blocked=false
     * 3. Update subscription_status=active if was past_due
     * 4. Create notification: payment_succeeded or subscription_renewed (if renewal)
     */
    void handlePaymentSucceeded(Event event);

    /**
     * Handle invoice.payment_failed
     * Architecture Plan: Critical — fires on payment failure.
     *
     * Actions:
     * 1. Upsert billing_invoices (status=open, amount_due)
     * 2. Set company_billing.payment_failure_date=COALESCE(current, now)
     * 3. Set company_billing.subscription_status=past_due
     * 4. Create notification: payment_failed
     */
    void handlePaymentFailed(Event event);

    /**
     * Handle invoice.created
     * Architecture Plan: High priority — fires when new invoice is generated.
     *
     * Actions:
     * 1. Create record in billing_invoices (status=open)
     * 2. Create notification: invoice_created
     */
    void handleInvoiceCreated(Event event);

    /**
     * Handle invoice.finalized
     * Architecture Plan: Medium priority.
     * ADDED: Was missing from original.
     *
     * Actions:
     * 1. Update billing_invoices.status based on finalized state
     */
    void handleInvoiceFinalized(Event event);

    /**
     * Handle invoice.voided
     * Architecture Plan: Medium priority.
     * ADDED: Was missing from original.
     *
     * Actions:
     * 1. Update billing_invoices.status = void
     */
    void handleInvoiceVoided(Event event);

    /**
     * Handle subscription_schedule.created
     * Architecture Plan: High priority — fires when annual downgrade is scheduled.
     *
     * Actions:
     * 1. Extract scheduled plan from schedule phases
     * 2. Update company_billing: stripe_schedule_id, pending_plan_id, pending_plan_code,
     *    pending_effective_date = period_end
     */
    void handleScheduleCreated(Event event);

    /**
     * Handle subscription_schedule.updated
     * Architecture Plan: High priority.
     * ADDED: Was missing from original.
     *
     * Actions:
     * 1. Re-extract scheduled plan from updated phases
     * 2. Update pending_plan fields in company_billing
     */
    void handleScheduleUpdated(Event event);

    /**
     * Handle subscription_schedule.released
     * Architecture Plan: High priority.
     * ADDED: Was missing from original.
     *
     * Actions:
     * 1. Clear stripe_schedule_id from company_billing
     * 2. Apply current subscription state (may cancel pending changes)
     */
    void handleScheduleReleased(Event event);

    /**
     * Handle subscription_schedule.completed
     * Architecture Plan: High priority — fires when pending plan takes effect.
     *
     * Actions:
     * 1. Apply pending changes: active_plan_id=pending_plan_id, active_plan_code=pending_plan_code
     * 2. Recompute entitlements for new plan
     * 3. Clear pending fields and stripe_schedule_id
     * 4. Log entitlement history (change_type=plan_change)
     * 5. Create notification: plan_changed
     */
    void handleScheduleCompleted(Event event);

    /**
     * Handle payment_method.attached
     * Architecture Plan: updates billing_payment_methods.
     *
     * Actions:
     * 1. Upsert billing_payment_methods (stripe_payment_method_id, type, card details)
     * 2. Set is_default=true if this is first payment method for company
     */
    void handlePaymentMethodAttached(Event event);

    /**
     * Handle payment_method.updated
     * Architecture Plan: updates expiry dates, triggers expiry notifications.
     *
     * Actions:
     * 1. Update billing_payment_methods (card_exp_month, card_exp_year, is_expired)
     * 2. If expiring within 30 days → create notification: payment_method_expired
     */
    void handlePaymentMethodUpdated(Event event);

    /**
     * Handle payment_method.detached
     * ADDED: Architecture plan implies payment methods can be removed.
     *
     * Actions:
     * 1. Delete from billing_payment_methods where stripe_payment_method_id=?
     * 2. If was default, promote another payment method to default
     */
    void handlePaymentMethodDetached(Event event);

    /**
     * Retry processing for failed webhook events.
     * Called by scheduled job every 15 minutes.
     * Architecture Plan: "Retry up to 3 times, then alert"
     *
     * @param maxRetries Maximum retry count before giving up
     * @return Number of events successfully retried
     */
    int retryFailedWebhooks(int maxRetries);

    /**
     * Get webhook processing statistics for monitoring dashboard.
     * Returns event type → count mapping.
     *
     * @return Map of event type to processing count
     */
    Map<String, Long> getWebhookStatistics();
}