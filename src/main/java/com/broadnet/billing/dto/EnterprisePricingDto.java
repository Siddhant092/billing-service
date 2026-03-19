package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingEnterprisePricing;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enterprise pricing DTO.
 * Used for both request (setPricing) and response (getActivePricing, getPricingHistory).
 *
 * Architecture Plan: POST /api/admin/enterprise/pricing
 * Schema: billing_enterprise_pricing table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterprisePricingDto {

    private Long id;
    private Long companyId;
    private BillingEnterprisePricing.PricingTier pricingTier;

    /** Per 1000 answers in cents. */
    private Integer answersRateCents;

    /** Per KB page in cents. */
    private Integer kbPagesRateCents;

    /** Per agent per month in cents. */
    private Integer agentsRateCents;

    /** Per user per month in cents. */
    private Integer usersRateCents;

    /**
     * Volume discount tiers JSON string.
     * e.g. [{"min": 100000, "discount": 10}]
     */
    private String answersVolumeDiscountTiers;
    private String kbPagesVolumeDiscountTiers;

    /** Minimum monthly spend in cents (nullable). */
    private Integer minimumMonthlyCommitmentCents;

    /** Minimum answers per month (nullable). */
    private Integer minimumAnswersCommitment;

    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Boolean isActive;

    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String contractReference;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
