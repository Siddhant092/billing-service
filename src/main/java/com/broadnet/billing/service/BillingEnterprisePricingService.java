package com.broadnet.billing.service;

import com.broadnet.billing.dto.EnterprisePricingDto;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for enterprise customer pricing management.
 * Handles custom per-unit pricing for postpaid/enterprise customers.
 *
 * Architecture Plan: billing_enterprise_pricing table + Enterprise Pricing Management section
 * API: POST /api/admin/enterprise/pricing
 *
 * CHANGES FROM ORIGINAL:
 * - calculateAmountDue: param/return types changed from Long to Integer
 *   (schema uses INTEGER for all monetary amounts in cents)
 * - isMinimumCommitmentMet: amountDue param changed to Integer
 * - setPricing: approvedBy param renamed from adminUserId for clarity (matches schema field approved_by)
 * - No structural additions or removals needed.
 */
public interface BillingEnterprisePricingService {

    /**
     * Get the currently active pricing for a company.
     * Queries: isActive=true AND effectiveFrom<=now AND (effectiveTo IS NULL OR effectiveTo>now)
     *
     * @param companyId The company ID
     * @return Active EnterprisePricingDto
     */
    EnterprisePricingDto getActivePricing(Long companyId);

    /**
     * Get the most recently created pricing for a company.
     *
     * @param companyId The company ID
     * @return Latest EnterprisePricingDto regardless of active status
     */
    EnterprisePricingDto getLatestPricing(Long companyId);

    /**
     * Get all pricing versions for a company (full history).
     *
     * @param companyId The company ID
     * @return List of EnterprisePricingDto ordered by effective_from DESC
     */
    List<EnterprisePricingDto> getPricingHistory(Long companyId);

    /**
     * Create or update enterprise pricing for a company.
     * Architecture Plan: POST /api/admin/enterprise/pricing
     *
     * Process:
     * 1. Create record in billing_enterprise_pricing
     * 2. Set approved_by, approved_at
     * 3. Update company_billing.enterprise_pricing_id
     * 4. Create notification for customer about pricing update
     *
     * @param companyId  The company ID
     * @param dto        The pricing details (rates, commitments, effective dates, contract reference)
     * @param approvedBy The admin user ID who is approving this pricing
     * @return Created/updated EnterprisePricingDto
     */
    EnterprisePricingDto setPricing(Long companyId, EnterprisePricingDto dto, Long approvedBy);

    /**
     * Get pricing that was effective on a specific date.
     * Used for retroactive billing recalculations.
     *
     * @param companyId The company ID
     * @param date      The date to check
     * @return EnterprisePricingDto effective on that date
     */
    EnterprisePricingDto getPricingOnDate(Long companyId, LocalDateTime date);

    /**
     * Find pricing by contract reference number.
     *
     * @param contractReference The contract reference (e.g. "ENT-2025-001")
     * @return EnterprisePricingDto
     */
    EnterprisePricingDto getPricingByContractReference(String contractReference);

    /**
     * Expire a pricing record (set effective_to = now, is_active = false).
     * Used when replacing pricing with a new version.
     *
     * @param pricingId The pricing record ID
     */
    void expirePricing(Long pricingId);

    /**
     * Deactivate a pricing record (set is_active = false without changing effective_to).
     *
     * @param pricingId The pricing record ID
     */
    void deactivatePricing(Long pricingId);

    /**
     * Calculate the total amount due for a given usage set.
     * Applies the company's active pricing rates and volume discounts.
     * Architecture Plan: "Calculate Bill" — applies rates from billing_enterprise_pricing.
     * FIXED: param/return types changed from Long to Integer (schema uses INTEGER cents).
     *
     * @param companyId   The company ID (to look up active pricing)
     * @param answersUsed Number of answers used
     * @param kbPagesUsed Number of KB pages
     * @param agentsUsed  Number of agents
     * @param usersUsed   Number of users
     * @return Total amount due in cents
     */
    Integer calculateAmountDue(
            Long companyId,
            Integer answersUsed,
            Integer kbPagesUsed,
            Integer agentsUsed,
            Integer usersUsed
    );

    /**
     * Check if the calculated amount meets the minimum monthly commitment.
     * FIXED: amountDue param changed to Integer.
     *
     * @param companyId The company ID
     * @param amountDue Calculated amount in cents
     * @return true if amountDue >= minimum_monthly_commitment_cents
     */
    boolean isMinimumCommitmentMet(Long companyId, Integer amountDue);

    /**
     * Get all currently active enterprise pricing records (across all companies).
     * Used by the monthly billing calculation cron job.
     *
     * @return List of EnterprisePricingDto for all active enterprise pricing
     */
    List<EnterprisePricingDto> getAllActivePricing();

    /**
     * Get company IDs where pricing has expired or is missing.
     * Used by admin to identify accounts needing pricing renewal.
     *
     * @return List of company IDs needing pricing update
     */
    List<Long> getCustomersNeedingPricingUpdate();
}