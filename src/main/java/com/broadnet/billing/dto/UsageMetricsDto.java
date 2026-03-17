package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for usage metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMetricsDto {
    
    // Answers
    private Integer answersUsed;
    private Integer answersLimit;
    private Integer answersRemaining;
    private Double answersPercentageUsed;
    private Boolean answersBlocked;
    
    // KB Pages
    private Integer kbPagesTotal;
    private Integer kbPagesLimit;
    private Integer kbPagesRemaining;
    private Double kbPagesPercentageUsed;
    
    // Agents
    private Integer agentsTotal;
    private Integer agentsLimit;
    private Integer agentsRemaining;
    private Double agentsPercentageUsed;
    
    // Users
    private Integer usersTotal;
    private Integer usersLimit;
    private Integer usersRemaining;
    private Double usersPercentageUsed;
    
    // Period Info
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer daysUntilReset;
}
