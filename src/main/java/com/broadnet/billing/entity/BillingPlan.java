package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for billing_plans table.
 * Represents plan definitions (starter, professional, business, enterprise).
 *
 * Architecture Plan: Core Tables §1
 *
 * CHANGES FROM ORIGINAL:
 * - Added @OneToMany relationship to BillingPlanLimit (missing entirely)
 * - Added @OneToMany relationship to BillingStripePrice (missing entirely)
 * - support_tier changed from plain String to proper @Enumerated column (matches ENUM in schema)
 */
@Entity
@Table(
        name = "billing_plans",
        indexes = {
                @Index(name = "idx_active", columnList = "is_active")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_plan_code", columnNames = "plan_code")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_code", nullable = false, unique = true, length = 50)
    private String planCode;

    @Column(name = "plan_name", nullable = false)
    private String planName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_enterprise", nullable = false)
    @Builder.Default
    private Boolean isEnterprise = false;

    /**
     * FIXED: Was plain String. Schema defines ENUM('basic','standard','priority','dedicated').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "support_tier", length = 20)
    private SupportTier supportTier;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    // -------------------------------------------------------------------------
    // ADDED: Relationships — were completely missing from original
    // -------------------------------------------------------------------------

    /**
     * A plan has many time-versioned limits.
     * Used by EntitlementService to compute effective limits.
     * mappedBy matches BillingPlanLimit.plan field name.
     */
    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BillingPlanLimit> limits;

    /**
     * A plan maps to one or more Stripe prices (monthly + annual).
     */
    @OneToMany(mappedBy = "plan", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BillingStripePrice> stripePrices;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM('basic','standard','priority','dedicated')
    // -------------------------------------------------------------------------
    public enum SupportTier {
        basic, standard, priority, dedicated
    }
}