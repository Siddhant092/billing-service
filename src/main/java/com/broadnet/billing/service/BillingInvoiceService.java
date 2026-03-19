package com.broadnet.billing.service;

import com.broadnet.billing.dto.InvoiceDto;
import com.broadnet.billing.entity.BillingInvoice;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for invoice management and retrieval.
 *
 * Architecture Plan: billing_invoices table, populated by invoice.* webhook events.
 *
 * CHANGES FROM ORIGINAL:
 * - getInvoicesByStatus: status param changed to BillingInvoice.InvoiceStatus enum
 * - getTotalAmountDue: return type changed to Integer (schema uses INTEGER cents, not Long)
 * - getTotalAmountPaid: return type changed to Integer
 * - updateInvoiceStatus: ADDED — webhook handlers update status without full Stripe re-fetch
 *   (architecture plan shows direct SQL updates for status changes)
 * - All other methods confirmed architecturally correct.
 */
public interface BillingInvoiceService {

    /**
     * Get invoice by Stripe invoice ID.
     *
     * @param stripeInvoiceId The Stripe invoice ID (in_xxx)
     * @return InvoiceDto
     */
    InvoiceDto getInvoiceByStripeId(String stripeInvoiceId);

    /**
     * Get all invoices for a company — paginated, newest-first.
     * Architecture Plan §3: GET /api/billing/invoices
     *
     * @param companyId The company ID
     * @param page      Page number
     * @param size      Page size
     * @return Paginated InvoiceDto
     */
    Page<InvoiceDto> getInvoicesByCompanyId(Long companyId, int page, int size);

    /**
     * Get invoices by status for a company.
     * FIXED: status param type changed to BillingInvoice.InvoiceStatus enum.
     *
     * @param companyId The company ID
     * @param status    Invoice status
     * @return List of InvoiceDto with given status
     */
    List<InvoiceDto> getInvoicesByStatus(Long companyId, BillingInvoice.InvoiceStatus status);

    /**
     * Get paid invoices for a company.
     *
     * @param companyId The company ID
     * @return List of paid InvoiceDto
     */
    List<InvoiceDto> getPaidInvoices(Long companyId);

    /**
     * Get unpaid invoices (open + draft) for a company.
     *
     * @param companyId The company ID
     * @return List of unpaid InvoiceDto
     */
    List<InvoiceDto> getUnpaidInvoices(Long companyId);

    /**
     * Get invoices in a date range for a company.
     *
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate   End date
     * @return List of InvoiceDto in range
     */
    List<InvoiceDto> getInvoicesByDateRange(Long companyId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get all overdue invoices across all companies.
     * Used for dunning/collection cron job.
     *
     * @return List of overdue InvoiceDto
     */
    List<InvoiceDto> getOverdueInvoices();

    /**
     * Get invoices due within N days.
     *
     * @param days Number of days to look ahead
     * @return Invoices due soon
     */
    List<InvoiceDto> getInvoicesDueSoon(int days);

    /**
     * Get total outstanding amount due for a company (sum of open+draft invoices).
     * FIXED: return type changed to Integer (schema uses INTEGER for cents).
     *
     * @param companyId The company ID
     * @return Total amount due in cents
     */
    Integer getTotalAmountDue(Long companyId);

    /**
     * Get total amount paid by a company in a period.
     * FIXED: return type changed to Integer.
     *
     * @param companyId The company ID
     * @param startDate Start date
     * @param endDate   End date
     * @return Total amount paid in cents
     */
    Integer getTotalAmountPaid(Long companyId, LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get the latest invoice for a company.
     * Used by billing snapshot API.
     *
     * @param companyId The company ID
     * @return Most recent InvoiceDto
     */
    InvoiceDto getLatestInvoice(Long companyId);

    /**
     * Create or update invoice from Stripe data.
     * Called by webhook handlers (invoice.created, invoice.finalized, etc.)
     * Uses ON DUPLICATE KEY UPDATE pattern (upsert by stripe_invoice_id).
     *
     * @param companyId       The company ID
     * @param stripeInvoiceId The Stripe invoice ID
     * @return Created/updated InvoiceDto
     */
    InvoiceDto createOrUpdateFromStripe(Long companyId, String stripeInvoiceId);

    /**
     * Update invoice status without re-fetching from Stripe.
     * ADDED: Used by webhook handlers for status-only updates (voided, uncollectible).
     * Architecture Plan: shows direct UPDATE for status changes.
     *
     * @param stripeInvoiceId The Stripe invoice ID
     * @param status          The new status
     */
    void updateInvoiceStatus(String stripeInvoiceId, BillingInvoice.InvoiceStatus status);

    /**
     * Mark invoice as paid.
     * Called by invoice.payment_succeeded webhook handler.
     *
     * @param stripeInvoiceId The Stripe invoice ID
     * @param paidAt          Timestamp when payment was confirmed
     * @param amountPaid      Amount paid in cents
     */
    void markAsPaid(String stripeInvoiceId, LocalDateTime paidAt, Integer amountPaid);

    /**
     * Download invoice PDF bytes from Stripe hosted URL.
     *
     * @param companyId       The company ID (for authorization)
     * @param stripeInvoiceId The Stripe invoice ID
     * @return PDF bytes
     */
    byte[] downloadInvoicePdf(Long companyId, String stripeInvoiceId);

    /**
     * Resend invoice email to customer via Stripe.
     *
     * @param stripeInvoiceId The Stripe invoice ID
     */
    void resendInvoice(String stripeInvoiceId);

    /**
     * Sync past invoices from Stripe for a company.
     * Used during onboarding or data recovery.
     *
     * @param companyId The company ID
     * @return Number of invoices synced
     */
    int syncPastInvoicesFromStripe(Long companyId);
}