package com.broadnet.billing.exception;

/**
 * Thrown when an operation is attempted on a billing record
 * that is in an incompatible state.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * Examples:
 * - Trying to reactivate a subscription that is not cancel_at_period_end
 * - Attempting to cancel an already-canceled subscription
 * - Trying to generate an enterprise invoice when billing_status != calculated
 * - Trying to calculate billing when billing_status != pending
 * - Applying enterprise pricing to a prepaid company
 * - Removing a payment failure restriction when none exists
 *
 * Architecture Plan: State flows like pending→calculated→invoiced→paid
 * must not be violated.
 *
 * Usage:
 *   throw new BillingStateException("INVALID_BILLING_STATUS",
 *       "Cannot generate invoice: billing record " + billingId + " is not in 'calculated' state");
 *   throw new BillingStateException("SUBSCRIPTION_NOT_CANCELING",
 *       "Cannot reactivate: subscription is not set to cancel at period end");
 */
public class BillingStateException extends BillingException {

    public BillingStateException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BillingStateException(String message) {
        super("INVALID_BILLING_STATE", message);
    }
}
