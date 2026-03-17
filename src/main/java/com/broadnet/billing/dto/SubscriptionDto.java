package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for subscription details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDto {
    
    private String stripeSubscriptionId;
    private String status; // active, past_due, canceled, trialing, etc.
    private String planCode;
    private String planName;
    private String billingInterval;
    
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime renewalDate;
    
    // Cancellation
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime cancelAt;
    private LocalDateTime canceledAt;
    
    // Add-ons
    private List<String> activeAddonCodes;
    
    // Pending Changes
    private String pendingPlanCode;
    private List<String> pendingAddonCodes;
    private LocalDateTime pendingEffectiveDate;
    
    // Pricing
    private Integer amountCents;
    private String currency;
    
    // Support
    private String supportTier;
}
