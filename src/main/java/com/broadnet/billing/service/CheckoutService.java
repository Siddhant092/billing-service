package com.broadnet.billing.service;

import com.broadnet.billing.dto.CheckoutSessionRequest;
import com.broadnet.billing.dto.CheckoutSessionResponse;

/**
 * Service for handling Stripe checkout operations
 * 
 * Based on Architecture Plan:
 * - Section: API Design - Checkout API
 * - Endpoint: POST /api/billing/checkout/create-session
 */
public interface CheckoutService {
    
    /**
     * Create a Stripe checkout session for a plan subscription
     * 
     * @param companyId The company ID requesting checkout
     * @param request Checkout session request with plan_code, billing_interval, success_url, cancel_url
     * @return CheckoutSessionResponse with checkout_session_id and URL
     * 
     * Flow:
     * 1. Get or create Stripe customer
     * 2. Look up Stripe price by plan_code and billing_interval
     * 3. Create Stripe checkout session
     * 4. Return session ID and URL
     */
    CheckoutSessionResponse createCheckoutSession(Long companyId, CheckoutSessionRequest request);
    
    /**
     * Create checkout session for adding addon to existing subscription
     * 
     * @param companyId The company ID
     * @param addonCode The addon code to add
     * @param billingInterval The billing interval (month/year)
     * @return CheckoutSessionResponse with session details
     */
    CheckoutSessionResponse createAddonCheckoutSession(
        Long companyId, 
        String addonCode, 
        String billingInterval
    );
    
    /**
     * Handle successful checkout completion
     * Called after Stripe redirects to success_url
     * 
     * @param sessionId The Stripe checkout session ID
     * @return true if handling was successful
     * 
     * Note: Most subscription updates come via webhooks, 
     * this is just for immediate UI feedback
     */
    boolean handleCheckoutSuccess(String sessionId);
}
