package com.broadnet.billing.service;

import com.broadnet.billing.dto.CompanyUsageSummaryDto;
import com.broadnet.billing.dto.UsageStatsDto;
import com.broadnet.billing.entity.BillingUsageLog;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for usage analytics and reporting.
 *
 * Architecture Plan: billing_usage_logs table + billing_usage_analytics table
 * + Usage Enforcement section.
 *
 * CHANGES FROM ORIGINAL:
 * - getUsageLogs: usageType param changed to BillingUsageLog.UsageType enum (nullable)
 *   (was plain String — entity field is now @Enumerated)
 * - getBlockedAttempts return type: kept as List<BillingUsageLog> — correct
 * - aggregateUsageAnalytics: ADDED — architecture plan has billing_usage_analytics table
 *   that needs to be populated by a background aggregation job. This method drives that.
 * - getAnalyticsSeries: ADDED — reads from billing_usage_analytics for charting
 *   (separate from getUsageStats which reads from raw billing_usage_logs)
 * - No other structural changes needed.
 */
public interface UsageAnalyticsService {

    /**
     * Get usage statistics for a company in a date range.
     * Reads from billing_usage_logs (raw events).
     * Architecture Plan: Usage tracking section.
     *
     * Returns totals per usage type + blocked attempt count.
     *
     * @param companyId The company ID
     * @param startDate Start date (inclusive)
     * @param endDate   End date (inclusive)
     * @return UsageStatsDto with per-type totals
     */
    UsageStatsDto getUsageStats(Long companyId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get daily answer usage breakdown for chart display.
     * Reads from billing_usage_logs grouped by day.
     *
     * @param companyId The company ID
     * @param days      Number of days to look back
     * @return Map of date string (yyyy-MM-dd) to answer count
     */
    Map<String, Integer> getDailyAnswerUsage(Long companyId, int days);

    /**
     * Get usage summary for all companies. Admin-only.
     * Architecture Plan: GET /api/admin/billing/usage-summary
     *
     * Returns per company: name, current plan, usage vs limits, percentage, status.
     *
     * @param page Page number
     * @param size Page size
     * @return Page of CompanyUsageSummaryDto
     */
    Page<CompanyUsageSummaryDto> getCompanyUsageSummary(int page, int size);

    /**
     * Get companies approaching their limits. Admin-only.
     * Architecture Plan: GET /api/admin/billing/approaching-limits
     *
     * @param threshold Percentage threshold (e.g. 0.8 for 80%)
     * @return List of CompanyUsageSummaryDto for companies near limits
     */
    List<CompanyUsageSummaryDto> getCompaniesApproachingLimits(Double threshold);

    /**
     * Get blocked usage attempts for a company in the last N days.
     *
     * @param companyId The company ID
     * @param days      Number of days to look back
     * @return List of BillingUsageLog where was_blocked=true
     */
    List<BillingUsageLog> getBlockedAttempts(Long companyId, int days);

    /**
     * Get usage logs for a company with pagination.
     * Architecture Plan: GET /api/billing/usage-logs
     * FIXED: usageType param changed to BillingUsageLog.UsageType enum (nullable = no filter).
     *
     * @param companyId The company ID
     * @param usageType Filter by usage type (null = all types)
     * @param page      Page number
     * @param size      Page size
     * @return Page of BillingUsageLog
     */
    Page<BillingUsageLog> getUsageLogs(
            Long companyId,
            BillingUsageLog.UsageType usageType,
            int page,
            int size
    );

    /**
     * Get usage breakdown by type for a period.
     * Returns aggregated counts per usage type.
     *
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate   End date
     * @return Map of usage_type to count (e.g. {"answer": 1523, "kb_page_added": 45})
     */
    Map<String, Object> getUsageBreakdown(Long companyId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Populate pre-aggregated analytics buckets in billing_usage_analytics.
     * Called by cron job to aggregate raw billing_usage_logs into period buckets.
     * Architecture Plan: billing_usage_analytics table for "Pre-aggregated Usage Data"
     * ADDED: Was missing — without this, billing_usage_analytics table is never populated.
     *
     * @param companyId  The company ID to aggregate (null = all companies)
     * @param periodType Period type to aggregate (hour/day/week/month)
     * @return Number of analytics buckets written/updated
     */
    int aggregateUsageAnalytics(Long companyId, com.broadnet.billing.entity.BillingUsageAnalytics.PeriodType periodType);

    /**
     * Get pre-aggregated time-series data from billing_usage_analytics for charting.
     * Faster than getUsageStats for large date ranges.
     * ADDED: Was missing — needed to utilize the billing_usage_analytics table.
     *
     * @param companyId  The company ID
     * @param metricType The metric to chart
     * @param periodType Period granularity (hour/day/week/month)
     * @param startDate  Start of range
     * @param endDate    End of range
     * @return Map of period_start (ISO string) to usage_count
     */
    Map<String, Integer> getAnalyticsSeries(
            Long companyId,
            com.broadnet.billing.entity.BillingUsageAnalytics.MetricType metricType,
            com.broadnet.billing.entity.BillingUsageAnalytics.PeriodType periodType,
            LocalDateTime startDate,
            LocalDateTime endDate
    );
}