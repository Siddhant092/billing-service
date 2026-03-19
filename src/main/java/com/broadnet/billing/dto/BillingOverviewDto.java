package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated billing overview — combines all dashboard data in one response.
 * Returned by BillingDashboardService.getOverview().
 *
 * Architecture Plan §1.6 GET /api/billing/overview.
 * Aggregates: notifications, currentPlan, billingSnapshot, usageMetrics, availableBoosts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingOverviewDto {

    /** Unread notifications (same as GET /api/billing/notifications). */
    private List<NotificationDto> notifications;
    private Long unreadCount;

    /** Current plan details (same as GET /api/billing/current-plan). */
    private CurrentPlanDto currentPlan;

    /** Billing snapshot (same as GET /api/billing/billing-snapshot). */
    private BillingSnapshotDto billingSnapshot;

    /** Usage metrics (same as GET /api/billing/usage-metrics). */
    private UsageMetricsDto usageMetrics;

    /** Available boosts/addons (same as GET /api/billing/available-boosts). */
    private List<AddonDto> availableBoosts;
}
