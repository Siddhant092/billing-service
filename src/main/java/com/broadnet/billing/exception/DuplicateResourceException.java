package com.broadnet.billing.exception;

/**
 * Thrown when attempting to create a resource that already exists
 * and an upsert is not the correct behavior.
 * Maps to HTTP 409 Conflict.
 *
 * Distinct from OptimisticLockingException (version conflict on update).
 * This is for create-time uniqueness violations, e.g.:
 * - Creating a company_billing record when one already exists for that company
 * - Creating a pricing record with an already-active contract reference
 * - Attempting to add an addon that is already in active_addon_codes
 *
 * Usage:
 *   throw new DuplicateResourceException("CompanyBilling", "companyId", companyId);
 *   throw new DuplicateResourceException("Addon already purchased", "addonCode", "answers_boost_m");
 */
public class DuplicateResourceException extends BillingException {

    private static final String ERROR_CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String resourceName, String fieldName, Object fieldValue) {
        super(ERROR_CODE,
              String.format("%s already exists with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public DuplicateResourceException(String message) {
        super(ERROR_CODE, message);
    }
}
