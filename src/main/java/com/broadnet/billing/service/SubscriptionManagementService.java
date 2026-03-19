package com.broadnet.billing.service;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.BillingPlanLimit;

/**
 * Service for managing subscriptions: upgrades, downgrades, addons, cancellations.
 *
 * Architecture Plan: API Design §2 — Subscription Management APIs
 *
 * CHANGES FROM ORIGINAL:
 * - getAvailablePlans: ADDED — architecture plan §2.1 GET /api/billing/subscription/plans
 *   (was missing; belongs here as subscription management)
 * - changePlan: return type changed to include redirect URL per architecture plan §2.2
 *   (plan changes may require Stripe checkout redirect)
 * - cancelSubscription: added cancelAtPeriodEnd param — architecture plan §2.3 supports
 *   both immediate and period-end cancellation
 * - previewPlanChange: billingInterval param type changed to BillingPlanLimit.BillingInterval enum
 * - All addon management methods confirmed correct.
 * - AddonManagementRequest import: kept for potential future use but not used in current signatures
 */
public interface SubscriptionManagementService {

    /**
     * Get available plans with limits, pricing, and upgrade/downgrade eligibility.
     * Architecture Plan §2.1: GET /api/billing/subscription/plans
     * ADDED: Was missing from original.
     *
     * Process:
     * 1. Fetch all active plans from billing_plans
     * 2. Get current limits from billing_plan_limits (active, effective)
     * 3. For enterprise plans: set billingMode=postpaid, pricing.type=custom
     * 4. For regular plans: get pricing from billing_stripe_prices (monthly + annual)
     * 5. Get company's active_plan_code from company_billing
     * 6. Determine canUpgrade/canDowngrade based on plan hierarchy
     *
     * @param companyId       The company ID (needed for isCurrent flags)
     * @param billingInterval Billing interval for pricing display
     * @return AvailablePlansDto with currentPlanCode and list of plans
     */
    AvailablePlansDto getAvailablePlans(Long companyId, BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get current subscription details.
     * Architecture Plan: GET /api/billing/subscription
     *
     * @param companyId The company ID
     * @return SubscriptionDto with current plan, status, billing details, addons
     */
    SubscriptionDto getCurrentSubscription(Long companyId);

    /**
     * Upgrade or downgrade subscription plan.
     * Architecture Plan §2.2: POST /api/billing/subscription/change-plan
     *
     * Logic:
     * - Monthly plan changes → immediate via Stripe API (proration)
     * - Annual plan upgrades → immediate via Stripe API (proration)
     * - Annual plan downgrades → subscription schedule for next renewal
     *   → sets pending_plan_id, pending_effective_date in company_billing
     *
     * @param companyId The company ID
     * @param request   Contains newPlanCode, billingInterval, prorationBehavior
     * @return SubscriptionDto with updated state and optional checkoutUrl for redirect
     */
    SubscriptionDto changePlan(Long companyId, PlanChangeRequest request);

    /**
     * Add addon to existing subscription.
     * Architecture Plan: POST /api/billing/subscription/addons/add
     *
     * Flow:
     * 1. Look up addon Stripe price via lookup_key = "addon_{addonCode}_{interval}"
     * 2. Add as subscription item via Stripe API
     * 3. customer.subscription.updated webhook → EntitlementService recomputes
     *
     * @param companyId The company ID
     * @param addonCode The addon code to add
     * @return SubscriptionDto with updated addon list
     */
    SubscriptionDto addAddon(Long companyId, String addonCode);

    /**
     * Remove addon from existing subscription.
     * Architecture Plan: POST /api/billing/subscription/addons/remove
     *
     * Flow:
     * 1. Find subscription item for addon
     * 2. Delete subscription item via Stripe API
     * 3. customer.subscription.updated webhook → EntitlementService recomputes
     *
     * @param companyId The company ID
     * @param addonCode The addon code to remove
     * @return SubscriptionDto with updated addon list
     */
    SubscriptionDto removeAddon(Long companyId, String addonCode);

    /**
     * Upgrade addon tier (e.g. answers_boost_s → answers_boost_m).
     * Architecture Plan: POST /api/billing/subscription/addons/upgrade
     *
     * Flow:
     * 1. Remove current addon subscription item
     * 2. Add new addon subscription item
     * 3. Webhook → EntitlementService recomputes (logs addon_upgraded)
     *
     * @param companyId        The company ID
     * @param currentAddonCode Current addon to replace
     * @param newAddonCode     New addon to add
     * @return SubscriptionDto with updated state
     */
    SubscriptionDto upgradeAddon(Long companyId, String currentAddonCode, String newAddonCode);

    /**
     * Cancel subscription.
     * Architecture Plan §2.3: POST /api/billing/subscription/cancel
     * FIXED: added cancelAtPeriodEnd param — architecture plan supports both
     * immediate and period-end cancellation modes.
     *
     * Flow:
     * 1. Get subscription from Stripe
     * 2. If cancelAtPeriodEnd=true: update subscription cancel_at_period_end=true
     * 3. If cancelAtPeriodEnd=false: cancel subscription immediately
     * 4. customer.subscription.updated webhook updates company_billing
     * 5. Create notification: subscription_canceled
     *
     * @param companyId          The company ID
     * @param cancelAtPeriodEnd  true = cancel at period end, false = cancel immediately
     * @return SubscriptionDto with cancellation details
     */
    SubscriptionDto cancelSubscription(Long companyId, boolean cancelAtPeriodEnd);

    /**
     * Reactivate a subscription that was set to cancel at period end.
     * Architecture Plan §2.4: POST /api/billing/subscription/reactivate
     *
     * Flow:
     * 1. Update Stripe subscription: cancel_at_period_end=false
     * 2. customer.subscription.updated webhook clears cancel flags in company_billing
     *
     * @param companyId The company ID
     * @return SubscriptionDto with reactivated state
     */
    SubscriptionDto reactivateSubscription(Long companyId);

    /**
     * Preview what a plan change would cost (proration calculation).
     * Architecture Plan: POST /api/billing/subscription/preview-change
     * FIXED: billingInterval param type changed to BillingPlanLimit.BillingInterval enum.
     *
     * Uses Stripe's upcoming invoice preview API to calculate proration.
     *
     * @param companyId          The company ID
     * @param newPlanCode        The target plan code
     * @param newBillingInterval The target billing interval
     * @return SubscriptionPreviewDto with proration amount and effective date
     */
    SubscriptionPreviewDto previewPlanChange(
            Long companyId,
            String newPlanCode,
            BillingPlanLimit.BillingInterval newBillingInterval
    );
}