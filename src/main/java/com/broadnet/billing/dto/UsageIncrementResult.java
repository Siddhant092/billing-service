package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an atomic usage increment attempt.
 * Returned by UsageEnforcementService.increment*Usage() methods.
 *
 * Architecture Plan API response:
 * { success, answers_used, answers_limit, remaining, blocked }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageIncrementResult {

    /** true if the increment succeeded; false if blocked or limit exceeded. */
    private boolean success;

    /** Current usage count after the increment (or at time of rejection). */
    private Integer used;

    /** Effective limit for this metric. */
    private Integer limit;

    /** Remaining capacity (limit - used). Negative = over limit. */
    private Integer remaining;

    /** True if the company is now blocked (at or over limit). */
    private boolean blocked;

    /**
     * Error code if not successful.
     * e.g. "ANSWER_LIMIT_EXCEEDED", "KB_PAGE_LIMIT_EXCEEDED"
     */
    private String errorCode;

    /** Human-readable message for the UI. */
    private String message;
}