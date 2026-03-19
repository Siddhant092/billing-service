package com.broadnet.billing.exception;

/**
 * Thrown during Stripe webhook processing failures.
 * Maps to HTTP 400 Bad Request (for signature failures)
 * or HTTP 500 Internal Server Error (for processing failures).
 *
 * Architecture Plan: "Invalid Signature → WebhookHandler → 400 Bad Request"
 * Two subtypes of failure:
 *   1. INVALID_SIGNATURE — Stripe signature verification failed → 400
 *   2. PROCESSING_FAILED  — Event was valid but handler threw → 500 (retry by Stripe)
 *
 * Usage:
 *   throw new WebhookProcessingException(
 *       WebhookProcessingException.Reason.INVALID_SIGNATURE,
 *       "Stripe signature verification failed");
 *
 *   throw new WebhookProcessingException(
 *       WebhookProcessingException.Reason.PROCESSING_FAILED,
 *       "Failed to process customer.subscription.updated", cause);
 */
public class WebhookProcessingException extends BillingException {

    public enum Reason {
        INVALID_SIGNATURE,
        PROCESSING_FAILED,
        PAYLOAD_PARSE_ERROR,
        UNKNOWN_EVENT_TYPE
    }

    private final Reason reason;

    public WebhookProcessingException(Reason reason, String message) {
        super("WEBHOOK_" + reason.name(), message);
        this.reason = reason;
    }

    public WebhookProcessingException(Reason reason, String message, Throwable cause) {
        super("WEBHOOK_" + reason.name(), message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isSignatureFailure() {
        return reason == Reason.INVALID_SIGNATURE;
    }
}
