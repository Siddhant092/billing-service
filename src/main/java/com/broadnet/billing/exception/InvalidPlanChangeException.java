package com.broadnet.billing.exception;

/**
 * Thrown when a plan change request is semantically invalid.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * Examples of invalid plan changes:
 * - Attempting to "upgrade" to the same plan
 * - Changing plan when no active subscription exists
 * - Attempting a standard plan upgrade on an enterprise (postpaid) account
 * - Requesting an unknown or inactive plan code
 * - Downgrading when current usage exceeds the new plan's limits
 *
 * Architecture Plan: SubscriptionManagementService.changePlan()
 * "Validate plan exists and is active" — validation failures throw this.
 *
 * Usage:
 *   throw new InvalidPlanChangeException("SAME_PLAN",
 *       "Cannot change to the same plan: professional");
 *   throw new InvalidPlanChangeException("NO_ACTIVE_SUBSCRIPTION",
 *       "Company has no active subscription to modify");
 */
public class InvalidPlanChangeException extends BillingException {

    public InvalidPlanChangeException(String errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidPlanChangeException(String message) {
        super("INVALID_PLAN_CHANGE", message);
    }
}
