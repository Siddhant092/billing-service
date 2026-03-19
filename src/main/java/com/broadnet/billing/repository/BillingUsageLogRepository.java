package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for billing_usage_logs table.
 * This is an INSERT-only audit table — no updates or deletes.
 *
 * CHANGES FROM ORIGINAL:
 * - findByCompanyIdAndUsageType: usageType param changed to BillingUsageLog.UsageType enum
 * - findByUsageTypeAndDateRange: usageType param changed to enum
 * - countUsageByTypeInDateRange: JPQL groups by bul.usageType (enum) — correct as-is
 * - findByCompanyIdAndUsageTypeOrderByCreatedAtDesc (derived): usageType param → enum
 * - findByCompanyIdAndCreatedAtBetween (derived): confirmed correct
 * - findByCompanyIdOrderByCreatedAtDesc (derived): confirmed correct
 * - getUsageSummaryByCompanyId: confirmed correct
 */
@Repository
public interface BillingUsageLogRepository extends JpaRepository<BillingUsageLog, Long> {

    /**
     * Find all usage logs for a company — paginated, newest-first.
     * Used by usage analytics dashboard API.
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "ORDER BY bul.createdAt DESC")
    Page<BillingUsageLog> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * Find usage logs by company and type.
     * FIXED: usageType param type changed to BillingUsageLog.UsageType enum.
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.usageType = :usageType ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findByCompanyIdAndUsageType(
            @Param("companyId") Long companyId,
            @Param("usageType") BillingUsageLog.UsageType usageType
    );

    /**
     * Find all blocked usage attempts for a company.
     * Used to audit limit enforcement and diagnose customer complaints.
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.wasBlocked = true ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findBlockedUsageByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find usage logs for a company in a date range.
     * Used by usage analytics service for period-based reports.
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Aggregate usage count by type for a company in a date range.
     * Returns List<Object[]> with [UsageType, Long sum].
     * Used by analytics dashboard to show per-metric usage totals.
     */
    @Query("SELECT bul.usageType, SUM(bul.usageCount) FROM BillingUsageLog bul " +
            "WHERE bul.companyId = :companyId " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY bul.usageType")
    List<Object[]> countUsageByTypeInDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find recent usage logs for a company (e.g. last 24 hours).
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.createdAt >= :since ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findRecentUsageByCompanyId(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since
    );

    /**
     * Count total blocked attempts for a company (monitoring/alerting).
     */
    @Query("SELECT COUNT(bul) FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.wasBlocked = true")
    Long countBlockedAttemptsByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find usage logs by type and date range — across all companies.
     * Used by platform-wide analytics.
     * FIXED: usageType param → enum
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.usageType = :usageType " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt ASC")
    List<BillingUsageLog> findByUsageTypeAndDateRange(
            @Param("usageType") BillingUsageLog.UsageType usageType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get overall usage summary for a company (all-time totals per type).
     * Returns List<Object[]> with [UsageType, Long count, Long sum].
     */
    @Query("SELECT bul.usageType, COUNT(bul), SUM(bul.usageCount) FROM BillingUsageLog bul " +
            "WHERE bul.companyId = :companyId GROUP BY bul.usageType")
    List<Object[]> getUsageSummaryByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find all blocked attempts platform-wide in a date range (ops monitoring).
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.wasBlocked = true " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findAllBlockedUsageInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // -------------------------------------------------------------------------
    // Spring Data derived queries — required by UsageAnalyticsServiceImpl
    // -------------------------------------------------------------------------

    /**
     * Find logs for a company within a date range — Spring Data derived query.
     * Required by UsageAnalyticsServiceImpl.getUsageStats() and getDailyAnswerUsage().
     */
    List<BillingUsageLog> findByCompanyIdAndCreatedAtBetween(
            Long companyId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Find logs by company and type with pagination — newest-first.
     * Required by UsageAnalyticsServiceImpl.getUsageLogs().
     * FIXED: usageType param → BillingUsageLog.UsageType enum
     */
    Page<BillingUsageLog> findByCompanyIdAndUsageTypeOrderByCreatedAtDesc(
            Long companyId,
            BillingUsageLog.UsageType usageType,
            Pageable pageable
    );

    /**
     * Find all logs for a company with pagination — newest-first.
     * Required by UsageAnalyticsServiceImpl.getUsageLogs() (no type filter).
     */
    Page<BillingUsageLog> findByCompanyIdOrderByCreatedAtDesc(
            Long companyId,
            Pageable pageable
    );
}