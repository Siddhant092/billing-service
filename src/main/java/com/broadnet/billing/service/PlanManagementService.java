package com.broadnet.billing.service;

import com.broadnet.billing.dto.PlanDto;
import com.broadnet.billing.dto.PlanLimitDto;
import com.broadnet.billing.dto.AddonDto;
import java.util.List;

public interface PlanManagementService {

    /**
     * Get all active plans with their current limits
     */
    List<PlanDto> getAllActivePlans(String billingInterval);

    /**
     * Get plan details by plan code
     */
    PlanDto getPlanByCode(String planCode);

    /**
     * Get plan by ID (internal use)
     */
    PlanDto getPlanById(Long planId);

    /**
     * Create a new plan (Admin only)
     */
    PlanDto createPlan(PlanDto planDto);

    /**
     * Update plan limits (Admin only)
     */
    PlanDto updatePlanLimit(String planCode, PlanLimitDto limitDto);

    /**
     * Deactivate a plan (Admin only)
     */
    void deactivatePlan(String planCode);

    /**
     * Get all active addons
     */
    List<AddonDto> getAllActiveAddons(String category);

    /**
     * Get addon details by code
     */
    AddonDto getAddonByCode(String addonCode);

    /**
     * Get addon by ID (internal use)
     */
    AddonDto getAddonById(Long addonId);

    /**
     * Get all active addons by codes (bulk lookup)
     */
    List<AddonDto> getAddonsByCodes(List<String> addonCodes);

    /**
     * Create a new addon (Admin only)
     */
    AddonDto createAddon(AddonDto addonDto);

    /**
     * Update addon delta (Admin only)
     */
    AddonDto updateAddonDelta(String addonCode, com.broadnet.billing.dto.AddonDeltaDto deltaDto);

    /**
     * Deactivate an addon (Admin only)
     */
    void deactivateAddon(String addonCode);

    /**
     * Sync plans and addons with Stripe
     */
    int syncStripePrices();
}