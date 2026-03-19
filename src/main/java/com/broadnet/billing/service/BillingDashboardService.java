package com.broadnet.billing.service;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.BillingNotification;
import com.broadnet.billing.entity.BillingPlanLimit;
import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Service for the billing dashboard — aggregates data for all dashboard screens.
 *
 * Architecture Plan: UI API Design §1–§5 (Overview, Subscription, Usage, Billing, Admin)
 *
 * CHANGES FROM ORIGINAL:
 * - getNotifications: type param added (architecture plan §1.1 supports type filter)
 * - getNotifications: signature changed to match architecture plan query params
 *   (unreadOnly bool + optional type filter + limit)
 * - getAvailableBoosts: ADDED — architecture plan §1.5 defines this as a distinct endpoint
 * - getOverview: ADDED — architecture plan §1.6 defines an aggregator endpoint
 * - markAllNotificationsAsRead: ADDED — architecture plan §1.7 implies bulk read
 * - dismissNotification: ADDED — architecture plan §1.8 DELETE /notifications/{id}
 * - getAvailablePlans: ADDED — architecture plan §2.1 GET /billing/subscription/plans
 * - All existing methods confirmed correct.
 */
public interface BillingDashboardService {

    /**
     * Get complete billing snapshot for dashboard header/cards.
     * Architecture Plan §1.3: GET /api/billing/billing-snapshot
     *
     * Returns: nextInvoice (from Stripe API), latestInvoice (from billing_invoices),
     *          paymentMethod (from billing_payment_methods where is_default=true)
     *
     * @param companyId The company ID
     * @return BillingSnapshotDto
     */
    BillingSnapshotDto getBillingSnapshot(Long companyId);

    /**
     * Get usage metrics for current billing period.
     * Architecture Plan §1.4: GET /api/billing/usage-metrics
     *
     * Returns: answers (used/limit/remaining/percentage/isBlocked),
     *          kbPages, agents, users — all from company_billing counters
     *
     * @param companyId The company ID
     * @return UsageMetricsDto
     */
    UsageMetricsDto getUsageMetrics(Long companyId);

    /**
     * Get current plan details.
     * Architecture Plan §1.2: GET /api/billing/current-plan
     *
     * @param companyId The company ID
     * @return CurrentPlanDto with plan details, billing interval, renewal date
     */
    CurrentPlanDto getCurrentPlan(Long companyId);

    /**
     * Get all available plans with limits and pricing.
     * Architecture Plan §2.1: GET /api/billing/subscription/plans
     * ADDED: Was missing from original.
     *
     * Returns all active plans with: limits, pricing, isCurrent, canUpgrade/Downgrade flags.
     *
     * @param companyId       The company ID (needed for isCurrent / canUpgrade flags)
     * @param billingInterval Billing interval for pricing display
     * @return List of PlanDto with full details
     */
    List<PlanDto> getAvailablePlans(Long companyId, BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get available boosts (addons) with pricing and purchased status.
     * Architecture Plan §1.5: GET /api/billing/available-boosts
     * ADDED: Was missing from original.
     *
     * @param companyId       The company ID
     * @param billingInterval The billing interval for pricing
     * @return List of AddonDto with isPurchased flag
     */
    List<AddonDto> getAvailableBoosts(Long companyId, BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get aggregated overview combining all dashboard data.
     * Architecture Plan §1.6: GET /api/billing/overview
     * ADDED: Was missing from original — calls all individual methods in parallel.
     *
     * @param companyId       The company ID
     * @param billingInterval Billing interval for plan/boost pricing
     * @return BillingOverviewDto aggregating notifications, currentPlan, snapshot, usage, boosts
     */
    BillingOverviewDto getOverview(Long companyId, BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get paginated invoice history.
     * Architecture Plan §3: GET /api/billing/invoices
     *
     * @param companyId The company ID
     * @param page      Page number (0-indexed)
     * @param size      Page size
     * @return Paginated InvoiceDto list
     */
    Page<InvoiceDto> getInvoices(Long companyId, int page, int size);

    /**
     * Get upcoming invoice preview (from Stripe API).
     * Architecture Plan §1.3 (billing-snapshot.nextInvoice).
     *
     * @param companyId The company ID
     * @return UpcomingInvoiceDto with projected amount, date, line items
     */
    UpcomingInvoiceDto getUpcomingInvoice(Long companyId);

    /**
     * Download invoice PDF.
     * Returns PDF bytes from the hosted_invoice_url or invoice_pdf_url.
     *
     * @param companyId The company ID
     * @param invoiceId The Stripe invoice ID
     * @return PDF bytes
     */
    byte[] downloadInvoicePdf(Long companyId, String invoiceId);

    /**
     * Get payment methods for a company.
     * Architecture Plan §3: GET /api/billing/details (payment methods section)
     *
     * @param companyId The company ID
     * @return List of PaymentMethodDto
     */
    List<PaymentMethodDto> getPaymentMethods(Long companyId);

    /**
     * Get billing notifications with optional filters.
     * Architecture Plan §1.1: GET /api/billing/notifications
     * FIXED: added type param + limit param to match architecture plan query parameters.
     *
     * @param companyId  The company ID
     * @param unreadOnly Filter to unread only (default: true per plan)
     * @param type       Optional filter by notification type (null = all types)
     * @param limit      Max notifications to return (default 20 per plan)
     * @return List of NotificationDto
     */
    List<NotificationDto> getNotifications(
            Long companyId,
            boolean unreadOnly,
            BillingNotification.NotificationType type,
            int limit
    );

    /**
     * Mark a single notification as read.
     * Architecture Plan §1.7: PATCH /api/billing/notifications/{notificationId}/read
     *
     * @param notificationId The notification ID
     */
    void markNotificationAsRead(Long notificationId);

    /**
     * Mark all unread notifications as read for a company.
     * Architecture Plan: bulk read action.
     * ADDED: Was missing from original.
     *
     * @param companyId The company ID
     * @return Number of notifications marked as read
     */
    int markAllNotificationsAsRead(Long companyId);

    /**
     * Dismiss (delete) a notification.
     * Architecture Plan §1.8: DELETE /api/billing/notifications/{notificationId}
     * ADDED: Was missing from original.
     *
     * @param notificationId The notification ID
     */
    void dismissNotification(Long notificationId);

    /**
     * Get usage history for charts.
     * Architecture Plan §4: Usage Analytics dashboard
     *
     * @param companyId The company ID
     * @param days      Number of days to look back
     * @return UsageHistoryDto with daily usage series
     */
    UsageHistoryDto getUsageHistory(Long companyId, int days);

    /**
     * Get entitlement change history (audit log).
     * Used by admin / account owners to see what changed and when.
     *
     * @param companyId The company ID
     * @param page      Page number
     * @param size      Page size
     * @return Paginated EntitlementHistoryDto
     */
    Page<EntitlementHistoryDto> getEntitlementHistory(Long companyId, int page, int size);
}