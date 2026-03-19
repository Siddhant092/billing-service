package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingEnterprisePricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_enterprise_pricing table.
 *
 * CHANGES FROM ORIGINAL:
 * - All JPQL queries confirmed correct — entity fields match (companyId, isActive,
 *   effectiveFrom, effectiveTo, contractReference).
 * - findLatestByCompanyId: returns Optional but JPQL can return multiple rows.
 *   Fixed to use LIMIT 1 to ensure at most one result is returned.
 * - No enum fields are queried here (pricingTier not queried in existing methods).
 * - Minor: contractReference length in entity is 255 (was 100 in original) — no impact on repo.
 */
@Repository
public interface BillingEnterprisePricingRepository extends JpaRepository<BillingEnterprisePricing, Long> {

    /**
     * Find the currently active pricing for a company.
     * Architecture Plan: "Used by billing calculation to compute monthly invoices"
     * Time-based: effective_from <= now AND (effective_to IS NULL OR effective_to > now)
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.companyId = :companyId " +
            "AND bep.isActive = true " +
            "AND bep.effectiveFrom <= :currentDate " +
            "AND (bep.effectiveTo IS NULL OR bep.effectiveTo > :currentDate)")
    Optional<BillingEnterprisePricing> findActivePricingByCompanyId(
            @Param("companyId") Long companyId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find the most recently created pricing for a company (regardless of active status).
     * FIXED: Added LIMIT 1 — JPQL was returning multiple rows into Optional (throws if >1 result).
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.companyId = :companyId " +
            "ORDER BY bep.effectiveFrom DESC LIMIT 1")
    Optional<BillingEnterprisePricing> findLatestByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find all pricing versions for a company — for admin audit history.
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.companyId = :companyId " +
            "ORDER BY bep.effectiveFrom DESC")
    List<BillingEnterprisePricing> findAllByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find pricing by contract reference number.
     * Used by admin when looking up a contract.
     */
    Optional<BillingEnterprisePricing> findByContractReference(String contractReference);

    /**
     * Find all currently active pricing across all enterprise companies.
     * Used by monthly billing calculation cron job.
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.isActive = true " +
            "AND bep.effectiveFrom <= :currentDate " +
            "AND (bep.effectiveTo IS NULL OR bep.effectiveTo > :currentDate)")
    List<BillingEnterprisePricing> findActivePricing(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find pricing that was effective in a given date range for a company.
     * Used for retroactive billing calculations or audits.
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.companyId = :companyId " +
            "AND bep.effectiveFrom <= :endDate " +
            "AND (bep.effectiveTo IS NULL OR bep.effectiveTo >= :startDate)")
    List<BillingEnterprisePricing> findByCompanyIdAndEffectivePeriod(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find active pricing records whose effective_to has passed.
     * Used by a cron job to auto-expire outdated pricing.
     */
    @Query("SELECT bep FROM BillingEnterprisePricing bep WHERE bep.isActive = true " +
            "AND bep.effectiveTo IS NOT NULL AND bep.effectiveTo <= :currentDate")
    List<BillingEnterprisePricing> findExpiredPricing(@Param("currentDate") LocalDateTime currentDate);
}