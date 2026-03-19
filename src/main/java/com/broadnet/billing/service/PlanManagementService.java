package com.broadnet.billing.service;

import com.broadnet.billing.dto.AddonDto;
import com.broadnet.billing.dto.AddonDeltaDto;
import com.broadnet.billing.dto.PlanDto;
import com.broadnet.billing.dto.PlanLimitDto;
import com.broadnet.billing.entity.BillingAddon;
import com.broadnet.billing.entity.BillingPlanLimit;
import java.util.List;

/**
 * Service for plan and addon catalog management.
 *
 * Architecture Plan: billing_plans, billing_plan_limits, billing_addons,
 * billing_addon_deltas, billing_stripe_prices — all managed here.
 *
 * CHANGES FROM ORIGINAL:
 * - getAllActivePlans: billingInterval param type changed to BillingPlanLimit.BillingInterval enum
 * - getAllActiveAddons: category param type changed to BillingAddon.AddonCategory enum
 * - AddonDeltaDto import moved from inline to top-level import
 * - getAvailableBoosts: ADDED — architecture plan §1.5 defines a distinct "available boosts"
 *   API that returns addons with pricing + purchased status for the company
 * - No other structural changes — interface was architecturally aligned.
 */
public interface PlanManagementService {

    /**
     * Get all active plans with their current limits and pricing.
     * Architecture Plan §2.1: GET /api/billing/subscription/plans
     * Returns plans with limits for the specified billing interval.
     *
     * @param billingInterval The billing interval to fetch limits/pricing for
     * @return List of PlanDto with limits and pricing included
     */
    List<PlanDto> getAllActivePlans(BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get plan details by plan code.
     *
     * @param planCode The plan code (e.g. "professional")
     * @return PlanDto with full details
     */
    PlanDto getPlanByCode(String planCode);

    /**
     * Get plan by internal ID (used by service layer, not exposed via API).
     *
     * @param planId The plan ID
     * @return PlanDto
     */
    PlanDto getPlanById(Long planId);

    /**
     * Create a new plan. Admin only.
     *
     * @param planDto Plan details
     * @return Created PlanDto
     */
    PlanDto createPlan(PlanDto planDto);

    /**
     * Update a plan's limit. Admin only.
     * Architecture Plan: PUT /api/admin/billing/plans/{plan_code}/limits
     * Creates a new billing_plan_limits row with the new effective_from date,
     * marks the old row as inactive.
     *
     * @param planCode  The plan code
     * @param limitDto  The new limit definition
     * @return Updated PlanDto
     */
    PlanDto updatePlanLimit(String planCode, PlanLimitDto limitDto);

    /**
     * Deactivate a plan. Admin only.
     * Sets is_active=false. Does not affect existing subscriptions.
     *
     * @param planCode The plan code
     */
    void deactivatePlan(String planCode);

    /**
     * Get all active addons, optionally filtered by category.
     * FIXED: category param changed to BillingAddon.AddonCategory enum.
     *
     * @param category Filter by category (null = all categories)
     * @return List of AddonDto
     */
    List<AddonDto> getAllActiveAddons(BillingAddon.AddonCategory category);

    /**
     * Get available boosts for a company — with pricing and purchased status.
     * Architecture Plan §1.5: GET /api/billing/available-boosts
     *
     * Process:
     * 1. Fetch all active addons from billing_addons
     * 2. Get pricing from billing_stripe_prices (monthly + annual)
     * 3. Get active_addon_codes from company_billing
     * 4. Mark addons as isPurchased if in active_addon_codes
     *
     * @param companyId       The company ID (needed to determine isPurchased status)
     * @param billingInterval The billing interval for pricing display
     * @return List of AddonDto with pricing and purchased status
     */
    List<AddonDto> getAvailableBoosts(Long companyId, BillingPlanLimit.BillingInterval billingInterval);

    /**
     * Get addon details by addon code.
     *
     * @param addonCode The addon code (e.g. "answers_boost_m")
     * @return AddonDto
     */
    AddonDto getAddonByCode(String addonCode);

    /**
     * Get addon by internal ID (used by service layer).
     *
     * @param addonId The addon ID
     * @return AddonDto
     */
    AddonDto getAddonById(Long addonId);

    /**
     * Bulk lookup of addons by codes.
     * Used by EntitlementService when resolving active_addon_codes.
     *
     * @param addonCodes List of addon codes
     * @return List of AddonDto for found addons
     */
    List<AddonDto> getAddonsByCodes(List<String> addonCodes);

    /**
     * Create a new addon. Admin only.
     *
     * @param addonDto Addon details
     * @return Created AddonDto
     */
    AddonDto createAddon(AddonDto addonDto);

    /**
     * Update an addon's delta. Admin only.
     * Creates a new billing_addon_deltas row with new effective_from,
     * marks old row as inactive.
     *
     * @param addonCode The addon code
     * @param deltaDto  The new delta definition
     * @return Updated AddonDto
     */
    AddonDto updateAddonDelta(String addonCode, AddonDeltaDto deltaDto);

    /**
     * Deactivate an addon. Admin only.
     * Sets is_active=false. Does not remove from existing subscriptions immediately.
     *
     * @param addonCode The addon code
     */
    void deactivateAddon(String addonCode);

    /**
     * Sync plan and addon prices with Stripe.
     * Fetches prices from Stripe and updates billing_stripe_prices.
     *
     * @return Number of prices synced
     */
    int syncStripePrices();
}