package com.broadnet.billing.service;

import com.broadnet.billing.entity.CompanyBilling;

/**
 * Service for company billing record lifecycle management.
 *
 * Architecture Plan: company_billing is the central table — one record per company.
 * This service manages CRUD and state transitions on that record.
 *
 * CHANGES FROM ORIGINAL:
 * - initializeCompanyBilling: removed companyName and email params.
 *   The architecture plan does NOT store company name or email in company_billing
 *   (those live in the company table). company_billing only needs a companyId
 *   and a Stripe customer ID (created via Stripe API during initialization).
 *   The corrected signature creates a Stripe customer and stores stripe_customer_id.
 * - Added initializeCompanyBilling(Long companyId, String stripeCustomerId) overload
 *   for cases where Stripe customer already exists.
 * - All other methods confirmed correct against architecture plan.
 */
public interface CompanyBillingService {

    /**
     * Initialize billing for a new company.
     * Creates Stripe customer via Stripe API and stores stripe_customer_id.
     * Creates company_billing row with billing_mode=prepaid, version=1.
     *
     * Architecture Plan: "Company gains access → Stripe customer record needed"
     *
     * @param companyId The company ID (FK to company table)
     * @return Initialized CompanyBilling entity
     */
    CompanyBilling initializeCompanyBilling(Long companyId);

    /**
     * Initialize billing for a company using an existing Stripe customer ID.
     * Used when Stripe customer was created externally (e.g. migrated from another system).
     *
     * @param companyId        The company ID
     * @param stripeCustomerId Existing Stripe customer ID
     * @return Initialized CompanyBilling entity
     */
    CompanyBilling initializeCompanyBilling(Long companyId, String stripeCustomerId);

    /**
     * Get company billing record by company ID.
     * Throws ResourceNotFoundException if not found.
     *
     * @param companyId The company ID
     * @return CompanyBilling entity
     */
    CompanyBilling getCompanyBilling(Long companyId);

    /**
     * Get company billing by Stripe customer ID.
     * Used by webhook handler to resolve company from Stripe event.
     *
     * @param stripeCustomerId The Stripe customer ID (cus_xxx)
     * @return CompanyBilling entity
     */
    CompanyBilling getByStripeCustomerId(String stripeCustomerId);

    /**
     * Get company billing by Stripe subscription ID.
     * Used by webhook handler when resolving via subscription events.
     *
     * @param subscriptionId The Stripe subscription ID (sub_xxx)
     * @return CompanyBilling entity
     */
    CompanyBilling getCompanyBillingBySubscriptionId(String subscriptionId);

    /**
     * Get company billing with a PESSIMISTIC_WRITE lock.
     * Used by usage enforcement service before counter increments.
     *
     * Architecture Plan: "Row-level locking for usage increments"
     *
     * @param companyId The company ID
     * @return CompanyBilling entity with row lock held
     */
    CompanyBilling getCompanyBillingWithLock(Long companyId);

    /**
     * Save changes to company billing.
     * Respects optimistic locking — throws ObjectOptimisticLockingFailureException on conflict.
     *
     * @param companyBilling The updated entity
     * @return Saved CompanyBilling entity
     */
    CompanyBilling updateCompanyBilling(CompanyBilling companyBilling);

    /**
     * Sync company billing state from Stripe API.
     * Fetches current subscription from Stripe and reconciles with local state.
     * Used by daily sync cron job to catch any drift.
     *
     * Architecture Plan: Cron Job §4 — syncSubscriptionStatesFromStripe
     *
     * @param companyId The company ID
     * @return Updated CompanyBilling entity
     */
    CompanyBilling syncFromStripe(Long companyId);

    /**
     * Check if company has an active or trialing subscription.
     * Fast check — does not call Stripe API.
     *
     * @param companyId The company ID
     * @return true if subscription_status IN (active, trialing)
     */
    boolean hasActiveSubscription(Long companyId);

    /**
     * Apply payment failure service restrictions.
     * Sets service_restricted_at, restriction_reason=payment_failed, answers_blocked=true.
     * Called by the payment failure restriction cron job (daily at 01:00 UTC).
     *
     * Architecture Plan: Cron Job §2 — ApplyPaymentFailureRestrictions
     *
     * @param companyId The company ID
     */
    void applyPaymentFailureRestrictions(Long companyId);

    /**
     * Remove payment failure restrictions.
     * Clears: payment_failure_date, service_restricted_at, restriction_reason, answers_blocked.
     * Called after payment_succeeded webhook event.
     *
     * @param companyId The company ID
     */
    void removePaymentFailureRestrictions(Long companyId);
}