package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_usage_analytics table.
 * Pre-aggregated usage data for dashboard charts and reporting.
 * Populated by background jobs that aggregate billing_usage_logs.
 *
 * Architecture Plan: UI Extension Tables §4
 *
 * STATUS: NEW — This entity did not exist at all in the original codebase.
 *
 * Key design notes:
 * - Pre-aggregated (not computed at query time) for performance
 * - Unique constraint prevents duplicate aggregation for same company+metric+period
 * - limit_value is snapshotted at time of period (for historical accuracy)
 * - No @UpdateTimestamp — rows are written once per period bucket
 * - No FK to company in the index (company_id in table via FK to company)
 */
@Entity
@Table(
        name = "billing_usage_analytics",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_metric_period",
                        columnNames = {"company_id", "metric_type", "period_type", "period_start"}
                )
        },
        indexes = {
                @Index(name = "idx_company_period", columnList = "company_id, period_type, period_start"),
                @Index(name = "idx_metric_period",  columnList = "metric_type, period_type, period_start")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingUsageAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id).
     * Kept as bare Long until Company entity is confirmed.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * Schema ENUM('answers','kb_pages','agents','users')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "metric_type", nullable = false, length = 20)
    private MetricType metricType;

    /**
     * Schema ENUM('hour','day','week','month')
     * Drives which bucket this row represents.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    private PeriodType periodType;

    @Column(name = "period_start", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime periodEnd;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    /**
     * The effective limit value at the time this period was recorded.
     * NULL if limit was unlimited (enterprise).
     */
    @Column(name = "limit_value")
    private Integer limitValue;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    // -------------------------------------------------------------------------
    // Enums matching schema ENUM definitions exactly
    // -------------------------------------------------------------------------

    public enum MetricType {
        answers, kb_pages, agents, users
    }

    public enum PeriodType {
        hour, day, week, month
    }
}