package com.broadnet.billing.service;

import com.broadnet.billing.dto.EntitlementsDto;
import java.util.List;

public interface EntitlementService {

    /**
     * Compute entitlements from plan code and addon codes
     * This is the core entitlement calculation logic
     */
    EntitlementsDto computeEntitlements(
            String planCode,
            List<String> addonCodes,
            String billingInterval
    );

    /**
     * Compute entitlements for a company from its current subscription
     * Convenience method that extracts plan/addons from CompanyBilling
     *
     * @param companyId The company ID
     * @return EntitlementsDto with computed limits
     */
    EntitlementsDto computeEntitlementsForCompany(Long companyId);

    /**
     * Update company entitlements from Stripe subscription
     * Called by webhooks after subscription changes
     *
     * @param companyId The company ID
     * @param stripeSubscription The Stripe subscription object
     * @param triggeredBy Source of update ("webhook", "admin")
     * @param stripeEventId Optional event ID
     */
    void updateEntitlementsFromSubscription(
            Long companyId,
            com.stripe.model.Subscription stripeSubscription,
            String triggeredBy,
            String stripeEventId
    );

    /**
     * Update company entitlements and persist to database
     * Uses optimistic locking to prevent concurrent updates
     */
    void updateCompanyEntitlements(
            Long companyId,
            EntitlementsDto entitlements,
            String triggeredBy,
            String stripeEventId
    );

    /**
     * Recompute and update entitlements for all active subscriptions
     * Called when plan limits are changed by admin
     */
    int recomputeEntitlementsForPlan(String planCode);

    /**
     * Get current entitlements for a company
     */
    EntitlementsDto getCurrentEntitlements(Long companyId);

    /**
     * Preview entitlements for a plan before subscription
     * Used by pricing page to show what user will get
     */
    EntitlementsDto previewEntitlements(
            String planCode,
            List<String> addonCodes,
            String billingInterval
    );
}