package com.broadnet.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a Stripe checkout session
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutSessionRequest {
    
    @NotBlank(message = "Plan code is required")
    private String planCode;
    
    @NotBlank(message = "Billing interval is required")
    @Pattern(regexp = "^(month|year)$", message = "Billing interval must be 'month' or 'year'")
    private String billingInterval;
    
    @NotBlank(message = "Success URL is required")
    private String successUrl;
    
    @NotBlank(message = "Cancel URL is required")
    private String cancelUrl;
}
