package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Usage statistics for a date range.
 * Returned by UsageAnalyticsService.getUsageStats().
 * Reads from billing_usage_logs (raw events).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageStatsDto {

    private Long companyId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;

    /** Total answers generated in period. */
    private Integer totalAnswers;

    /** Total KB pages added in period. */
    private Integer totalKbPagesAdded;

    /** Total KB pages updated in period. */
    private Integer totalKbPagesUpdated;

    /** Total agents created in period. */
    private Integer totalAgentsCreated;

    /** Total users added in period. */
    private Integer totalUsersCreated;

    /** Total blocked usage attempts (any type). */
    private Integer totalBlockedAttempts;

    /** Blocked answer attempts specifically. */
    private Integer blockedAnswerAttempts;
}
