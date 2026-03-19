package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingPaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_payment_methods table.
 *
 * STATUS: NEW — This repository did not exist in the original codebase.
 *
 * Populated exclusively by webhook events:
 *   - payment_method.attached  → create/upsert record
 *   - payment_method.updated   → update expiration and expired flag
 *   - payment_method.detached  → delete record
 *
 * Used by:
 *   - /api/billing/billing-snapshot → returns default payment method
 *   - /api/billing/details          → returns all payment methods
 */
@Repository
public interface BillingPaymentMethodRepository extends JpaRepository<BillingPaymentMethod, Long> {

    /**
     * Find payment method by Stripe payment method ID.
     * Primary lookup for webhook handlers (upsert pattern).
     */
    Optional<BillingPaymentMethod> findByStripePaymentMethodId(String stripePaymentMethodId);

    /**
     * Check if payment method already exists.
     */
    boolean existsByStripePaymentMethodId(String stripePaymentMethodId);

    /**
     * Find all payment methods for a company.
     * Used by /api/billing/details to list all payment methods.
     */
    List<BillingPaymentMethod> findByCompanyId(Long companyId);

    /**
     * Find the default payment method for a company.
     * Architecture Plan: "payment_method WHERE company_id=? AND is_default=TRUE"
     * Used by /api/billing/billing-snapshot API.
     */
    Optional<BillingPaymentMethod> findByCompanyIdAndIsDefaultTrue(Long companyId);

    /**
     * Find all non-expired payment methods for a company.
     */
    List<BillingPaymentMethod> findByCompanyIdAndIsExpiredFalse(Long companyId);

    /**
     * Find expired payment methods for a company.
     * Used for expired card notification.
     */
    List<BillingPaymentMethod> findByCompanyIdAndIsExpiredTrue(Long companyId);

    /**
     * Find all non-default payment methods for a company.
     * Used when removing a payment method to check if default needs to be reassigned.
     */
    List<BillingPaymentMethod> findByCompanyIdAndIsDefaultFalse(Long companyId);

    /**
     * Find payment methods by type for a company.
     */
    List<BillingPaymentMethod> findByCompanyIdAndType(
            Long companyId,
            BillingPaymentMethod.PaymentMethodType type
    );

    /**
     * Find payment methods expiring in a given month/year (for expiry notifications).
     * Architecture Plan: "payment_method.updated — check if expiring soon (30 days before expiry)"
     */
    @Query("SELECT bpm FROM BillingPaymentMethod bpm WHERE bpm.isExpired = false " +
            "AND bpm.cardExpYear = :year AND bpm.cardExpMonth = :month")
    List<BillingPaymentMethod> findExpiringInMonth(
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    /**
     * Find all expired payment methods platform-wide (for cron cleanup/notification).
     */
    @Query("SELECT bpm FROM BillingPaymentMethod bpm WHERE bpm.isExpired = false " +
            "AND bpm.cardExpYear IS NOT NULL " +
            "AND (bpm.cardExpYear < :currentYear OR " +
            "(bpm.cardExpYear = :currentYear AND bpm.cardExpMonth < :currentMonth))")
    List<BillingPaymentMethod> findActuallyExpired(
            @Param("currentYear") Integer currentYear,
            @Param("currentMonth") Integer currentMonth
    );

    /**
     * Mark all existing default payment methods for a company as non-default.
     * Called before setting a new default.
     */
    @Modifying
    @Query("UPDATE BillingPaymentMethod bpm SET bpm.isDefault = false " +
            "WHERE bpm.companyId = :companyId AND bpm.isDefault = true")
    int clearDefaultForCompany(@Param("companyId") Long companyId);

    /**
     * Mark a payment method as expired.
     * Called by payment method expiry cron job.
     */
    @Modifying
    @Query("UPDATE BillingPaymentMethod bpm SET bpm.isExpired = true " +
            "WHERE bpm.stripePaymentMethodId = :stripePaymentMethodId")
    void markAsExpired(@Param("stripePaymentMethodId") String stripePaymentMethodId);

    /**
     * Delete a payment method by Stripe payment method ID.
     * Called by payment_method.detached webhook event.
     */
    @Modifying
    @Query("DELETE FROM BillingPaymentMethod bpm WHERE bpm.stripePaymentMethodId = :stripePaymentMethodId")
    void deleteByStripePaymentMethodId(@Param("stripePaymentMethodId") String stripePaymentMethodId);

    /**
     * Count payment methods for a company.
     * Used to validate before adding new ones (optional limit enforcement).
     */
    long countByCompanyId(Long companyId);
}