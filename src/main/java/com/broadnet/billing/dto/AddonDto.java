package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingAddon;
import com.broadnet.billing.entity.BillingAddonDelta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Addon details with deltas and pricing.
 * Architecture Plan §1.5 GET /api/billing/available-boosts response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddonDto {

    private Long id;
    private String addonCode;
    private String addonName;
    private BillingAddon.AddonCategory category;
    private BillingAddon.AddonTier tier;
    private String description;
    private Boolean isActive;

    /** How many units this addon adds per period. */
    private Integer deltaValue;
    private BillingAddonDelta.DeltaType deltaType;

    /** Monthly price in cents. */
    private Integer priceMonthly;
    private String priceMonthlyFormatted;

    /** Annual price in cents. */
    private Integer priceAnnual;
    private String priceAnnualFormatted;

    /** True if this addon is already in the company's active_addon_codes. */
    private Boolean isPurchased;
}