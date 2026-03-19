package com.broadnet.billing.service;

import com.broadnet.billing.dto.NotificationDto;
import com.broadnet.billing.entity.BillingNotification;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for in-app billing notifications and alerts.
 *
 * Architecture Plan: billing_notifications table + Notification System (§1.1)
 *
 * CHANGES FROM ORIGINAL:
 * - createNotification: severity param type narrowed to BillingNotification.Severity enum
 * - createNotificationWithAction: severity param → enum; added actionText param
 *   (architecture plan schema has action_text column — was missing from original)
 * - createNotificationWithAction: added stripeEventId param (schema has stripe_event_id)
 * - getCriticalNotifications: renamed to getErrorNotifications — 'critical' severity
 *   does not exist in schema (valid values: info, warning, error, success)
 * - getNotificationsByType: type param changed to BillingNotification.NotificationType enum
 * - getNotificationsBySeverity: severity param changed to BillingNotification.Severity enum
 * - notifyPaymentFailed: added invoiceId, amount params (architecture plan shows
 *   notifications carry metadata like invoiceId and amount)
 * - notifyLimitWarning: limitType param changed to BillingUsageLog.UsageType enum
 * - notifyLimitExceeded: limitType param changed to enum
 * - notifyPaymentMethodExpiring: added cardLast4 param (notification message uses it)
 * - ADDED: createWebhookNotification — single entry point for all webhook-driven
 *   notifications with full metadata support (architecture plan shows all notifications
 *   carry stripeEventId + metadata JSON)
 * - ADDED: notifySubscriptionActive, notifyPlanChanged, notifyAddonAdded,
 *   notifyAddonRemoved — all defined in architecture plan notification types
 */
public interface BillingNotificationService {

    /**
     * Create a basic notification for a company.
     *
     * @param companyId The company ID
     * @param type      Notification type (enum)
     * @param title     Notification title
     * @param message   Notification message
     * @param severity  Severity level (info/warning/error/success)
     * @return Created NotificationDto
     */
    NotificationDto createNotification(
            Long companyId,
            BillingNotification.NotificationType type,
            String title,
            String message,
            BillingNotification.Severity severity
    );

    /**
     * Create a notification with action URL, action text, Stripe event ID, and metadata.
     * This is the full-featured creation method used by webhook handlers.
     *
     * Architecture Plan: Notifications carry actionUrl, actionText, stripeEventId, metadata.
     * FIXED: added actionText, stripeEventId params; severity → enum.
     *
     * @param companyId     The company ID
     * @param type          Notification type
     * @param title         Notification title
     * @param message       Notification message
     * @param severity      Severity level
     * @param actionUrl     URL to take action (e.g. "/billing/payment-methods")
     * @param actionText    Button label (e.g. "Update Payment Method")
     * @param stripeEventId Stripe event ID that triggered this notification (nullable)
     * @param metadata      Additional context as JSON-serializable Map (nullable)
     * @param expiresAt     Auto-dismiss timestamp (nullable)
     * @return Created NotificationDto
     */
    NotificationDto createNotificationWithAction(
            Long companyId,
            BillingNotification.NotificationType type,
            String title,
            String message,
            BillingNotification.Severity severity,
            String actionUrl,
            String actionText,
            String stripeEventId,
            java.util.Map<String, Object> metadata,
            LocalDateTime expiresAt
    );

    /**
     * Get all notifications for a company — paginated, newest-first.
     *
     * @param companyId The company ID
     * @param page      Page number (0-indexed)
     * @param size      Page size
     * @return Paginated NotificationDto list
     */
    Page<NotificationDto> getNotifications(Long companyId, int page, int size);

    /**
     * Get unread notifications for a company.
     * Architecture Plan: "Query billing_notifications where is_read=FALSE"
     *
     * @param companyId The company ID
     * @return List of unread NotificationDto
     */
    List<NotificationDto> getUnreadNotifications(Long companyId);

    /**
     * Get count of unread notifications.
     * Used by dashboard header badge.
     *
     * @param companyId The company ID
     * @return Number of unread notifications
     */
    Long getUnreadCount(Long companyId);

    /**
     * Get unread error-severity notifications for a company.
     * FIXED: renamed from getCriticalNotifications — 'critical' is not a valid severity.
     * Schema severity enum: info, warning, error, success.
     *
     * @param companyId The company ID
     * @return List of unread error-severity notifications
     */
    List<NotificationDto> getErrorNotifications(Long companyId);

    /**
     * Get notifications by type for a company.
     * FIXED: type param changed to BillingNotification.NotificationType enum.
     *
     * @param companyId The company ID
     * @param type      Notification type
     * @return List of matching notifications
     */
    List<NotificationDto> getNotificationsByType(
            Long companyId,
            BillingNotification.NotificationType type
    );

    /**
     * Get notifications by severity for a company.
     * FIXED: severity param changed to BillingNotification.Severity enum.
     *
     * @param companyId The company ID
     * @param severity  Severity level
     * @return List of matching notifications
     */
    List<NotificationDto> getNotificationsBySeverity(
            Long companyId,
            BillingNotification.Severity severity
    );

    /**
     * Get active (non-expired) notifications for a company.
     *
     * @param companyId The company ID
     * @return List of active notifications
     */
    List<NotificationDto> getActiveNotifications(Long companyId);

    /**
     * Mark a single notification as read.
     * Sets is_read=true, read_at=now().
     *
     * @param notificationId The notification ID
     */
    void markAsRead(Long notificationId);

    /**
     * Mark all unread notifications as read for a company.
     *
     * @param companyId The company ID
     * @return Number of notifications marked as read
     */
    int markAllAsRead(Long companyId);

    /**
     * Hard-delete a notification by ID.
     * Architecture Plan §1.8: DELETE /api/billing/notifications/{notificationId}
     *
     * @param notificationId The notification ID
     */
    void deleteNotification(Long notificationId);

    /**
     * Delete all expired notifications (expiresAt <= now).
     * Called by cleanup cron job.
     *
     * @return Number of notifications deleted
     */
    int deleteExpiredNotifications();

    /**
     * Get notifications in a date range for a company.
     *
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate   End date
     * @return List of notifications created in range
     */
    List<NotificationDto> getNotificationsByDateRange(
            Long companyId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    // -------------------------------------------------------------------------
    // Domain-specific notification creators — called by webhook handlers and cron jobs
    // -------------------------------------------------------------------------

    /**
     * Notify that subscription is now active.
     * Triggered by: customer.subscription.created / updated (status → active)
     * Architecture Plan: notification type subscription_active
     *
     * @param companyId    The company ID
     * @param planCode     Active plan code
     * @param renewalDate  Next renewal date
     * @param stripeEventId Stripe event ID
     */
    void notifySubscriptionActive(Long companyId, String planCode, LocalDateTime renewalDate, String stripeEventId);

    /**
     * Notify that payment has failed.
     * Triggered by: invoice.payment_failed
     * Architecture Plan: notification type payment_failed, severity error
     *
     * @param companyId    The company ID
     * @param invoiceId    Internal invoice ID (for metadata)
     * @param amountCents  Amount that failed (cents)
     * @param stripeEventId Stripe event ID
     */
    void notifyPaymentFailed(Long companyId, Long invoiceId, Integer amountCents, String stripeEventId);

    /**
     * Notify that payment succeeded.
     * Triggered by: invoice.payment_succeeded
     *
     * @param companyId    The company ID
     * @param invoiceId    Internal invoice ID
     * @param amountCents  Amount paid (cents)
     * @param stripeEventId Stripe event ID
     */
    void notifyPaymentSucceeded(Long companyId, Long invoiceId, Integer amountCents, String stripeEventId);

    /**
     * Notify that plan has changed.
     * Triggered by: customer.subscription.updated, subscription_schedule.completed
     *
     * @param companyId    The company ID
     * @param oldPlanCode  Previous plan code
     * @param newPlanCode  New plan code
     * @param stripeEventId Stripe event ID
     */
    void notifyPlanChanged(Long companyId, String oldPlanCode, String newPlanCode, String stripeEventId);

    /**
     * Notify that an addon was added.
     * Triggered by: customer.subscription.updated (addon item added)
     *
     * @param companyId    The company ID
     * @param addonCode    Addon code that was added
     * @param stripeEventId Stripe event ID
     */
    void notifyAddonAdded(Long companyId, String addonCode, String stripeEventId);

    /**
     * Notify that an addon was removed.
     * Triggered by: customer.subscription.updated (addon item removed)
     *
     * @param companyId    The company ID
     * @param addonCode    Addon code that was removed
     * @param stripeEventId Stripe event ID
     */
    void notifyAddonRemoved(Long companyId, String addonCode, String stripeEventId);

    /**
     * Notify when usage is approaching the limit (80% threshold).
     * Triggered by: cron job (daily check) or real-time when crossing 80%.
     * Architecture Plan: notification type limit_warning
     * FIXED: metricType param changed to BillingUsageLog.UsageType enum.
     *
     * @param companyId       The company ID
     * @param metricType      Type of metric approaching limit
     * @param percentageUsed  Percentage of limit consumed (e.g. 85.0)
     * @param used            Actual usage count
     * @param limit           Effective limit value
     * @param resetDate       When the limit resets (nullable for non-periodic limits)
     */
    void notifyLimitWarning(
            Long companyId,
            com.broadnet.billing.entity.BillingUsageLog.UsageType metricType,
            Double percentageUsed,
            Integer used,
            Integer limit,
            LocalDateTime resetDate
    );

    /**
     * Notify when usage has exceeded the limit (100%).
     * Triggered by: usage enforcement service when answers_blocked is set to true.
     * Architecture Plan: notification type limit_exceeded, severity error
     * FIXED: metricType param changed to enum.
     *
     * @param companyId  The company ID
     * @param metricType Type of metric that exceeded limit
     * @param used       Actual usage count (= limit)
     * @param limit      Effective limit value
     */
    void notifyLimitExceeded(
            Long companyId,
            com.broadnet.billing.entity.BillingUsageLog.UsageType metricType,
            Integer used,
            Integer limit
    );

    /**
     * Notify about upcoming payment method expiration.
     * Triggered by: daily cron job (30 days before expiry) or payment_method.updated webhook.
     * Architecture Plan: notification type payment_method_expired (misnamed — actually expiring)
     * FIXED: added cardLast4 param (notification message uses "card ending in 4242").
     *
     * @param companyId       The company ID
     * @param daysUntilExpiry Days until the card expires
     * @param cardLast4       Last 4 digits of the card number
     */
    void notifyPaymentMethodExpiring(Long companyId, int daysUntilExpiry, String cardLast4);

    /**
     * Notify that subscription has been canceled.
     * Triggered by: customer.subscription.deleted or cancel_at_period_end set.
     *
     * @param companyId      The company ID
     * @param periodEnd      When access will end (for cancel_at_period_end=true)
     * @param stripeEventId  Stripe event ID
     */
    void notifySubscriptionCanceled(Long companyId, LocalDateTime periodEnd, String stripeEventId);

    /**
     * Notify that a new invoice is available.
     * Triggered by: invoice.created webhook.
     *
     * @param companyId    The company ID
     * @param invoiceId    Internal invoice ID
     * @param amountCents  Invoice amount (cents)
     * @param dueDate      Payment due date
     * @param stripeEventId Stripe event ID
     */
    void notifyInvoiceCreated(
            Long companyId,
            Long invoiceId,
            Integer amountCents,
            LocalDateTime dueDate,
            String stripeEventId
    );
}