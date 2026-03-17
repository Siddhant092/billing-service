package com.broadnet.billing.service;

import com.broadnet.billing.dto.UsageStatsDto;
import com.broadnet.billing.dto.CompanyUsageSummaryDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for usage analytics and reporting
 * 
 * Based on Architecture Plan:
 * - Section: Usage Enforcement
 * - Section: billing_usage_logs table
 * - Provides usage analytics for dashboard and admin reports
 */
public interface UsageAnalyticsService {
    
    /**
     * Get usage statistics for a company in a date range
     * 
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate End date
     * @return UsageStatsDto with usage breakdown
     * 
     * Returns:
     * - Total answers generated
     * - KB pages added/updated
     * - Agents created
     * - Users added
     * - Blocked attempts
     */
    UsageStatsDto getUsageStats(Long companyId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Get daily usage breakdown for charts
     * 
     * @param companyId The company ID
     * @param days Number of days to look back
     * @return Map of date to usage counts
     */
    Map<String, Integer> getDailyAnswerUsage(Long companyId, int days);
    
    /**
     * Get usage summary for all companies (Admin)
     * 
     * @param page Page number
     * @param size Page size
     * @return Page of company usage summaries
     * 
     * Endpoint: GET /api/admin/billing/usage-summary
     * 
     * Returns:
     * - Company name
     * - Current plan
     * - Usage vs limits
     * - Percentage used
     * - Status (ok, warning, blocked)
     */
    org.springframework.data.domain.Page<CompanyUsageSummaryDto> getCompanyUsageSummary(
        int page, 
        int size
    );
    
    /**
     * Get companies approaching limits (Admin)
     * For proactive support and upsell opportunities
     * 
     * @param threshold Percentage threshold (e.g., 0.8 for 80%)
     * @return List of companies near limits
     * 
     * Endpoint: GET /api/admin/billing/approaching-limits
     */
    List<CompanyUsageSummaryDto> getCompaniesApproachingLimits(Double threshold);
    
    /**
     * Get blocked usage attempts for a company
     * 
     * @param companyId The company ID
     * @param days Number of days to look back
     * @return List of blocked attempts
     */
    List<com.broadnet.billing.entity.BillingUsageLog> getBlockedAttempts(Long companyId, int days);
    
    /**
     * Get usage logs for a company with pagination
     * 
     * @param companyId The company ID
     * @param usageType Filter by usage type (null for all)
     * @param page Page number
     * @param size Page size
     * @return Page of usage logs
     * 
     * Endpoint: GET /api/billing/usage-logs
     */
    org.springframework.data.domain.Page<com.broadnet.billing.entity.BillingUsageLog> getUsageLogs(
        Long companyId,
        String usageType,
        int page,
        int size
    );
    
    /**
     * Get usage breakdown by type for a period
     * 
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate End date
     * @return Map of usage_type to count
     */
    Map<String, Object> getUsageBreakdown(Long companyId, LocalDateTime startDate, LocalDateTime endDate);
}
