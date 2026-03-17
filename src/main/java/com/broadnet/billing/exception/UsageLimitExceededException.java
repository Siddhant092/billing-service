package com.broadnet.billing.exception;

/**
 * Exception thrown when usage limit is exceeded
 */
public class UsageLimitExceededException extends RuntimeException {
    
    private final String limitType;
    private final Integer currentUsage;
    private final Integer limit;
    
    public UsageLimitExceededException(String message, String limitType, Integer currentUsage, Integer limit) {
        super(message);
        this.limitType = limitType;
        this.currentUsage = currentUsage;
        this.limit = limit;
    }
    
    public String getLimitType() {
        return limitType;
    }
    
    public Integer getCurrentUsage() {
        return currentUsage;
    }
    
    public Integer getLimit() {
        return limit;
    }
}
