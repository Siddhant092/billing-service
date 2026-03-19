package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Usage metrics for current billing period.
 * Architecture Plan §1.4 GET /api/billing/usage-metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageMetricsDto {

    private MetricDto answers;
    private MetricDto kbPages;
    private MetricDto agents;
    private MetricDto users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricDto {
        private Integer used;
        private Integer limit;
        private Integer remaining;
        private Double percentage;
        private Boolean isBlocked;

        /**
         * When this metric resets to zero.
         * Non-null for answers (periodic), null for kb_pages/agents/users (cumulative).
         */
        private LocalDateTime resetDate;
    }
}