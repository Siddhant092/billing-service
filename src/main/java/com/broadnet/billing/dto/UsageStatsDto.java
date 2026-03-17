package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for usage statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageStatsDto {
    
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    
    // Answer Stats
    private Long totalAnswersGenerated;
    private Long blockedAnswerAttempts;
    
    // KB Stats
    private Long kbPagesAdded;
    private Long kbPagesUpdated;
    private Long kbPagesDeleted;
    
    // Agent Stats
    private Long agentsCreated;
    
    // User Stats
    private Long usersAdded;
    
    // Overall
    private Long totalUsageEvents;
    private Long totalBlockedAttempts;
}
