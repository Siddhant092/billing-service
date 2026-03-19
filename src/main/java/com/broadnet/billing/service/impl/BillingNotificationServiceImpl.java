package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.NotificationDto;
import com.broadnet.billing.entity.BillingNotification;
import com.broadnet.billing.entity.BillingUsageLog;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.repository.BillingNotificationsRepository;
import com.broadnet.billing.service.BillingNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingNotificationServiceImpl implements BillingNotificationService {

    private final BillingNotificationsRepository notificationsRepository;

    // -------------------------------------------------------------------------
    // Core creation
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public NotificationDto createNotification(Long companyId,
                                              BillingNotification.NotificationType type,
                                              String title, String message,
                                              BillingNotification.Severity severity) {
        return createNotificationWithAction(companyId, type, title, message,
                severity, null, null, null, null, null);
    }

    @Override
    @Transactional
    public NotificationDto createNotificationWithAction(Long companyId,
                                                        BillingNotification.NotificationType type,
                                                        String title, String message,
                                                        BillingNotification.Severity severity,
                                                        String actionUrl, String actionText,
                                                        String stripeEventId,
                                                        Map<String, Object> metadata,
                                                        LocalDateTime expiresAt) {
        BillingNotification notification = BillingNotification.builder()
                .companyId(companyId)
                .notificationType(type)
                .title(title)
                .message(message)
                .severity(severity != null ? severity : BillingNotification.Severity.info)
                .isRead(false)
                .actionUrl(actionUrl)
                .actionText(actionText)
                .stripeEventId(stripeEventId)
                .metadata(metadata)
                .expiresAt(expiresAt)
                .build();

        BillingNotification saved = notificationsRepository.save(notification);
        log.debug("Created notification type={} for companyId={}", type, companyId);
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long companyId, int page, int size) {
        return notificationsRepository
                .findByCompanyId(companyId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getUnreadNotifications(Long companyId) {
        return notificationsRepository.findUnreadByCompanyId(companyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Long getUnreadCount(Long companyId) {
        return notificationsRepository.countUnreadByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getErrorNotifications(Long companyId) {
        return notificationsRepository
                .findByCompanyIdAndSeverity(companyId, BillingNotification.Severity.error)
                .stream().filter(n -> !Boolean.TRUE.equals(n.getIsRead()))
                .map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsByType(Long companyId,
                                                        BillingNotification.NotificationType type) {
        return notificationsRepository.findByCompanyIdAndType(companyId, type)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsBySeverity(Long companyId,
                                                            BillingNotification.Severity severity) {
        return notificationsRepository.findByCompanyIdAndSeverity(companyId, severity)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getActiveNotifications(Long companyId) {
        return notificationsRepository.findActiveByCompanyId(companyId, LocalDateTime.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotificationsByDateRange(Long companyId,
                                                             LocalDateTime startDate,
                                                             LocalDateTime endDate) {
        return notificationsRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // State mutations
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationsRepository.markAsRead(notificationId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public int markAllAsRead(Long companyId) {
        return notificationsRepository.markAllAsRead(companyId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        if (!notificationsRepository.existsById(notificationId)) {
            throw new ResourceNotFoundException("BillingNotification", "id", notificationId);
        }
        notificationsRepository.deleteById(notificationId);
    }

    @Override
    @Transactional
    public int deleteExpiredNotifications() {
        return notificationsRepository.deleteExpired(LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // Domain-specific notification creators
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void notifySubscriptionActive(Long companyId, String planCode,
                                         LocalDateTime renewalDate, String stripeEventId) {
        // Deduplicate: skip if same type sent in last 24h
        if (recentUnreadExists(companyId,
                BillingNotification.NotificationType.subscription_active, 24)) return;

        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.subscription_active,
                "Subscription Active",
                "Your " + planCode + " plan is active and will renew on "
                        + formatDate(renewalDate),
                BillingNotification.Severity.success,
                "/billing/subscription", "View Subscription",
                stripeEventId,
                Map.of("planCode", planCode, "renewalDate", renewalDate != null
                        ? renewalDate.toString() : ""),
                null);
    }

    @Override
    @Transactional
    public void notifyPaymentFailed(Long companyId, Long invoiceId,
                                    Integer amountCents, String stripeEventId) {
        // payment_failed: always create new (tracks retry attempts per architecture plan)
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.payment_failed,
                "Payment Failed",
                "We couldn't process your payment of " + formatCents(amountCents)
                        + ". Please update your payment method.",
                BillingNotification.Severity.error,
                "/billing/payment-methods", "Update Payment Method",
                stripeEventId,
                Map.of("invoiceId", invoiceId != null ? invoiceId : 0, "amount", amountCents != null ? amountCents : 0),
                null);
    }

    @Override
    @Transactional
    public void notifyPaymentSucceeded(Long companyId, Long invoiceId,
                                       Integer amountCents, String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.payment_succeeded,
                "Payment Successful",
                "Your payment of " + formatCents(amountCents) + " has been processed successfully.",
                BillingNotification.Severity.success,
                "/billing/invoices", "View Invoice",
                stripeEventId,
                Map.of("invoiceId", invoiceId != null ? invoiceId : 0, "amount", amountCents != null ? amountCents : 0),
                null);
    }

    @Override
    @Transactional
    public void notifyPlanChanged(Long companyId, String oldPlanCode,
                                  String newPlanCode, String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.plan_changed,
                "Plan Changed",
                "Your plan has been changed from " + oldPlanCode + " to " + newPlanCode + ".",
                BillingNotification.Severity.info,
                "/billing/subscription", "View Subscription",
                stripeEventId,
                Map.of("oldPlanCode", oldPlanCode != null ? oldPlanCode : "",
                        "newPlanCode", newPlanCode != null ? newPlanCode : ""),
                null);
    }

    @Override
    @Transactional
    public void notifyAddonAdded(Long companyId, String addonCode, String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.addon_added,
                "Add-on Added",
                "Add-on " + addonCode + " has been added to your subscription.",
                BillingNotification.Severity.success,
                "/billing/subscription", "View Subscription",
                stripeEventId,
                Map.of("addonCode", addonCode),
                null);
    }

    @Override
    @Transactional
    public void notifyAddonRemoved(Long companyId, String addonCode, String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.addon_removed,
                "Add-on Removed",
                "Add-on " + addonCode + " has been removed from your subscription.",
                BillingNotification.Severity.info,
                "/billing/subscription", "View Subscription",
                stripeEventId,
                Map.of("addonCode", addonCode),
                null);
    }

    @Override
    @Transactional
    public void notifyLimitWarning(Long companyId, BillingUsageLog.UsageType metricType,
                                   Double percentageUsed, Integer used, Integer limit,
                                   LocalDateTime resetDate) {
        if (recentUnreadExists(companyId,
                BillingNotification.NotificationType.limit_warning, 24)) return;

        String metricName = metricType.name().replace("_", " ");
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.limit_warning,
                "Usage Limit Warning",
                String.format("You've used %.0f%% of your %s limit (%d of %d).",
                        percentageUsed, metricName, used, limit),
                BillingNotification.Severity.warning,
                "/billing/subscription", "Upgrade Plan",
                null,
                Map.of("metricType", metricType.name(), "used", used, "limit", limit,
                        "percentage", percentageUsed),
                resetDate);
    }

    @Override
    @Transactional
    public void notifyLimitExceeded(Long companyId, BillingUsageLog.UsageType metricType,
                                    Integer used, Integer limit) {
        String metricName = metricType.name().replace("_", " ");
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.limit_exceeded,
                "Usage Limit Exceeded",
                "You've reached your " + metricName + " limit of " + limit + ".",
                BillingNotification.Severity.error,
                "/billing/subscription", "Upgrade Plan",
                null,
                Map.of("metricType", metricType.name(), "used", used, "limit", limit,
                        "isBlocked", true),
                null);
    }

    @Override
    @Transactional
    public void notifyPaymentMethodExpiring(Long companyId, int daysUntilExpiry,
                                            String cardLast4) {
        if (recentUnreadExists(companyId,
                BillingNotification.NotificationType.payment_method_expired, 72)) return;

        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.payment_method_expired,
                "Payment Method Expiring",
                "Your card ending in " + cardLast4 + " expires in " + daysUntilExpiry
                        + " days. Please update your payment method.",
                BillingNotification.Severity.warning,
                "/billing/payment-methods", "Update Payment Method",
                null,
                Map.of("cardLast4", cardLast4, "daysUntilExpiry", daysUntilExpiry),
                null);
    }

    @Override
    @Transactional
    public void notifySubscriptionCanceled(Long companyId, LocalDateTime periodEnd,
                                           String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.subscription_canceled,
                "Subscription Canceled",
                "Your subscription has been canceled. You'll retain access until "
                        + formatDate(periodEnd) + ".",
                BillingNotification.Severity.info,
                "/billing/subscription", "Reactivate Subscription",
                stripeEventId,
                Map.of("periodEnd", periodEnd != null ? periodEnd.toString() : ""),
                periodEnd);
    }

    @Override
    @Transactional
    public void notifyInvoiceCreated(Long companyId, Long invoiceId,
                                     Integer amountCents, LocalDateTime dueDate,
                                     String stripeEventId) {
        createNotificationWithAction(companyId,
                BillingNotification.NotificationType.invoice_created,
                "New Invoice Available",
                "Your invoice for " + formatCents(amountCents) + " is ready.",
                BillingNotification.Severity.info,
                "/billing/invoices", "View Invoice",
                stripeEventId,
                Map.of("invoiceId", invoiceId != null ? invoiceId : 0,
                        "amount", amountCents != null ? amountCents : 0,
                        "dueDate", dueDate != null ? dueDate.toString() : ""),
                null);
    }

    // -------------------------------------------------------------------------
    // Conversion
    // -------------------------------------------------------------------------

    private NotificationDto toDto(BillingNotification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .companyId(n.getCompanyId())
                .type(n.getNotificationType())
                .title(n.getTitle())
                .message(n.getMessage())
                .severity(n.getSeverity())
                .isRead(n.getIsRead())
                .actionUrl(n.getActionUrl())
                .actionText(n.getActionText())
                .stripeEventId(n.getStripeEventId())
                .metadata(n.getMetadata())
                .expiresAt(n.getExpiresAt())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Architecture Plan deduplication:
     * "Same notification type within N hours: update existing instead of creating new"
     */
    private boolean recentUnreadExists(Long companyId,
                                       BillingNotification.NotificationType type,
                                       int withinHours) {
        LocalDateTime since = LocalDateTime.now().minusHours(withinHours);
        return !notificationsRepository
                .findRecentUnreadByType(companyId, type, since).isEmpty();
    }

    private String formatCents(Integer cents) {
        if (cents == null) return "$0.00";
        return String.format("$%.2f", cents / 100.0);
    }

    private String formatDate(LocalDateTime dt) {
        if (dt == null) return "N/A";
        return dt.toLocalDate().toString();
    }
}