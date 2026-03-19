package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a non-incrementing limit check.
 * Returned by UsageEnforcementService.check*Limit() methods.
 *
 * Architecture Plan API response:
 * { allowed, kb_pages_total, kb_pages_limit, remaining }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageCheckResult {

    /** True if the action is allowed within current limits. */
    private boolean allowed;

    /** Current usage count for this metric. */
    private Integer used;

    /** Effective limit for this metric. */
    private Integer limit;

    /** Remaining capacity (limit - used). */
    private Integer remaining;

    /** Percentage used (0.0–100.0). */
    private Double percentageUsed;
}