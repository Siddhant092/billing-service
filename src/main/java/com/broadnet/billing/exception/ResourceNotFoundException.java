package com.broadnet.billing.exception;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 Not Found.
 *
 * Referenced in service Javadocs (e.g. getActivePricing, getCompanyBilling).
 *
 * Usage:
 *   throw new ResourceNotFoundException("CompanyBilling", "companyId", companyId);
 *   throw new ResourceNotFoundException("BillingPlan", "planCode", "professional");
 */
public class ResourceNotFoundException extends BillingException {

    private static final String ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(ERROR_CODE,
              String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    public ResourceNotFoundException(String message) {
        super(ERROR_CODE, message);
    }
}
