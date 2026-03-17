package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for company usage summary (Admin view)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyUsageSummaryDto {
    
    private Long companyId;
    private String companyName;
    private String planCode;
    private String planName;
    private String subscriptionStatus;
    
    // Usage vs Limits
    private Integer answersUsed;
    private Integer answersLimit;
    private Double answersPercentage;
    
    private Integer kbPagesUsed;
    private Integer kbPagesLimit;
    private Double kbPagesPercentage;
    
    private Integer agentsUsed;
    private Integer agentsLimit;
    
    private Integer usersUsed;
    private Integer usersLimit;
    
    // Status
    private String status; // ok, warning, blocked
    private Boolean answersBlocked;
    
    // Alerts
    private Boolean approachingLimit; // >80% usage
    private String alertMessage;
}
