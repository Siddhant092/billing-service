package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_plan_limits table
 * Represents dynamic limits per plan with versioning support
 */
@Entity
@Table(name = "billing_plan_limits")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPlanLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "limit_type", nullable = false, length = 30)
    private String limitType; // answers_per_period, kb_pages, agents, users

    @Column(name = "limit_value", nullable = false)
    private Integer limitValue;

    @Column(name = "billing_interval", nullable = false, length = 10)
    @Builder.Default
    private String billingInterval = "month"; // month, year

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "effective_from", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;
}
