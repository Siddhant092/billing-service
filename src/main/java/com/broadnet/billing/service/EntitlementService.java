package com.broadnet.billing.service;

import com.broadnet.billing.dto.EntitlementsDto;
import com.stripe.model.Subscription;
import java.util.List;

/**
 * Service for computing and updating company entitlements.
 *
 * Architecture Plan: Entitlement computation is the core of the billing system.
 * Entitlements = base plan limits + sum of active addon deltas.
 * The computed snapshot is stored in company_billing.effective_*_limit fields.
 *
 * CHANGES FROM ORIGINAL:
 * - updateEntitlementsFromSubscription: Stripe import moved to correct package
 *   (was com.stripe.model.Subscription inline — now proper import at top)
 * - triggeredBy params: type narrowed to BillingEntitlementHistory.TriggeredBy enum
 *   (was plain String — matches corrected entity)
 * - updateCompanyEntitlements: triggeredBy param changed to enum
 * - No method additions or removals — contract was architecturally correct.
 */
public interface EntitlementService {

    /**
     * Core entitlement computation logic.
     * Queries billing_plan_limits for the plan and billing_addon_deltas for each addon.
     * Returns the computed limits — does NOT write to DB.
     *
     * Architecture Plan: EntitlementService.computeEntitlements(planLookupKey, addonLookupKeys)
     *
     * @param planCode       The plan code (e.g. "professional")
     * @param addonCodes     List of active addon codes (e.g. ["answers_boost_m", "kb_boost_s"])
     * @param billingInterval The billing interval ("month" or "year") — used to select correct limit rows
     * @return EntitlementsDto with computed answers/kb_pages/agents/users limits
     */
    EntitlementsDto computeEntitlements(
            String planCode,
            List<String> addonCodes,
            String billingInterval
    );

    /**
     * Convenience method: compute entitlements for a company from its current DB state.
     * Reads active_plan_code, active_addon_codes, billing_interval from company_billing.
     *
     * @param companyId The company ID
     * @return EntitlementsDto with computed limits
     */
    EntitlementsDto computeEntitlementsForCompany(Long companyId);

    /**
     * Update company entitlements from a Stripe Subscription object.
     * Called by webhook handlers after any subscription change.
     *
     * Architecture Plan:
     * 1. Extract planLookupKey and addonLookupKeys from subscription items
     * 2. computeEntitlements(...)
     * 3. updateCompanyEntitlements(...) with optimistic locking
     * 4. Log to billing_entitlement_history
     * 5. Create notifications
     *
     * @param companyId          The company ID
     * @param stripeSubscription The Stripe subscription object
     * @param triggeredBy        Source of update (webhook/admin/api)
     * @param stripeEventId      Stripe event ID for idempotency (nullable for admin-triggered)
     */
    void updateEntitlementsFromSubscription(
            Long companyId,
            Subscription stripeSubscription,
            com.broadnet.billing.entity.BillingEntitlementHistory.TriggeredBy triggeredBy,
            String stripeEventId
    );

    /**
     * Persist computed entitlements to company_billing with optimistic locking.
     * Uses @Version field — retries on ObjectOptimisticLockingFailureException (max 3x).
     *
     * Architecture Plan: "Optimistic Locking Pattern" — always check version before write.
     *
     * @param companyId   The company ID
     * @param entitlements The computed entitlements to persist
     * @param triggeredBy  Source (webhook/admin/api)
     * @param stripeEventId Stripe event ID (nullable)
     */
    void updateCompanyEntitlements(
            Long companyId,
            EntitlementsDto entitlements,
            com.broadnet.billing.entity.BillingEntitlementHistory.TriggeredBy triggeredBy,
            String stripeEventId
    );

    /**
     * Recompute and update entitlements for all companies on a given plan.
     * Called by admin when plan limits are changed.
     *
     * Architecture Plan: "Recomputing Entitlements for All Companies"
     * — After plan limit changes, trigger entitlement recomputation.
     *
     * @param planCode The plan code whose limits changed
     * @return Number of companies updated
     */
    int recomputeEntitlementsForPlan(String planCode);

    /**
     * Get current effective entitlements for a company (read from DB snapshot).
     * Fast path — reads from company_billing.effective_*_limit (no re-computation).
     *
     * @param companyId The company ID
     * @return EntitlementsDto from the stored snapshot
     */
    EntitlementsDto getCurrentEntitlements(Long companyId);

    /**
     * Preview what entitlements would be on a given plan+addons.
     * Used by pricing page and subscription change preview — does NOT write to DB.
     *
     * @param planCode       The plan code to preview
     * @param addonCodes     Addon codes to include in preview
     * @param billingInterval The billing interval
     * @return EntitlementsDto with projected limits
     */
    EntitlementsDto previewEntitlements(
            String planCode,
            List<String> addonCodes,
            String billingInterval
    );
}