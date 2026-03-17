package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for billing plan details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanDto {
    
    private Long id;
    private String planCode;
    private String planName;
    private String description;
    private Boolean isActive;
    private Boolean isEnterprise;
    private String supportTier;
    
    // Limits for this plan
    private List<PlanLimitDto> limits;
    
    // Pricing
    private Integer monthlyPriceCents;
    private Integer yearlyPriceCents;
    private String currency;
    
    // UI Flags
    private Boolean isCurrent; // Is this the user's current plan
    private Boolean isRecommended;
}
