package com.broadnet.billing.exception;

/**
 * Base class for all billing domain exceptions.
 * All custom exceptions in this package extend this.
 *
 * Using a base class lets the GlobalExceptionHandler catch
 * billing domain exceptions as a group when needed.
 */
public class BillingException extends RuntimeException {

    private final String errorCode;

    public BillingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BillingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
