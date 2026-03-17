package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for current plan details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentPlanDto {
    
    private String planCode;
    private String planName;
    private String description;
    private String billingInterval;
    private LocalDateTime renewalDate;
    private Boolean cancelAtPeriodEnd;
    private String supportTier;
    
    // Pricing
    private Integer amountCents;
    private String currency;
}
