package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for entitlement change history
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntitlementHistoryDto {
    
    private Long id;
    private String changeType; // plan_change, addon_added, addon_removed, etc.
    
    private String oldPlanCode;
    private String newPlanCode;
    
    private List<String> oldAddonCodes;
    private List<String> newAddonCodes;
    
    private Integer oldAnswersLimit;
    private Integer newAnswersLimit;
    
    private Integer oldKbPagesLimit;
    private Integer newKbPagesLimit;
    
    private Integer oldAgentsLimit;
    private Integer newAgentsLimit;
    
    private Integer oldUsersLimit;
    private Integer newUsersLimit;
    
    private String triggeredBy; // webhook, admin, api
    private String stripeEventId;
    private LocalDateTime effectiveDate;
    private LocalDateTime createdAt;
}
