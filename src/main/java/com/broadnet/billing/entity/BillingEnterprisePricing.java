package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity for billing_enterprise_pricing table.
 * Stores custom per-unit pricing for enterprise/postpaid customers.
 *
 * Architecture Plan: UI Extension Tables §6
 *
 * CHANGES FROM ORIGINAL:
 * - ADDED: pricingTier field (was missing — schema has pricing_tier ENUM('standard','custom','negotiated'))
 * - ADDED: answersVolumeDiscountTiers JSON field (was missing — schema has this column)
 * - ADDED: kbPagesVolumeDiscountTiers JSON field (was missing — schema has this column)
 * - ADDED: minimumAnswersCommitment field (was missing — schema has minimum_answers_commitment)
 * - ADDED: approvedBy field (was missing — schema has approved_by BIGINT)
 * - ADDED: approvedAt field (was missing — schema has approved_at DATETIME(6))
 * - minimumMonthlyCommitmentCents: made nullable (schema: NULL is valid)
 * - REMOVED: createdBy / updatedBy (not in architecture plan schema for this table)
 * - Updated @Table indexes to match schema
 */
@Entity
@Table(
        name = "billing_enterprise_pricing",
        indexes = {
                @Index(name = "idx_company_active",   columnList = "company_id, is_active, effective_from"),
                @Index(name = "idx_effective_dates",  columnList = "effective_from, effective_to")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEnterprisePricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id) ON DELETE CASCADE.
     * Kept as bare Long until Company entity is confirmed.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * ADDED: Was missing. Schema: pricing_tier ENUM('standard','custom','negotiated') NOT NULL DEFAULT 'standard'
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_tier", nullable = false, length = 20)
    @Builder.Default
    private PricingTier pricingTier = PricingTier.standard;

    /** Price per 1000 answers in cents. */
    @Column(name = "answers_rate_cents", nullable = false)
    private Integer answersRateCents;

    /** Price per KB page in cents. */
    @Column(name = "kb_pages_rate_cents", nullable = false)
    private Integer kbPagesRateCents;

    /** Price per agent per month in cents. */
    @Column(name = "agents_rate_cents", nullable = false)
    private Integer agentsRateCents;

    /** Price per user per month in cents. */
    @Column(name = "users_rate_cents", nullable = false)
    private Integer usersRateCents;

    /**
     * ADDED: Was missing. Schema: answers_volume_discount_tiers JSON NULL
     * e.g. [{"min": 100000, "discount": 10}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answers_volume_discount_tiers", columnDefinition = "JSON")
    private String answersVolumeDiscountTiers;

    /**
     * ADDED: Was missing. Schema: kb_pages_volume_discount_tiers JSON NULL
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kb_pages_volume_discount_tiers", columnDefinition = "JSON")
    private String kbPagesVolumeDiscountTiers;

    /**
     * FIXED: Made nullable — schema: minimum_monthly_commitment_cents INTEGER NULL
     */
    @Column(name = "minimum_monthly_commitment_cents")
    private Integer minimumMonthlyCommitmentCents;

    /**
     * ADDED: Was missing. Schema: minimum_answers_commitment INTEGER NULL
     */
    @Column(name = "minimum_answers_commitment")
    private Integer minimumAnswersCommitment;

    @Column(name = "effective_from", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveTo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * ADDED: Was missing. Schema: approved_by BIGINT NULL (User ID who approved pricing).
     */
    @Column(name = "approved_by")
    private Long approvedBy;

    /**
     * ADDED: Was missing. Schema: approved_at DATETIME(6) NULL.
     */
    @Column(name = "approved_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime approvedAt;

    @Column(name = "contract_reference", length = 255)
    private String contractReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM('standard','custom','negotiated')
    // -------------------------------------------------------------------------

    public enum PricingTier {
        standard, custom, negotiated
    }
}