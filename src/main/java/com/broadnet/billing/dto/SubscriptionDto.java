package com.broadnet.billing.dto;

import com.broadnet.billing.entity.CompanyBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current subscription state response DTO.
 * Returned by SubscriptionManagementService.getCurrentSubscription() and all
 * plan/addon change operations.
 *
 * Architecture Plan §2 Subscription Management APIs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDto {

    private String stripeSubscriptionId;
    private String stripeScheduleId;

    private CompanyBilling.SubscriptionStatus subscriptionStatus;
    private CompanyBilling.BillingInterval billingInterval;
    private CompanyBilling.BillingMode billingMode;

    /** Current active plan. */
    private String activePlanCode;
    private String activePlanName;

    /** Active addon codes. */
    private List<String> activeAddonCodes;

    /** Billing period dates. */
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    /** Cancellation state. */
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime cancelAt;
    private LocalDateTime canceledAt;

    /** Pending scheduled plan change (for annual downgrades). */
    private String pendingPlanCode;
    private String pendingPlanName;
    private LocalDateTime pendingEffectiveDate;

    /** Current effective entitlements. */
    private Integer effectiveAnswersLimit;
    private Integer effectiveKbPagesLimit;
    private Integer effectiveAgentsLimit;
    private Integer effectiveUsersLimit;

    /**
     * Optional checkout URL — populated when a plan change requires
     * a Stripe redirect (e.g. upgrading via Checkout Session).
     */
    private String checkoutUrl;
    private String checkoutSessionId;

    /** Effective date of the change (immediate or next renewal). */
    private LocalDateTime effectiveDate;
}
