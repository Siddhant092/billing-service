package com.broadnet.billing.exception;

/**
 * Thrown when Stripe API calls fail unexpectedly.
 * Maps to HTTP 502 Bad Gateway — the upstream Stripe API failed.
 *
 * Architecture Plan: "Stripe API failures: Log and alert, don't block user operations"
 * This exception is thrown when we cannot reach Stripe or get an unexpected response.
 *
 * Distinct from WebhookProcessingException (inbound) — this is for outbound Stripe API calls:
 * - Creating checkout sessions
 * - Fetching subscriptions
 * - Updating subscriptions
 * - Creating invoices
 * - Creating customers
 *
 * Usage:
 *   throw new StripeIntegrationException(
 *       "Failed to create Stripe checkout session for companyId: " + companyId, stripeException);
 */
public class StripeIntegrationException extends BillingException {

    private static final String ERROR_CODE = "STRIPE_API_ERROR";

    public StripeIntegrationException(String message) {
        super(ERROR_CODE, message);
    }

    public StripeIntegrationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
