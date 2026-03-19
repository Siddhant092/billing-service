package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Preview of a planned subscription change — proration costs.
 * Returned by SubscriptionManagementService.previewPlanChange().
 *
 * Architecture Plan §2: POST /api/billing/subscription/preview-change
 * Uses Stripe's upcoming invoice preview API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPreviewDto {

    /** Current plan code. */
    private String currentPlanCode;
    private String currentPlanName;

    /** Target plan code. */
    private String newPlanCode;
    private String newPlanName;
    private String newBillingInterval;

    /**
     * Proration amount in cents.
     * Positive = credit (downgrade), Negative = charge (upgrade).
     */
    private Integer prorationAmountCents;
    private String prorationAmountFormatted;

    /** When the change takes effect. */
    private LocalDateTime effectiveDate;

    /**
     * "immediate" — change applies now with proration.
     * "next_renewal" — change scheduled via subscription schedule.
     */
    private String changeType;

    /** Next invoice amount after the change (cents). */
    private Integer nextInvoiceAmountCents;
    private String nextInvoiceAmountFormatted;
    private LocalDateTime nextInvoiceDate;

    /** New entitlements after the change. */
    private Integer newAnswersLimit;
    private Integer newKbPagesLimit;
    private Integer newAgentsLimit;
    private Integer newUsersLimit;
}
