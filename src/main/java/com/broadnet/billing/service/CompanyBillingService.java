package com.broadnet.billing.service;

import com.broadnet.billing.entity.CompanyBilling;

/**
 * Service for company billing record management
 * 
 * Based on Architecture Plan:
 * - Section: Database Schema - company_billing table
 * - Handles company billing initialization and state management
 */
public interface CompanyBillingService {
    
    /**
     * Initialize billing for a new company
     * Creates company_billing record and Stripe customer
     * 
     * @param companyId The company ID
     * @param companyName The company name
     * @param email Primary contact email
     * @return Created CompanyBilling
     * 
     * Flow:
     * 1. Create Stripe customer
     * 2. Create company_billing record
     * 3. Set defaults (free plan, zero limits)
     * 
     * Called when: New company is registered
     */
    CompanyBilling initializeCompanyBilling(Long companyId, String companyName, String email);
    
    /**
     * Get company billing by company ID
     * 
     * @param companyId The company ID
     * @return CompanyBilling
     * @throws ResourceNotFoundException if not found
     */
    CompanyBilling getCompanyBilling(Long companyId);
    
    /**
     * Get company billing by Stripe customer ID
     * 
     * @param stripeCustomerId The Stripe customer ID
     * @return CompanyBilling
     */
    CompanyBilling getByStripeCustomerId(String stripeCustomerId);
    
    /**
     * Get company billing with pessimistic lock (for concurrent updates)
     * 
     * @param companyId The company ID
     * @return CompanyBilling with lock
     * 
     * Used by: Usage enforcement operations
     */
    CompanyBilling getCompanyBillingWithLock(Long companyId);
    
    /**
     * Update company billing with optimistic locking
     * 
     * @param companyBilling The updated company billing
     * @return Updated CompanyBilling
     * @throws OptimisticLockingException if version conflict
     */
    CompanyBilling updateCompanyBilling(CompanyBilling companyBilling);
    
    /**
     * Sync company billing state from Stripe
     * Fetches latest subscription data from Stripe and updates local state
     * 
     * @param companyId The company ID
     * @return Updated CompanyBilling
     * 
     * Endpoint: POST /api/billing/sync
     * 
     * Flow:
     * 1. Get Stripe subscription by stripe_subscription_id
     * 2. Extract status, period dates, items
     * 3. Compute entitlements
     * 4. Update company_billing
     */
    CompanyBilling syncFromStripe(Long companyId);
    
    /**
     * Check if company has active subscription
     * 
     * @param companyId The company ID
     * @return true if subscription is active or trialing
     */
    boolean hasActiveSubscription(Long companyId);
    
    /**
     * Apply payment failure restrictions
     * Called by cron job after grace period (7 days)
     * 
     * @param companyId The company ID
     * 
     * Actions:
     * 1. Set service_restricted_at
     * 2. Set restriction_reason = 'payment_failed'
     * 3. Block all usage
     */
    void applyPaymentFailureRestrictions(Long companyId);
    
    /**
     * Remove payment failure restrictions
     * Called when payment succeeds after being past_due
     * 
     * @param companyId The company ID
     * 
     * Actions:
     * 1. Clear service_restricted_at
     * 2. Clear payment_failure_date
     * 3. Clear restriction_reason
     * 4. Unblock answers
     */
    void removePaymentFailureRestrictions(Long companyId);
}
