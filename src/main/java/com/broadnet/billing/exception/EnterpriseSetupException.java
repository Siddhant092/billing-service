package com.broadnet.billing.exception;

/**
 * Thrown when enterprise customer setup or configuration is invalid.
 * Maps to HTTP 422 Unprocessable Entity.
 *
 * Examples:
 * - Attempting to set enterprise pricing when no company_billing record exists
 * - Converting a contact to enterprise when active pricing is not configured
 * - Tracking enterprise usage when billing_mode != postpaid
 * - Attempting to initialize an enterprise billing period when one already exists
 * - Setting enterprise pricing with effective_from in the past
 *
 * Architecture Plan: Enterprise setup requires specific preconditions
 * (billing_mode=postpaid, enterprise_pricing_id set, etc.)
 *
 * Usage:
 *   throw new EnterpriseSetupException("NO_ENTERPRISE_PRICING",
 *       "Cannot convert contact to enterprise: no active pricing configured for companyId: " + companyId);
 *   throw new EnterpriseSetupException("NOT_POSTPAID",
 *       "Cannot track enterprise usage: company " + companyId + " is not in postpaid billing mode");
 */
public class EnterpriseSetupException extends BillingException {

    public EnterpriseSetupException(String errorCode, String message) {
        super(errorCode, message);
    }

    public EnterpriseSetupException(String message) {
        super("ENTERPRISE_SETUP_ERROR", message);
    }
}
