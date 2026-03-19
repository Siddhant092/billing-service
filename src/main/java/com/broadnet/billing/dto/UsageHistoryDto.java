package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Usage history for charts.
 * Architecture Plan §4 Usage Analytics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageHistoryDto {

    /** Map of date string (yyyy-MM-dd) to answer count. */
    private Map<String, Integer> dailyAnswers;

    /** Map of date string to KB pages added. */
    private Map<String, Integer> dailyKbPages;

    /** Number of days this history covers. */
    private Integer days;

    /** Total answers in period. */
    private Integer totalAnswers;

    /** Total KB pages added in period. */
    private Integer totalKbPagesAdded;
}