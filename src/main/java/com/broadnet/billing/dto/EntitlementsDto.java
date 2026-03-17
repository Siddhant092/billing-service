package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for company entitlements (limits)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitlementsDto {
    
    private String planCode;
    private String planName;
    private List<String> addonCodes;
    private String billingInterval;
    
    // Computed Limits
    private Integer answersLimit;
    private Integer kbPagesLimit;
    private Integer agentsLimit;
    private Integer usersLimit;
    
    // Current Usage (optional, for UI)
    private Integer answersUsed;
    private Integer kbPagesUsed;
    private Integer agentsUsed;
    private Integer usersUsed;
    
    // Status
    private Boolean answersBlocked;
}
