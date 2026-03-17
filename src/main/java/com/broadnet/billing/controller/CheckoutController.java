package com.broadnet.billing.controller;

import com.broadnet.billing.dto.CheckoutSessionRequest;
import com.broadnet.billing.dto.CheckoutSessionResponse;
import com.broadnet.billing.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Stripe Checkout
 * Base path: /api/billing/checkout
 *
 * Handles new subscription and addon checkout flows:
 * - Creates a Stripe Checkout Session and returns the redirect URL
 * - After Stripe redirects back to success_url, provides immediate feedback
 *   (the real subscription state comes via webhook)
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    /**
     * POST /api/billing/checkout/create-session
     * Creates a Stripe Checkout session for a new plan subscription.
     *
     * Request body: { "planCode": "starter", "billingInterval": "month",
     *                 "successUrl": "...", "cancelUrl": "..." }
     * Response:     { "checkoutSessionId": "cs_xxx", "url": "https://checkout.stripe.com/..." }
     *
     * Frontend redirects user to the returned URL.
     */
    @PostMapping("/create-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestParam Long companyId,
            @RequestBody CheckoutSessionRequest request) {
        log.info("POST /api/billing/checkout/create-session for company {} plan {}",
                companyId, request.getPlanCode());
        return ResponseEntity.ok(checkoutService.createCheckoutSession(companyId, request));
    }

    /**
     * POST /api/billing/checkout/addon-session?addonCode=answers_boost_s&billingInterval=month
     * Creates a Stripe Checkout session to add an addon to an existing subscription.
     */
    @PostMapping("/addon-session")
    public ResponseEntity<CheckoutSessionResponse> createAddonCheckoutSession(
            @RequestParam Long companyId,
            @RequestParam String addonCode,
            @RequestParam(defaultValue = "month") String billingInterval) {
        log.info("POST /api/billing/checkout/addon-session for company {} addon {}",
                companyId, addonCode);
        return ResponseEntity.ok(
                checkoutService.createAddonCheckoutSession(companyId, addonCode, billingInterval));
    }

    /**
     * POST /api/billing/checkout/success?sessionId=cs_xxx
     * Called after Stripe redirects to success_url.
     * Provides immediate UI feedback — the definitive state update comes via webhook.
     */
    @PostMapping("/success")
    public ResponseEntity<Boolean> handleCheckoutSuccess(@RequestParam String sessionId) {
        log.info("POST /api/billing/checkout/success sessionId={}", sessionId);
        return ResponseEntity.ok(checkoutService.handleCheckoutSuccess(sessionId));
    }
}