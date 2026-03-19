package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/billing/checkout/create-session
 * Architecture Plan §1 Checkout API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSessionRequest {

    /** Plan code to subscribe to (e.g. "professional"). */
    private String planCode;

    /** Billing interval ("month" or "year"). */
    private String billingInterval;

    /** URL to redirect to after successful checkout. */
    private String successUrl;

    /** URL to redirect to if user cancels checkout. */
    private String cancelUrl;
}