package com.broadnet.billing.service;

import com.broadnet.billing.dto.UsageCheckResult;
import com.broadnet.billing.dto.UsageIncrementResult;

/**
 * Service for real-time usage enforcement and tracking
 * 
 * Based on Architecture Plan:
 * - Section: Usage Enforcement
 * - Section: API Design - Usage Enforcement APIs
 * - Implements atomic usage increments with row-level locking
 */
public interface UsageEnforcementService {
    
    /**
     * Atomically increment answer usage with limit checking
     * Uses SELECT FOR UPDATE for row-level locking
     * 
     * @param companyId The company ID
     * @return UsageIncrementResult with success status and current usage
     * @throws UsageLimitExceededException if limit is reached
     * 
     * Endpoint: POST /api/billing/usage/increment-answer
     * 
     * Flow:
     * 1. BEGIN TRANSACTION
     * 2. SELECT FOR UPDATE company_billing WHERE company_id = ?
     * 3. Check if answers_blocked = true → REJECT
     * 4. Check if answers_used >= effective_answers_limit → REJECT and SET blocked = true
     * 5. INCREMENT answers_used_in_period
     * 6. Set blocked = true if now at limit
     * 7. COMMIT
     * 8. Log usage in billing_usage_logs
     */
    UsageIncrementResult incrementAnswerUsage(Long companyId);
    
    /**
     * Check if KB page creation is allowed (does not increment)
     * 
     * @param companyId The company ID
     * @return UsageCheckResult with allowed status and current usage
     * 
     * Endpoint: POST /api/billing/usage/check-kb-page
     * 
     * Flow:
     * 1. SELECT kb_pages_total, effective_kb_pages_limit
     * 2. Return allowed = (kb_pages_total < effective_kb_pages_limit)
     */
    UsageCheckResult checkKbPageLimit(Long companyId);
    
    /**
     * Atomically increment KB pages count
     * Uses SELECT FOR UPDATE for row-level locking
     * 
     * @param companyId The company ID
     * @return UsageIncrementResult with success status
     * @throws UsageLimitExceededException if limit is reached
     * 
     * Flow:
     * 1. BEGIN TRANSACTION
     * 2. SELECT FOR UPDATE company_billing
     * 3. Check if kb_pages_total >= effective_kb_pages_limit → REJECT
     * 4. INCREMENT kb_pages_total
     * 5. COMMIT
     * 6. Log usage in billing_usage_logs
     */
    UsageIncrementResult incrementKbPageUsage(Long companyId);
    
    /**
     * Decrement KB pages (when page is deleted)
     * 
     * @param companyId The company ID
     */
    void decrementKbPageUsage(Long companyId);
    
    /**
     * Check if agent creation is allowed
     * 
     * @param companyId The company ID
     * @return UsageCheckResult with allowed status
     */
    UsageCheckResult checkAgentLimit(Long companyId);
    
    /**
     * Atomically increment agents count
     * 
     * @param companyId The company ID
     * @return UsageIncrementResult with success status
     * @throws UsageLimitExceededException if limit is reached
     */
    UsageIncrementResult incrementAgentUsage(Long companyId);
    
    /**
     * Decrement agents count (when agent is deleted)
     * 
     * @param companyId The company ID
     */
    void decrementAgentUsage(Long companyId);
    
    /**
     * Check if user creation is allowed
     * 
     * @param companyId The company ID
     * @return UsageCheckResult with allowed status
     */
    UsageCheckResult checkUserLimit(Long companyId);
    
    /**
     * Atomically increment users count
     * 
     * @param companyId The company ID
     * @return UsageIncrementResult with success status
     * @throws UsageLimitExceededException if limit is reached
     */
    UsageIncrementResult incrementUserUsage(Long companyId);
    
    /**
     * Decrement users count (when user is removed)
     * 
     * @param companyId The company ID
     */
    void decrementUserUsage(Long companyId);
    
    /**
     * Manually unblock answers for a company (admin operation)
     * 
     * @param companyId The company ID
     * @param adminUserId The admin user performing the action
     */
    void unblockAnswers(Long companyId, Long adminUserId);
    
    /**
     * Reset period usage counters (called by cron job or manual reset)
     * 
     * @param companyId The company ID
     */
    void resetPeriodUsage(Long companyId);
}
