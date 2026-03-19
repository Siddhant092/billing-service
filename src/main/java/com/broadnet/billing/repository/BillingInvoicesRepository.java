package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingInvoice;
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
 * Repository for billing_invoices table.
 *
 * CHANGES FROM ORIGINAL:
 * - findByCompanyIdAndStatus: status param changed to BillingInvoice.InvoiceStatus enum
 * - findPaidInvoicesByCompanyId: 'paid' literal → enum constant
 * - findUnpaidInvoicesByCompanyId: 'open','draft' literals → enum constants
 * - findByStatus: status param changed to enum
 * - findOverdueInvoices: 'open','draft' literals → enum constants
 * - findInvoicesDueSoon: 'open' literal → enum constant
 * - getTotalAmountDueByCompanyId: 'open','draft' literals → enum constants
 *   Return type changed Long → Integer (schema uses INTEGER, not BIGINT for monetary amounts)
 * - getTotalAmountPaidByCompanyIdAndPeriod: return type Long → Integer (schema INTEGER)
 * - findLatestByCompanyId: LIMIT 1 confirmed in JPQL (MySQL compatible)
 */
@Repository
public interface BillingInvoicesRepository extends JpaRepository<BillingInvoice, Long> {

    /** Find invoice by Stripe invoice ID — primary lookup for webhook handlers. */
    Optional<BillingInvoice> findByStripeInvoiceId(String stripeInvoiceId);

    /** Check if invoice already exists by Stripe invoice ID (idempotency check). */
    boolean existsByStripeInvoiceId(String stripeInvoiceId);

    /**
     * Find all invoices for a company — paginated, newest-first.
     * Used by /api/billing/invoices endpoint.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId ORDER BY bi.invoiceDate DESC")
    Page<BillingInvoice> findByCompanyId(
            @Param("companyId") Long companyId,
            Pageable pageable
    );

    /**
     * Find invoices by company and status.
     * FIXED: status param type changed to BillingInvoice.InvoiceStatus enum.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId " +
            "AND bi.status = :status ORDER BY bi.invoiceDate DESC")
    List<BillingInvoice> findByCompanyIdAndStatus(
            @Param("companyId") Long companyId,
            @Param("status") BillingInvoice.InvoiceStatus status
    );

    /**
     * Find paid invoices for a company.
     * FIXED: 'paid' literal → enum constant.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId " +
            "AND bi.status = com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.paid " +
            "ORDER BY bi.invoiceDate DESC")
    List<BillingInvoice> findPaidInvoicesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find unpaid invoices for a company (open + draft).
     * FIXED: 'open','draft' literals → enum constants.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId " +
            "AND bi.status IN (" +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.open, " +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.draft) " +
            "ORDER BY bi.invoiceDate DESC")
    List<BillingInvoice> findUnpaidInvoicesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find invoices for a company in a specific date range.
     * Used by enterprise usage billing report.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId " +
            "AND bi.invoiceDate BETWEEN :startDate AND :endDate ORDER BY bi.invoiceDate DESC")
    List<BillingInvoice> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all invoices with a given status — across all companies.
     * FIXED: status param type changed to BillingInvoice.InvoiceStatus enum.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.status = :status ORDER BY bi.invoiceDate DESC")
    List<BillingInvoice> findByStatus(@Param("status") BillingInvoice.InvoiceStatus status);

    /**
     * Find overdue invoices (open/draft, past due date).
     * Used by dunning cron job.
     * FIXED: status literals → enum constants.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.status IN (" +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.open, " +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.draft) " +
            "AND bi.dueDate < :currentDate ORDER BY bi.dueDate ASC")
    List<BillingInvoice> findOverdueInvoices(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find invoices due soon (within a date window).
     * FIXED: 'open' literal → enum constant.
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.status = " +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.open " +
            "AND bi.dueDate BETWEEN :startDate AND :endDate ORDER BY bi.dueDate ASC")
    List<BillingInvoice> findInvoicesDueSoon(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get total outstanding amount due for a company.
     * FIXED: status literals → enum constants. Return type Integer (schema uses INTEGER cents).
     */
    @Query("SELECT COALESCE(SUM(bi.amountDue - bi.amountPaid), 0) FROM BillingInvoice bi " +
            "WHERE bi.companyId = :companyId AND bi.status IN (" +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.open, " +
            "com.broadnet.billing.entity.BillingInvoice$InvoiceStatus.draft)")
    Integer getTotalAmountDueByCompanyId(@Param("companyId") Long companyId);

    /**
     * Get total amount paid by a company in a date range (revenue reporting).
     * FIXED: Return type Integer (schema uses INTEGER cents).
     */
    @Query("SELECT COALESCE(SUM(bi.amountPaid), 0) FROM BillingInvoice bi " +
            "WHERE bi.companyId = :companyId AND bi.paidAt BETWEEN :startDate AND :endDate")
    Integer getTotalAmountPaidByCompanyIdAndPeriod(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /** Find invoice by human-readable invoice number. */
    Optional<BillingInvoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Find the latest invoice for a company (for billing snapshot API).
     * Architecture Plan: "Get latest invoice from billing_invoices ORDER BY invoice_date DESC LIMIT 1"
     */
    @Query("SELECT bi FROM BillingInvoice bi WHERE bi.companyId = :companyId " +
            "ORDER BY bi.invoiceDate DESC LIMIT 1")
    Optional<BillingInvoice> findLatestByCompanyId(@Param("companyId") Long companyId);
}