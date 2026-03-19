package com.broadnet.billing.service;

/**
 * Service for scheduled billing jobs and maintenance tasks.
 *
 * Architecture Plan: Cron Jobs Setup section — defines all scheduled operations.
 *
 * CHANGES FROM ORIGINAL:
 * - checkAndExpirePaymentMethods: ADDED — architecture plan defines daily job at 09:00 UTC
 *   to find payment methods expiring in next 30 days and create notifications.
 *   Original had sendPaymentMethodExpirationWarnings() but also needs to mark
 *   actually-expired cards as is_expired=true (separate concern).
 * - sendPaymentMethodExpirationWarnings: kept — creates notifications (separate from marking expired)
 * - calculateEnterpriseBillings: ADDED — architecture plan cron job at end-of-month
 *   triggers billing_enterprise_usage_billing calculation
 * - generateEnterpriseInvoices: ADDED — architecture plan 1st of month 00:10 UTC
 *   generates Stripe invoices for calculated enterprise billing records
 * - cleanupExpiredNotifications: ADDED — architecture plan notifications have expires_at;
 *   a cron job should purge them
 * - All original methods confirmed correct against architecture plan cron job descriptions.
 */
public interface BillingScheduledJobsService {

    /**
     * Reset answer usage for annual plan companies.
     * Architecture Plan: Daily at 00:05 UTC
     * Cron: @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find annual subscriptions where DAY(today) = answers_reset_day
     *    AND (answers_period_start IS NULL OR month/year doesn't match today)
     * 2. For each: Reset answers_used_in_period=0, answers_period_start=today,
     *    answers_blocked=false, increment version
     * 3. Log reset in billing_usage_logs
     *
     * @return Number of companies reset
     */
    int resetAnnualAnswerUsage();

    /**
     * Apply payment failure service restrictions.
     * Architecture Plan: Daily at 01:00 UTC
     * Cron: @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find companies where payment_failure_date IS NOT NULL
     *    AND service_restricted_at IS NULL
     *    AND NOW() >= payment_failure_date + 7 days
     *    AND subscription_status IN (past_due, unpaid)
     * 2. For each: Set service_restricted_at=now, restriction_reason=payment_failed,
     *    answers_blocked=true, increment version
     * 3. Create notification (subscription_inactive)
     *
     * @return Number of companies restricted
     */
    int applyPaymentFailureRestrictions();

    /**
     * Apply pending plan changes whose effective date has arrived.
     * Architecture Plan: Hourly
     * Cron: @Scheduled(cron = "0 0 * * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find companies where pending_plan_code IS NOT NULL
     *    AND pending_effective_date <= now
     * 2. For each: Promote pending plan to active, recompute entitlements,
     *    clear pending fields, log entitlement history, create notification
     *
     * @return Number of plan changes applied
     */
    int applyPendingPlanChanges();

    /**
     * Send usage limit warning notifications.
     * Architecture Plan: Daily at 10:00 UTC
     * Cron: @Scheduled(cron = "0 0 10 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find companies where usage >= 80% of limit (answers + KB pages)
     * 2. For each, check if limit_warning notification already sent this period
     * 3. If not: create limit_warning notification
     *
     * @return Number of warnings sent
     */
    int sendUsageLimitWarnings();

    /**
     * Retry failed webhook events.
     * Architecture Plan: Every 5 minutes (original), every 15 minutes (schedule doc).
     * Cron: @Scheduled(cron = "0 /15 * * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find unprocessed webhooks with errorMessage IS NOT NULL AND retryCount < 3
     * 2. Re-process each via StripeWebhookService
     * 3. On success: mark processed=true
     * 4. On failure: increment retry_count, update error_message
     * 5. If retryCount >= 3: send alert (log/monitor)
     *
     * @return Number of events successfully retried
     */
    int retryFailedWebhooks();

    /**
     * Clean up old processed webhook events.
     * Architecture Plan: Weekly on Sunday at 02:00 UTC
     * Cron: @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
     *
     * Flow:
     * 1. Delete processed webhooks where processedAt < 90 days ago
     * 2. Keep failed/unprocessed webhooks for manual review
     *
     * @return Number of events deleted
     */
    int cleanupOldWebhookEvents();

    /**
     * Sync subscription states from Stripe to detect drift.
     * Architecture Plan: Daily at 03:00 UTC
     * Cron: @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find all companies with stripe_subscription_id IS NOT NULL
     * 2. For each, fetch subscription from Stripe API
     * 3. Compare status, period dates, plan, addons with local state
     * 4. If differences: update company_billing, log discrepancy
     *
     * @return Number of companies synced (updated)
     */
    int syncSubscriptionStatesFromStripe();

    /**
     * Send payment method expiration warnings.
     * Architecture Plan: Daily at 09:00 UTC
     * Cron: @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find payment methods where is_expired=false AND card expires within 30 days
     * 2. For each company: create payment_method_expired notification if not already sent
     *
     * @return Number of warnings sent
     */
    int sendPaymentMethodExpirationWarnings();

    /**
     * Mark actually-expired payment methods as is_expired=true.
     * ADDED: Separate from warning notifications — this updates the data.
     * Cron: @Scheduled(cron = "0 0 0 * * *", zone = "UTC") — daily at midnight UTC
     *
     * Flow:
     * 1. Find payment methods where is_expired=false AND
     *    (cardExpYear < currentYear OR (cardExpYear=currentYear AND cardExpMonth < currentMonth))
     * 2. Mark each as is_expired=true
     *
     * @return Number of payment methods marked expired
     */
    int checkAndExpirePaymentMethods();

    /**
     * Reset monthly usage counters for monthly subscriptions.
     * Architecture Plan: Daily at 00:10 UTC (catches missed webhook resets)
     * Cron: @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
     *
     * Flow:
     * 1. Find monthly subscriptions where period_end < now AND subscription_status=active
     * 2. For each: Reset answers_used_in_period=0, answers_blocked=false,
     *    answers_period_start=period_start, increment version
     *
     * Note: This is a safety net — primary reset happens via webhook.
     *
     * @return Number of companies reset
     */
    int resetMonthlyUsageCounters();

    /**
     * Calculate enterprise usage billing records at end of billing period.
     * ADDED: Architecture Plan — Enterprise Billing Cycles section.
     * Cron: @Scheduled(cron = "0 0 0 1 * *", zone = "UTC") — 1st of month at midnight
     *
     * Flow:
     * 1. Find enterprise companies whose billing_period_end has passed (status=pending)
     * 2. For each: Apply enterprise pricing to usage counts
     * 3. Calculate amounts per metric, subtotal, tax, total
     * 4. Update billing_status=calculated
     *
     * @return Number of billing records calculated
     */
    int calculateEnterpriseBillings();

    /**
     * Generate Stripe invoices for calculated enterprise billing records.
     * ADDED: Architecture Plan — Invoice Generation Process section.
     * Cron: @Scheduled(cron = "0 10 0 1 * *", zone = "UTC") — 1st of month at 00:10 UTC
     *
     * Flow:
     * 1. Find billing_enterprise_usage_billing where billing_status=calculated
     * 2. For each: Create Stripe Invoice with line items (answers, kb_pages, agents, users)
     * 3. Finalize invoice (auto-send to customer)
     * 4. Update: stripe_invoice_id, billing_status=invoiced, invoiced_at=now
     * 5. Create record in billing_invoices
     * 6. Create notification: invoice_created
     *
     * @return Number of invoices generated
     */
    int generateEnterpriseInvoices();

    /**
     * Clean up expired billing notifications.
     * ADDED: Architecture Plan — notifications have expires_at timestamp.
     * Cron: @Scheduled(cron = "0 0 4 * * *", zone = "UTC") — daily at 04:00 UTC
     *
     * Flow:
     * 1. Delete billing_notifications where expires_at <= now
     *
     * @return Number of notifications deleted
     */
    int cleanupExpiredNotifications();
}