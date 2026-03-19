package com.broadnet.billing.dto;

import com.broadnet.billing.entity.CompanyBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-company usage summary for admin dashboard.
 * Returned by UsageAnalyticsService.getCompanyUsageSummary() and getCompaniesApproachingLimits().
 *
 * Architecture Plan: GET /api/admin/billing/usage-summary
 * Shows: company, current plan, usage vs limits, status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyUsageSummaryDto {

    private Long companyId;
    private CompanyBilling.SubscriptionStatus subscriptionStatus;
    private String activePlanCode;
    private CompanyBilling.BillingMode billingMode;

    /** Answer usage. */
    private Integer answersUsed;
    private Integer answersLimit;
    private Double answersPercentage;
    private Boolean answersBlocked;

    /** KB pages. */
    private Integer kbPagesTotal;
    private Integer kbPagesLimit;
    private Double kbPagesPercentage;

    /** Agents. */
    private Integer agentsTotal;
    private Integer agentsLimit;
    private Double agentsPercentage;

    /** Users. */
    private Integer usersTotal;
    private Integer usersLimit;
    private Double usersPercentage;

    /**
     * Overall health status: "ok", "warning" (>80%), "blocked", "restricted".
     */
    private String status;
}
