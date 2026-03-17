package com.broadnet.billing.service;

import com.broadnet.billing.dto.SubscriptionDto;
import com.broadnet.billing.dto.PlanChangeRequest;
import com.broadnet.billing.dto.AddonManagementRequest;

/**
 * Service for managing subscriptions (upgrades, downgrades, cancellations)
 * 
 * Based on Architecture Plan:
 * - Section: API Design - Subscription Management
 * - Handles plan changes, addon management, cancellations
 */
public interface SubscriptionManagementService {
    
    /**
     * Get current subscription details
     * 
     * @param companyId The company ID
     * @return SubscriptionDto with current plan, status, billing details
     * 
     * Endpoint: GET /api/billing/subscription
     */
    SubscriptionDto getCurrentSubscription(Long companyId);
    
    /**
     * Upgrade or downgrade subscription plan
     * 
     * @param companyId The company ID
     * @param request Plan change request (new plan_code, billing_interval)
     * @return SubscriptionDto with updated subscription
     * 
     * Endpoint: POST /api/billing/subscription/change-plan
     * 
     * Logic:
     * - Monthly plan changes: Immediate (proration)
     * - Annual plan upgrades: Immediate (proration)
     * - Annual plan downgrades: Scheduled at period end
     */
    SubscriptionDto changePlan(Long companyId, PlanChangeRequest request);
    
    /**
     * Add addon to subscription
     * 
     * @param companyId The company ID
     * @param addonCode The addon code to add
     * @return SubscriptionDto with updated subscription
     * 
     * Endpoint: POST /api/billing/subscription/addons/add
     * 
     * Flow:
     * 1. Get Stripe subscription
     * 2. Add addon price as subscription item
     * 3. Stripe sends webhook → entitlements recomputed
     */
    SubscriptionDto addAddon(Long companyId, String addonCode);
    
    /**
     * Remove addon from subscription
     * 
     * @param companyId The company ID
     * @param addonCode The addon code to remove
     * @return SubscriptionDto with updated subscription
     * 
     * Endpoint: POST /api/billing/subscription/addons/remove
     * 
     * Flow:
     * 1. Get Stripe subscription
     * 2. Remove addon subscription item
     * 3. Stripe sends webhook → entitlements recomputed
     */
    SubscriptionDto removeAddon(Long companyId, String addonCode);
    
    /**
     * Upgrade addon tier (e.g., answers_boost_s → answers_boost_m)
     * 
     * @param companyId The company ID
     * @param currentAddonCode Current addon code
     * @param newAddonCode New addon code
     * @return SubscriptionDto with updated subscription
     * 
     * Endpoint: POST /api/billing/subscription/addons/upgrade
     */
    SubscriptionDto upgradeAddon(Long companyId, String currentAddonCode, String newAddonCode);
    
    /**
     * Cancel subscription at period end
     * 
     * @param companyId The company ID
     * @return SubscriptionDto with cancellation details
     * 
     * Endpoint: POST /api/billing/subscription/cancel
     * 
     * Flow:
     * 1. Update Stripe subscription (cancel_at_period_end = true)
     * 2. Stripe sends webhook
     * 3. Update company_billing (cancel_at_period_end = true)
     */
    SubscriptionDto cancelSubscription(Long companyId);
    
    /**
     * Reactivate a canceled subscription (undo cancel_at_period_end)
     * 
     * @param companyId The company ID
     * @return SubscriptionDto with reactivated subscription
     * 
     * Endpoint: POST /api/billing/subscription/reactivate
     * 
     * Flow:
     * 1. Update Stripe subscription (cancel_at_period_end = false)
     * 2. Stripe sends webhook
     * 3. Update company_billing (cancel_at_period_end = false)
     */
    SubscriptionDto reactivateSubscription(Long companyId);
    
    /**
     * Preview subscription change (shows cost difference)
     * 
     * @param companyId The company ID
     * @param newPlanCode The new plan code
     * @param newBillingInterval The new billing interval
     * @return Preview with proration amount and effective date
     * 
     * Endpoint: POST /api/billing/subscription/preview-change
     */
    com.broadnet.billing.dto.SubscriptionPreviewDto previewPlanChange(
        Long companyId, 
        String newPlanCode, 
        String newBillingInterval
    );
}
