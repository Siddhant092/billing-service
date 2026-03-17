package com.broadnet.billing.controller;

import com.broadnet.billing.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for Stripe Webhooks
 * Base path: /webhooks/stripe
 *
 * IMPORTANT: This endpoint must be excluded from authentication/CSRF filters
 * because Stripe signs the request with a secret — no session token is used.
 * Add "/webhooks/stripe" to your security whitelist.
 */
@Slf4j
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    /**
     * POST /webhooks/stripe
     * Receives all Stripe webhook events.
     *
     * Stripe sends events for:
     * - customer.subscription.created/updated/deleted
     * - invoice.payment_succeeded / invoice.payment_failed / invoice.created
     * - subscription_schedule.created / subscription_schedule.completed
     * - payment_method.attached / payment_method.updated
     *
     * Flow inside the service:
     * 1. Verifies the Stripe-Signature header
     * 2. Checks idempotency (ignores already-processed event IDs)
     * 3. Stores the raw event in billing_webhook_events
     * 4. Routes to the appropriate handler
     * 5. Returns 200 OK to Stripe (failure returns 400 so Stripe retries)
     *
     * MUST return 200 quickly — Stripe retries on non-2xx.
     */
    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {
        log.info("POST /webhooks/stripe received");

        boolean success = stripeWebhookService.processWebhook(signature, payload);

        if (success) {
            return ResponseEntity.ok("Webhook processed");
        } else {
            // 400 tells Stripe the event was not processed → Stripe will retry
            return ResponseEntity.badRequest().body("Webhook processing failed");
        }
    }

    /**
     * GET /webhooks/stripe/stats  (admin use)
     * Returns processing statistics: total, completed, failed, processing counts.
     */
    @GetMapping("/stripe/stats")
    public ResponseEntity<Map<String, Long>> getWebhookStats() {
        return ResponseEntity.ok(stripeWebhookService.getWebhookStatistics());
    }
}
