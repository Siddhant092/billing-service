package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingUsageAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_usage_analytics table.
 * Pre-aggregated usage data for dashboard charts and reporting.
 *
 * STATUS: NEW — This repository did not exist in the original codebase.
 *
 * Write pattern: upsert per (company_id, metric_type, period_type, period_start).
 * Read pattern: time-series queries per company + metric + period granularity.
 *
 * Unique constraint: uk_company_metric_period on (company_id, metric_type, period_type, period_start)
 * — prevents duplicate aggregation buckets.
 */
@Repository
public interface BillingUsageAnalyticsRepository extends JpaRepository<BillingUsageAnalytics, Long> {

    /**
     * Find a specific analytics record for a company, metric, period type, and start time.
     * Used by the aggregation job to check if a bucket already exists before upserting.
     */
    Optional<BillingUsageAnalytics> findByCompanyIdAndMetricTypeAndPeriodTypeAndPeriodStart(
            Long companyId,
            BillingUsageAnalytics.MetricType metricType,
            BillingUsageAnalytics.PeriodType periodType,
            LocalDateTime periodStart
    );

    /**
     * Find all analytics records for a company and metric type in a date range.
     * Core query for usage charts on the dashboard.
     * e.g. "show me daily answer usage for the last 30 days"
     */
    @Query("SELECT bua FROM BillingUsageAnalytics bua WHERE bua.companyId = :companyId " +
            "AND bua.metricType = :metricType " +
            "AND bua.periodType = :periodType " +
            "AND bua.periodStart >= :startDate " +
            "AND bua.periodEnd <= :endDate " +
            "ORDER BY bua.periodStart ASC")
    List<BillingUsageAnalytics> findByCompanyAndMetricInRange(
            @Param("companyId") Long companyId,
            @Param("metricType") BillingUsageAnalytics.MetricType metricType,
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all analytics records for a company and period type in a date range.
     * Returns all metrics in a single query (for multi-chart dashboard load).
     */
    @Query("SELECT bua FROM BillingUsageAnalytics bua WHERE bua.companyId = :companyId " +
            "AND bua.periodType = :periodType " +
            "AND bua.periodStart >= :startDate " +
            "AND bua.periodEnd <= :endDate " +
            "ORDER BY bua.metricType, bua.periodStart ASC")
    List<BillingUsageAnalytics> findByCompanyAndPeriodTypeInRange(
            @Param("companyId") Long companyId,
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all analytics records for a company — all metrics, all period types.
     * Used for data export.
     */
    List<BillingUsageAnalytics> findByCompanyIdOrderByPeriodStartDesc(Long companyId);

    /**
     * Find the most recent analytics record for a company and metric.
     * Used to get the latest usage snapshot for a metric.
     */
    @Query("SELECT bua FROM BillingUsageAnalytics bua WHERE bua.companyId = :companyId " +
            "AND bua.metricType = :metricType AND bua.periodType = :periodType " +
            "ORDER BY bua.periodStart DESC LIMIT 1")
    Optional<BillingUsageAnalytics> findLatestByCompanyAndMetric(
            @Param("companyId") Long companyId,
            @Param("metricType") BillingUsageAnalytics.MetricType metricType,
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType
    );

    /**
     * Increment usage count for an existing analytics bucket.
     * Used by usage tracking service to update pre-aggregated counts in real-time.
     */
    @Modifying
    @Query("UPDATE BillingUsageAnalytics bua SET bua.usageCount = bua.usageCount + :delta " +
            "WHERE bua.companyId = :companyId " +
            "AND bua.metricType = :metricType " +
            "AND bua.periodType = :periodType " +
            "AND bua.periodStart = :periodStart")
    int incrementUsageCount(
            @Param("companyId") Long companyId,
            @Param("metricType") BillingUsageAnalytics.MetricType metricType,
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("delta") Integer delta
    );

    /**
     * Delete analytics records older than a retention cutoff.
     * Used by data retention cron job (e.g. keep hourly data for 7 days, monthly for 2 years).
     */
    @Modifying
    @Query("DELETE FROM BillingUsageAnalytics bua WHERE bua.periodType = :periodType " +
            "AND bua.periodEnd < :cutoffDate")
    int deleteOldRecordsByPeriodType(
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType,
            @Param("cutoffDate") LocalDateTime cutoffDate
    );

    /**
     * Aggregate total usage for a metric across a date range for a company.
     * Returns a single Integer sum — used for period totals in reports.
     */
    @Query("SELECT COALESCE(SUM(bua.usageCount), 0) FROM BillingUsageAnalytics bua " +
            "WHERE bua.companyId = :companyId " +
            "AND bua.metricType = :metricType " +
            "AND bua.periodType = :periodType " +
            "AND bua.periodStart >= :startDate " +
            "AND bua.periodEnd <= :endDate")
    Integer sumUsageInRange(
            @Param("companyId") Long companyId,
            @Param("metricType") BillingUsageAnalytics.MetricType metricType,
            @Param("periodType") BillingUsageAnalytics.PeriodType periodType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}