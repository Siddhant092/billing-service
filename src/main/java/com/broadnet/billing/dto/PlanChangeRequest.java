package com.broadnet.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for changing subscription plan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanChangeRequest {
    
    @NotBlank(message = "New plan code is required")
    private String newPlanCode;
    
    @NotBlank(message = "Billing interval is required")
    @Pattern(regexp = "^(month|year)$", message = "Billing interval must be 'month' or 'year'")
    private String billingInterval;
    
    private Boolean prorationBehavior; // true = prorate, false = no proration
}
