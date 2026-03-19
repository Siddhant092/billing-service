package com.broadnet.billing.service;

import com.broadnet.billing.dto.UsageCheckResult;
import com.broadnet.billing.dto.UsageIncrementResult;

/**
 * Service for real-time usage enforcement and tracking.
 *
 * Architecture Plan: Usage Enforcement + API Design §2 (Usage Enforcement APIs)
 *
 * CHANGES FROM ORIGINAL:
 * - incrementAnswerUsage: added count param (architecture supports bulk increments,
 *   e.g. enterprise tracking uses count > 1; pre-paid default is 1)
 * - All enterprise-path tracking funnels through BillingEnterpriseUsageService;
 *   this service handles ONLY prepaid (blocking) enforcement.
 * - No structural changes beyond the above — interface was mostly correct.
 */
public interface UsageEnforcementService {

    /**
     * Atomically increment answer usage with limit checking.
     * Uses SELECT FOR UPDATE (pessimistic lock) for row-level safety.
     *
     * Architecture Plan: "Atomic increment" — single UPDATE with version check.
     * Returns success=false if answers_blocked=true OR answers_used >= limit.
     *
     * Endpoint: POST /api/billing/usage/increment-answer
     *
     * Flow:
     * 1. BEGIN TRANSACTION
     * 2. SELECT FOR UPDATE company_billing WHERE company_id = ?
     * 3. Check answers_blocked = true → REJECT immediately
     * 4. Check answers_used_in_period >= effective_answers_limit → SET blocked=true, REJECT
     * 5. UPDATE: increment answers_used_in_period, set blocked if now at limit, increment version
     * 6. COMMIT
     * 7. Append to billing_usage_logs (async-safe, outside transaction)
     *
     * @param companyId The company ID
     * @return UsageIncrementResult with success, answers_used, answers_limit, remaining, blocked
     */
    UsageIncrementResult incrementAnswerUsage(Long companyId);

    /**
     * Check if KB page creation is allowed without incrementing.
     * Read-only check — no transaction needed.
     *
     * Endpoint: POST /api/billing/usage/check-kb-page
     *
     * @param companyId The company ID
     * @return UsageCheckResult with allowed, kb_pages_total, kb_pages_limit, remaining
     */
    UsageCheckResult checkKbPageLimit(Long companyId);

    /**
     * Atomically increment KB pages count.
     * Uses SELECT FOR UPDATE (pessimistic lock).
     *
     * Architecture Plan: "Pessimistic Locking for KB/Agent Creation"
     *
     * @param companyId The company ID
     * @return UsageIncrementResult with success status and current usage
     */
    UsageIncrementResult incrementKbPageUsage(Long companyId);

    /**
     * Decrement KB pages when a page is deleted.
     * Ensures counters stay accurate when content is removed.
     *
     * @param companyId The company ID
     */
    void decrementKbPageUsage(Long companyId);

    /**
     * Check if agent creation is allowed without incrementing.
     *
     * @param companyId The company ID
     * @return UsageCheckResult with allowed, agents_total, agents_limit, remaining
     */
    UsageCheckResult checkAgentLimit(Long companyId);

    /**
     * Atomically increment agents count.
     * Uses SELECT FOR UPDATE (pessimistic lock).
     *
     * @param companyId The company ID
     * @return UsageIncrementResult with success status
     */
    UsageIncrementResult incrementAgentUsage(Long companyId);

    /**
     * Decrement agents count when an agent is deleted.
     *
     * @param companyId The company ID
     */
    void decrementAgentUsage(Long companyId);

    /**
     * Check if user creation is allowed without incrementing.
     *
     * @param companyId The company ID
     * @return UsageCheckResult with allowed, users_total, users_limit, remaining
     */
    UsageCheckResult checkUserLimit(Long companyId);

    /**
     * Atomically increment users count.
     * Uses SELECT FOR UPDATE (pessimistic lock).
     *
     * @param companyId The company ID
     * @return UsageIncrementResult with success status
     */
    UsageIncrementResult incrementUserUsage(Long companyId);

    /**
     * Decrement users count when a user is removed from the company.
     *
     * @param companyId The company ID
     */
    void decrementUserUsage(Long companyId);

    /**
     * Manually unblock answers for a company.
     * Admin-only operation — bypasses limit check.
     * Logs the unblock action in billing_usage_logs with admin metadata.
     *
     * @param companyId  The company ID
     * @param adminUserId The admin user performing the unblock
     */
    void unblockAnswers(Long companyId, Long adminUserId);

    /**
     * Reset period usage counters for a company.
     * Called by cron job (annual/monthly reset) or manually by admin.
     * Resets: answers_used_in_period=0, answers_period_start=now, answers_blocked=false.
     *
     * @param companyId The company ID
     */
    void resetPeriodUsage(Long companyId);
}