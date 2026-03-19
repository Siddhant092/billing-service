package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingEnterpriseUsageBilling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_enterprise_usage_billing table.
 *
 * CHANGES FROM ORIGINAL:
 * - billingStatus param type changed to BillingEnterpriseUsageBilling.BillingStatus enum throughout
 * - findPendingBillingRecords: 'pending' literal → enum constant
 * - findCalculatedNotInvoiced: 'calculated' literal → enum constant
 * - findInvoicedByPeriod: 'invoiced' literal → enum constant
 * - findByCompanyIdAndStatus: status param → enum
 * - getTotalRevenueInPeriod: 'invoiced' literal → enum constant; return type Long → Integer
 *   (schema uses INTEGER for monetary amounts)
 * - getTotalRevenueByCompanyId: same fix
 * - REMOVED: findByEnterprisePricingId — the enterprisePricingId field was removed from entity
 *   (not in architecture plan schema for this table)
 * - countByStatus: billingStatus is enum — returns Object[] with BillingStatus enum values
 */
@Repository
public interface BillingEnterpriseUsageBillingRepository extends JpaRepository<BillingEnterpriseUsageBilling, Long> {

    /**
     * Find the billing record for a company within a billing period.
     * Unique constraint on (company_id, billing_period_start, billing_period_end) ensures one result.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.companyId = :companyId " +
            "AND beub.billingPeriodStart <= :periodEnd " +
            "AND beub.billingPeriodEnd >= :periodStart")
    Optional<BillingEnterpriseUsageBilling> findByCompanyIdAndPeriod(
            @Param("companyId") Long companyId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd
    );

    /**
     * Find all billing records for a company — paginated, newest period first.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.companyId = :companyId " +
            "ORDER BY beub.billingPeriodStart DESC")
    Page<BillingEnterpriseUsageBilling> findByCompanyId(
            @Param("companyId") Long companyId,
            Pageable pageable
    );

    /**
     * Find billing records by company and status.
     * FIXED: status param type changed to BillingEnterpriseUsageBilling.BillingStatus enum.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.companyId = :companyId " +
            "AND beub.billingStatus = :status ORDER BY beub.billingPeriodStart DESC")
    List<BillingEnterpriseUsageBilling> findByCompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") BillingEnterpriseUsageBilling.BillingStatus status
    );

    /**
     * Find all pending billing records (not yet calculated).
     * Used by monthly billing calculation cron job.
     * FIXED: 'pending' literal → enum constant.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.pending " +
            "ORDER BY beub.billingPeriodEnd ASC")
    List<BillingEnterpriseUsageBilling> findPendingBillingRecords();

    /**
     * Find calculated but not-yet-invoiced billing records.
     * Architecture Plan: "Query billing_enterprise_usage_billing where billing_status = 'calculated'"
     * Used by invoice generation cron job (1st of month 00:10 UTC).
     * FIXED: 'calculated' literal → enum constant.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.calculated " +
            "ORDER BY beub.billingPeriodEnd ASC")
    List<BillingEnterpriseUsageBilling> findCalculatedNotInvoiced();

    /**
     * Find invoiced records within a specific date range.
     * FIXED: 'invoiced' literal → enum constant.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.invoiced " +
            "AND beub.invoicedAt BETWEEN :startDate AND :endDate ORDER BY beub.invoicedAt DESC")
    List<BillingEnterpriseUsageBilling> findInvoicedByPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /** Find billing record by Stripe invoice ID (for webhook invoice.payment_succeeded). */
    Optional<BillingEnterpriseUsageBilling> findByStripeInvoiceId(String stripeInvoiceId);

    /**
     * Get total invoiced revenue across all enterprise companies in a period.
     * FIXED: 'invoiced' literal → enum constant; return type Long → Integer (schema INTEGER).
     */
    @Query("SELECT COALESCE(SUM(beub.totalCents), 0) FROM BillingEnterpriseUsageBilling beub " +
            "WHERE beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.invoiced " +
            "AND beub.invoicedAt BETWEEN :startDate AND :endDate")
    Integer getTotalRevenueInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total invoiced revenue for a specific company (all time).
     * FIXED: 'invoiced' literal → enum constant; return type Long → Integer.
     */
    @Query("SELECT COALESCE(SUM(beub.totalCents), 0) FROM BillingEnterpriseUsageBilling beub " +
            "WHERE beub.companyId = :companyId AND beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.invoiced")
    Integer getTotalRevenueByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find billing records whose period has ended but are still pending.
     * Used by billing calculation cron job.
     */
    @Query("SELECT beub FROM BillingEnterpriseUsageBilling beub WHERE beub.billingStatus = " +
            "com.broadnet.billing.entity.BillingEnterpriseUsageBilling$BillingStatus.pending " +
            "AND beub.billingPeriodEnd <= :currentDate ORDER BY beub.billingPeriodEnd ASC")
    List<BillingEnterpriseUsageBilling> findDueBillingRecords(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Count billing records grouped by status.
     * Returns List<Object[]> with [BillingStatus enum, Long count].
     */
    @Query("SELECT beub.billingStatus, COUNT(beub) FROM BillingEnterpriseUsageBilling beub " +
            "GROUP BY beub.billingStatus")
    List<Object[]> countByStatus();
}