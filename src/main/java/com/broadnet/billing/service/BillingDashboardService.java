package com.broadnet.billing.service;

import com.broadnet.billing.dto.*;
import java.util.List;

public interface BillingDashboardService {

    /**
     * Get complete billing snapshot for dashboard
     */
    BillingSnapshotDto getBillingSnapshot(Long companyId);

    /**
     * Get usage metrics for current period
     */
    UsageMetricsDto getUsageMetrics(Long companyId);

    /**
     * Get current plan details
     */
    CurrentPlanDto getCurrentPlan(Long companyId);

    /**
     * Get invoice history
     */
    org.springframework.data.domain.Page<InvoiceDto> getInvoices(
            Long companyId,
            int page,
            int size
    );

    /**
     * Get upcoming invoice preview
     * Shows what will be charged on next billing date
     */
    UpcomingInvoiceDto getUpcomingInvoice(Long companyId);

    /**
     * Download invoice PDF
     */
    byte[] downloadInvoicePdf(Long companyId, String invoiceId);

    /**
     * Get payment methods
     */
    List<PaymentMethodDto> getPaymentMethods(Long companyId);

    /**
     * Get billing notifications (alerts, warnings)
     */
    List<NotificationDto> getNotifications(Long companyId, boolean unreadOnly);

    /**
     * Mark notification as read
     */
    void markNotificationAsRead(Long notificationId);

    /**
     * Get usage history for charts
     */
    UsageHistoryDto getUsageHistory(Long companyId, int days);

    /**
     * Get entitlement change history (audit log)
     */
    org.springframework.data.domain.Page<EntitlementHistoryDto> getEntitlementHistory(
            Long companyId,
            int page,
            int size
    );
}