package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for subscription change preview
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPreviewDto {
    
    private String currentPlanCode;
    private String newPlanCode;
    
    private Integer currentAmountCents;
    private Integer newAmountCents;
    private Integer prorationAmountCents;
    
    private Integer amountDueTodayCents;
    private String currency;
    
    private LocalDateTime effectiveDate;
    private String changeType; // immediate, scheduled
    
    private String description;
    
    // Entitlement Changes
    private EntitlementsDto currentEntitlements;
    private EntitlementsDto newEntitlements;
}
