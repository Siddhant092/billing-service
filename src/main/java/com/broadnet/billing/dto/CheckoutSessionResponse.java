package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for POST /api/billing/checkout/create-session
 * Architecture Plan: { checkout_session_id, url }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionResponse {

    /** Stripe checkout session ID (cs_xxx). */
    private String checkoutSessionId;

    /** Stripe-hosted checkout URL to redirect the user to. */
    private String url;

    /** Addon code (populated only for addon checkout sessions). */
    private String addonCode;

    /** Addon name (populated only for addon checkout sessions). */
    private String addonName;
}