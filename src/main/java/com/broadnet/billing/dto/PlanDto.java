package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

/**
 * Plan details with limits and pricing.
 * Architecture Plan §2.1 GET /api/billing/subscription/plans response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDto {

    private Long id;
    private String planCode;
    private String planName;
    private String description;
    private Boolean isActive;
    private Boolean isEnterprise;
    private String supportTier;

    /** Current effective limits for this plan. */
    private Integer answersPerPeriod;
    private Integer kbPages;
    private Integer agents;
    private Integer users;

    /** Pricing info — monthly and annual. */
    private PricingDto pricing;

    /** Whether this is the company's current plan (requires companyId context). */
    private Boolean isCurrent;

    /** Whether the company can upgrade to this plan. */
    private Boolean canUpgrade;

    /** Whether the company can downgrade to this plan. */
    private Boolean canDowngrade;

    /** "upgrade", "downgrade", "contact_us", or null. */
    private String upgradeAction;
    private String downgradeAction;

    /** Feature bullet points for pricing page display. */
    private List<String> features;

    /** "postpaid" for enterprise plans, null for regular. */
    private String billingMode;

    /** URL for enterprise contact (only for is_enterprise=true plans). */
    private String contactUsUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingDto {
        /** "standard" or "custom" (for enterprise). */
        private String type;
        private PriceIntervalDto monthly;
        private PriceIntervalDto annual;
        /** For enterprise: "Contact us for pricing". */
        private String message;
        private String startingFrom;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceIntervalDto {
        private Integer amount;
        private String amountFormatted;
        private String stripePriceId;
        /** Annual savings percentage vs monthly (e.g. "17%"). */
        private String savings;
    }
}