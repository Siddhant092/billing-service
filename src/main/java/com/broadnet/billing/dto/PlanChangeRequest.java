package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/billing/subscription/change-plan
 * Architecture Plan §2.2
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeRequest {

    /** Target plan code (e.g. "business"). */
    private String planCode;

    /** Target billing interval ("month" or "year"). */
    private String billingInterval;

    /**
     * Stripe proration behavior.
     * "create_prorations" (default), "none", "always_invoice"
     */
    private String prorationBehavior;
}