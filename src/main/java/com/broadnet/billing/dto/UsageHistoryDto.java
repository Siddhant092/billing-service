package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

/**
 * DTO for usage history with daily breakdown
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageHistoryDto {
    
    private Map<LocalDate, Integer> dailyAnswerCounts;
    private Map<LocalDate, Integer> dailyKbPageChanges;
    
    private Integer totalAnswers;
    private Integer totalKbPages;
    
    private Double averageAnswersPerDay;
    private String trend; // increasing, decreasing, stable
}
