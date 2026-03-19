package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_plan_limits table.
 * Stores dynamic, time-versioned limits per plan.
 *
 * Architecture Plan: Core Tables §2
 *
 * CHANGES FROM ORIGINAL:
 * - planId (Long) replaced with proper @ManyToOne BillingPlan relationship
 * - limitType changed from plain String to @Enumerated LimitType (matches schema ENUM)
 * - billingInterval changed from plain String to @Enumerated BillingInterval (matches schema ENUM)
 * - Added @Table uniqueConstraint to match schema uk_plan_limit_type_interval
 * - Added @Table index to match schema idx_plan_active
 * - effectiveFrom @Builder.Default set to now (matches schema DEFAULT CURRENT_TIMESTAMP)
 * - @Version initial value kept at 1 (correct — matches schema DEFAULT 1)
 */
@Entity
@Table(
        name = "billing_plan_limits",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_plan_limit_type_interval",
                        columnNames = {"plan_id", "limit_type", "billing_interval", "effective_from"}
                )
        },
        indexes = {
                @Index(name = "idx_plan_active", columnList = "plan_id, is_active, effective_from")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPlanLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FIXED: Was a bare Long planId. Must be a proper FK relationship.
     * FK constraint: fk_plan_limits_plan → billing_plans(id) ON DELETE RESTRICT
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_plan_limits_plan"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingPlan plan;

    /**
     * FIXED: Was plain String. Schema defines
     * ENUM('answers_per_period','kb_pages','agents','users').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false, length = 30)
    private LimitType limitType;

    @Column(name = "limit_value", nullable = false)
    private Integer limitValue;

    /**
     * FIXED: Was plain String. Schema defines ENUM('month','year').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    @Builder.Default
    private BillingInterval billingInterval = BillingInterval.month;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * FIXED: Defaulting to now() to match schema DEFAULT CURRENT_TIMESTAMP(6).
     */
    @Column(name = "effective_from", columnDefinition = "DATETIME(6)")
    @Builder.Default
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "effective_to", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking. JPA @Version manages increment automatically.
     * Schema: version INT NOT NULL DEFAULT 1
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    // -------------------------------------------------------------------------
    // Enums matching schema definitions exactly
    // -------------------------------------------------------------------------

    public enum LimitType {
        answers_per_period, kb_pages, agents, users
    }

    public enum BillingInterval {
        month, year
    }
}