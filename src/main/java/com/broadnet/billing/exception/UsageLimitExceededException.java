package com.broadnet.billing.exception;

/**
 * Thrown when a company has reached or exceeded a usage limit.
 * Maps to HTTP 403 Forbidden (blocked from performing the action).
 *
 * Architecture Plan: "Hard caps on usage with immediate blocking"
 * API response: { success: false, error: "ANSWER_LIMIT_EXCEEDED", ... }
 *
 * Referenced explicitly in UsageEnforcementService Javadocs.
 *
 * Usage:
 *   throw new UsageLimitExceededException("ANSWER_LIMIT_EXCEEDED",
 *       "You've reached your answer limit. Upgrade plan or add a boost.",
 *       8000, 8000);
 */
public class UsageLimitExceededException extends BillingException {

    private final Integer used;
    private final Integer limit;

    public UsageLimitExceededException(String errorCode, String message,
                                       Integer used, Integer limit) {
        super(errorCode, message);
        this.used = used;
        this.limit = limit;
    }

    public UsageLimitExceededException(String errorCode, String message) {
        super(errorCode, message);
        this.used = null;
        this.limit = null;
    }

    public Integer getUsed() {
        return used;
    }

    public Integer getLimit() {
        return limit;
    }
}
