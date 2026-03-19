package com.broadnet.billing.service;

import com.broadnet.billing.dto.CheckoutSessionRequest;
import com.broadnet.billing.dto.CheckoutSessionResponse;
import com.broadnet.billing.entity.BillingPlanLimit;

/**
 * Service for handling Stripe checkout operations.
 *
 * Architecture Plan: API Design §1 — Checkout API
 * Endpoint: POST /api/billing/checkout/create-session
 *
 * CHANGES FROM ORIGINAL:
 * - createAddonCheckoutSession: billingInterval param type changed to
 *   BillingPlanLimit.BillingInterval enum (was plain String)
 * - handleCheckoutSuccess: kept as-is — correct per architecture plan
 *   "Most subscription updates come via webhooks; this is for immediate UI feedback"
 * - No method additions — interface was architecturally complete.
 */
public interface CheckoutService {

    /**
     * Create a Stripe Checkout Session for a new plan subscription.
     *
     * Architecture Plan:
     * 1. Get or create Stripe customer (via stripe_customer_id in company_billing)
     * 2. Look up Stripe price via billing_stripe_prices.lookup_key = "plan_{planCode}_{interval}"
     * 3. Create Stripe Checkout Session in subscription mode
     * 4. Return session_id and checkout URL
     *
     * Endpoint: POST /api/billing/checkout/create-session
     *
     * @param companyId The company ID requesting checkout
     * @param request   Contains plan_code, billing_interval, success_url, cancel_url
     * @return CheckoutSessionResponse with checkout_session_id and URL
     */
    CheckoutSessionResponse createCheckoutSession(Long companyId, CheckoutSessionRequest request);

    /**
     * Create a Stripe Checkout Session for purchasing an addon.
     * Architecture Plan §1.9: POST /api/billing/boosts/purchase
     *
     * Process:
     * 1. Validate addon exists and is active
     * 2. Check addon is not already in active_addon_codes
     * 3. Look up Stripe price via lookup_key = "addon_{addonCode}_{interval}"
     * 4. Create Checkout Session with addon price
     * 5. Return session_id and URL
     *
     * FIXED: billingInterval param type changed to BillingPlanLimit.BillingInterval enum.
     *
     * @param companyId       The company ID
     * @param addonCode       The addon code to add (e.g. "answers_boost_m")
     * @param billingInterval The billing interval for the addon
     * @return CheckoutSessionResponse with session details
     */
    CheckoutSessionResponse createAddonCheckoutSession(
            Long companyId,
            String addonCode,
            BillingPlanLimit.BillingInterval billingInterval
    );

    /**
     * Handle successful checkout completion.
     * Called after Stripe redirects to success_url.
     *
     * Architecture Plan Note: "Most subscription updates come via webhooks.
     * This is just for immediate UI feedback."
     *
     * Typically just fetches the checkout session from Stripe to confirm
     * and optionally triggers an early webhook-style reconciliation.
     *
     * @param sessionId The Stripe checkout session ID
     * @return true if handling was successful
     */
    boolean handleCheckoutSuccess(String sessionId);
}