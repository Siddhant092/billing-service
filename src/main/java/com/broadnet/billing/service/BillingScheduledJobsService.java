package com.broadnet.billing.service;

/**
 * Service for scheduled billing jobs and maintenance tasks
 * 
 * Based on Architecture Plan:
 * - Section: Cron Jobs Setup
 * - Handles periodic usage resets, payment failure checks, cleanup
 */
public interface BillingScheduledJobsService {
    
    /**
     * Reset answer usage for annual plans
     * Runs daily at 00:05 UTC
     * 
     * Schedule: @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find all annual subscriptions where DAY(today) = answers_reset_day
     * 2. For each:
     *    - Reset answers_used_in_period = 0
     *    - Set answers_period_start = today
     *    - Set answers_blocked = false
     *    - Log in billing_usage_logs
     * 
     * Returns: Number of companies reset
     */
    int resetAnnualAnswerUsage();
    
    /**
     * Apply payment failure restrictions
     * Runs daily at 01:00 UTC
     * 
     * Schedule: @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find companies where payment_failure_date > 7 days ago
     * 2. For each:
     *    - Set service_restricted_at = now
     *    - Set restriction_reason = 'payment_failed'
     *    - Set answers_blocked = true
     *    - Create notification
     * 
     * Returns: Number of companies restricted
     */
    int applyPaymentFailureRestrictions();
    
    /**
     * Apply pending plan changes
     * Runs hourly to check for scheduled plan changes
     * 
     * Schedule: @Scheduled(cron = "0 0 * * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find companies where pending_effective_date <= now
     * 2. For each:
     *    - Move pending plan to active plan
     *    - Recompute entitlements
     *    - Clear pending fields
     *    - Log in entitlement history
     * 
     * Returns: Number of plan changes applied
     */
    int applyPendingPlanChanges();
    
    /**
     * Send usage limit warnings
     * Runs daily at 10:00 UTC
     * 
     * Schedule: @Scheduled(cron = "0 0 10 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find companies where usage >= 80% of limit
     * 2. For each:
     *    - Create notification if not already sent this period
     *    - Send email alert
     * 
     * Returns: Number of warnings sent
     */
    int sendUsageLimitWarnings();
    
    /**
     * Retry failed webhook events
     * Runs every 15 minutes
     * 
     * Schedule: @Scheduled(cron = "0 / 15 * * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find unprocessed webhooks with errors
     * 2. Retry processing (max 3 attempts)
     * 3. Update retry_count
     * 4. If max retries exceeded, send alert
     * 
     * Returns: Number of events successfully retried
     */
    int retryFailedWebhooks();
    
    /**
     * Clean up old webhook events
     * Runs weekly on Sunday at 02:00 UTC
     * 
     * Schedule: @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
     * 
     * Flow:
     * 1. Delete processed webhooks older than 90 days
     * 2. Keep failed/unprocessed webhooks for manual review
     * 
     * Returns: Number of events deleted
     */
    int cleanupOldWebhookEvents();
    
    /**
     * Sync subscription states from Stripe
     * Runs daily at 03:00 UTC to detect any drift
     * 
     * Schedule: @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Get all active subscriptions
     * 2. For each, fetch from Stripe API
     * 3. Compare with local state
     * 4. Update if differences found
     * 5. Log discrepancies
     * 
     * Returns: Number of syncs performed
     */
    int syncSubscriptionStatesFromStripe();
    
    /**
     * Send payment method expiration warnings
     * Runs daily at 09:00 UTC
     * 
     * Schedule: @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find payment methods expiring in next 30 days
     * 2. Create notifications
     * 3. Send email alerts
     * 
     * Returns: Number of warnings sent
     */
    int sendPaymentMethodExpirationWarnings();
    
    /**
     * Reset monthly usage counters for monthly subscriptions
     * Runs daily at 00:10 UTC
     * 
     * Schedule: @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
     * 
     * Flow:
     * 1. Find companies where period_end < now
     * 2. For each:
     *    - Reset answers_used_in_period = 0
     *    - Set answers_period_start = period_start
     *    - Set answers_blocked = false
     * 
     * Note: This catches any missed resets from webhooks
     * 
     * Returns: Number of companies reset
     */
    int resetMonthlyUsageCounters();
}
