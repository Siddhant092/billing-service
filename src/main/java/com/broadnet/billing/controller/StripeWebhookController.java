package com.broadnet.billing.controller;

import com.broadnet.billing.exception.WebhookProcessingException;
import com.broadnet.billing.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Stripe webhook receiver.
 *
 * Architecture Plan:
 *   POST /webhooks/stripe
 *
 * Flow (from architecture plan sequence diagram):
 *   1. Verify Stripe-Signature header
 *   2. Check idempotency (stripe_event_id)
 *   3. If already processed → return 200 OK immediately
 *   4. Store event in billing_webhook_events (full payload)
 *   5. Process event (route to handler)
 *   6. Mark webhook as processed
 *   7. Return 200 OK to Stripe
 *
 * PUBLIC endpoint — security is Stripe HMAC signature verification.
 * NO X-Company-Id required — CompanyIdFilter skips /webhooks/** paths.
 *
 * Stripe requires 200 within 30 seconds. We always return 200 on
 * processing errors so Stripe does not flood retry attempts.
 * Internal retry is handled by the cron job (every 15 minutes).
 */
@Slf4j
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("Stripe-Signature") String signature,
            @RequestBody String payload) {

        try {
            stripeWebhookService.processWebhook(signature, payload);
            return ResponseEntity.ok().build();

        } catch (WebhookProcessingException ex) {
            if (ex.isSignatureFailure()) {
                // Architecture Plan: "Invalid Signature → 400 Bad Request"
                log.warn("Stripe signature verification failed: {}", ex.getMessage());
                return ResponseEntity.badRequest().build();
            }
            // Processing error — return 200 so Stripe doesn't retry endlessly.
            // Our cron job retries internally.
            log.error("Webhook processing error [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
            return ResponseEntity.ok().build();

        } catch (Exception ex) {
            log.error("Unexpected webhook error: {}", ex.getMessage(), ex);
            return ResponseEntity.ok().build();
        }
    }
}