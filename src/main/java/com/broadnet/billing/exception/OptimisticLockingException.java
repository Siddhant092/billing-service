package com.broadnet.billing.exception;

/**
 * Thrown when a concurrent update version conflict is detected.
 * Maps to HTTP 409 Conflict.
 *
 * Architecture Plan: "Optimistic Locking Pattern — if updated == 0, throw OptimisticLockingException"
 * Referenced explicitly in architecture plan Java code samples and in EntitlementService.
 *
 * In practice this is caught internally in the service layer and retried (max 3x).
 * It only propagates to the global handler if all retries are exhausted.
 *
 * Usage:
 *   throw new OptimisticLockingException("Concurrent update detected on company_billing for companyId: " + companyId);
 */
public class OptimisticLockingException extends BillingException {

    private static final String ERROR_CODE = "OPTIMISTIC_LOCK_CONFLICT";

    public OptimisticLockingException(String message) {
        super(ERROR_CODE, message);
    }

    public OptimisticLockingException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
