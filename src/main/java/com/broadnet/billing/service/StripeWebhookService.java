package com.broadnet.billing.service;

import com.stripe.model.Event;

/**
 * Service for processing Stripe webhook events
 * 
 * Based on Architecture Plan:
 * - Section: Webhook Processing - Complete Guide
 * - Section: Complete List of Webhook Events to Capture
 * - Handles 20 different webhook event types
 */
public interface StripeWebhookService {
    
    /**
     * Main webhook processing entry point
     * Handles idempotency, signature verification, and event routing
     * 
     * @param signature The Stripe-Signature header
     * @param payload The raw webhook payload
     * @return true if processed successfully
     * 
     * Flow:
     * 1. Verify Stripe signature
     * 2. Parse event from payload
     * 3. Check idempotency (existsByStripeEventId)
     * 4. Store event in billing_webhook_events
     * 5. Route to appropriate handler based on event.type
     * 6. Mark as processed
     * 7. Return 200 OK to Stripe
     * 
     * Endpoint: POST /webhooks/stripe
     */
    boolean processWebhook(String signature, String payload);
    
    /**
     * Process customer.subscription.created event
     * Creates new subscription record
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Extract subscription data
     * 2. Update company_billing (subscription_id, status, period dates)
     * 3. Compute and store entitlements
     * 4. Log entitlement history
     * 5. Create notification
     */
    void handleSubscriptionCreated(Event event);
    
    /**
     * Process customer.subscription.updated event
     * Updates subscription state (plan changes, cancellations, etc.)
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Extract subscription data
     * 2. Update company_billing (status, plan, addons, cancel flags)
     * 3. Recompute entitlements if plan/addons changed
     * 4. Log entitlement history
     * 5. Create notifications
     */
    void handleSubscriptionUpdated(Event event);
    
    /**
     * Process customer.subscription.deleted event
     * Marks subscription as canceled
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Update company_billing (status = 'canceled', canceled_at)
     * 2. Set service_restricted_at
     * 3. Log entitlement history
     * 4. Create notification
     */
    void handleSubscriptionDeleted(Event event);
    
    /**
     * Process invoice.payment_succeeded event
     * Confirms successful payment
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Update billing_invoices (status = 'paid', paid_at)
     * 2. Clear payment_failure_date in company_billing
     * 3. Update subscription_status if was past_due
     * 4. Create notification
     */
    void handlePaymentSucceeded(Event event);
    
    /**
     * Process invoice.payment_failed event
     * Handles payment failures and retry logic
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Update billing_invoices (status, attempt_count)
     * 2. Set payment_failure_date in company_billing
     * 3. Update subscription_status to 'past_due'
     * 4. Create notification
     */
    void handlePaymentFailed(Event event);
    
    /**
     * Process invoice.created event
     * Stores new invoice
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Create record in billing_invoices
     * 2. Create notification
     */
    void handleInvoiceCreated(Event event);
    
    /**
     * Process subscription_schedule.created event
     * Handles scheduled plan changes (annual downgrades)
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Extract scheduled plan from phases
     * 2. Update company_billing (pending_plan_id, pending_effective_date)
     * 3. Create notification
     */
    void handleScheduleCreated(Event event);
    
    /**
     * Process subscription_schedule.completed event
     * Applies pending plan changes
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Move pending plan to active plan
     * 2. Recompute entitlements
     * 3. Clear pending fields
     * 4. Log entitlement history
     * 5. Create notification
     */
    void handleScheduleCompleted(Event event);
    
    /**
     * Process payment_method.attached event
     * Stores payment method details
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Create/update billing_payment_methods
     * 2. Create notification if needed
     */
    void handlePaymentMethodAttached(Event event);
    
    /**
     * Process payment_method.updated event
     * Updates payment method (expiration dates, etc.)
     * 
     * @param event The Stripe event
     * 
     * Actions:
     * 1. Update billing_payment_methods
     * 2. Check if expiring soon → create notification
     */
    void handlePaymentMethodUpdated(Event event);
    
    /**
     * Retry processing for failed webhook events
     * Called by scheduled job to retry events with errors
     * 
     * @param maxRetries Maximum retry count (default 3)
     * @return Number of events successfully retried
     */
    int retryFailedWebhooks(int maxRetries);
    
    /**
     * Get webhook processing statistics
     * 
     * @return Map of event types to processing counts
     */
    java.util.Map<String, Long> getWebhookStatistics();
}
