package com.broadnet.billing.service;

import com.broadnet.billing.dto.EnterpriseUsageBillingDto;
import com.broadnet.billing.entity.BillingUsageLog;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for enterprise usage tracking and billing calculation.
 * Handles postpaid billing cycles and Stripe invoice generation.
 *
 * Architecture Plan: Enterprise Billing section + EnterpriseUsageService code sample
 *
 * CHANGES FROM ORIGINAL:
 * - trackUsage: usageType param changed to BillingUsageLog.UsageType enum (was plain String)
 * - trackUsage: count param changed from Long to Integer (schema uses INTEGER)
 * - getTotalRevenueInPeriod: return type changed from Long to Integer (schema INTEGER)
 * - getTotalRevenueByCompanyId: return type changed from Long to Integer
 * - getAverageMonthlyRevenue: return type changed from Long to Integer
 * - initializeCurrentPeriod: ADDED — enterprise companies need a billing record created
 *   at the start of each billing period; this method creates it
 * - No other structural changes needed.
 */
public interface BillingEnterpriseUsageService {

    /**
     * Get the billing record for a company in a specific period.
     * Creates one if it doesn't exist yet.
     * Architecture Plan: "Get or create current period billing record"
     *
     * @param companyId   The company ID
     * @param periodStart Start of billing period
     * @param periodEnd   End of billing period
     * @return EnterpriseUsageBillingDto for the period
     */
    EnterpriseUsageBillingDto getOrCreateBillingRecord(
            Long companyId,
            LocalDateTime periodStart,
            LocalDateTime periodEnd
    );

    /**
     * Initialize billing record for a company's current billing period.
     * ADDED: Called when a company is first set up as enterprise or at the start of each period.
     *
     * @param companyId The company ID
     * @return Created EnterpriseUsageBillingDto for the current period
     */
    EnterpriseUsageBillingDto initializeCurrentPeriod(Long companyId);

    /**
     * Get billing history for a company — paginated, newest period first.
     *
     * @param companyId The company ID
     * @param page      Page number
     * @param size      Page size
     * @return Page of EnterpriseUsageBillingDto
     */
    Page<EnterpriseUsageBillingDto> getBillingHistory(Long companyId, int page, int size);

    /**
     * Get billing records with status=pending.
     * Called by monthly billing calculation cron job.
     *
     * @return List of pending EnterpriseUsageBillingDto
     */
    List<EnterpriseUsageBillingDto> getPendingBillingRecords();

    /**
     * Calculate billing amounts for a specific billing record.
     * Applies enterprise pricing: amounts = usage * rate_per_unit.
     * Updates: billing_status=calculated, all *_amount_cents, subtotal, tax, total.
     *
     * Architecture Plan: Monthly billing calculation cron job.
     *
     * @param billingId The billing record ID
     * @return Updated EnterpriseUsageBillingDto with calculated amounts
     */
    EnterpriseUsageBillingDto calculateBilling(Long billingId);

    /**
     * Calculate billing for all due records (period_end passed, status=pending).
     * Called by monthly billing cron job.
     *
     * @return Number of records calculated
     */
    int calculateAllDueBillings();

    /**
     * Get billing records ready for invoicing (status=calculated).
     * Architecture Plan: "Query billing_enterprise_usage_billing where billing_status='calculated'"
     *
     * @return List of calculated EnterpriseUsageBillingDto ready for invoicing
     */
    List<EnterpriseUsageBillingDto> getReadyToInvoice();

    /**
     * Create a Stripe invoice for a calculated billing record.
     * Architecture Plan: Invoice Generation Process section.
     *
     * Flow:
     * 1. Get Stripe customer ID from company_billing
     * 2. Create Stripe Invoice with line items (answers, kb_pages, agents, users)
     * 3. Finalize invoice (auto-send)
     * 4. Update billing record: stripe_invoice_id, billing_status=invoiced, invoiced_at
     * 5. Create record in billing_invoices
     * 6. Create notification: invoice_created
     *
     * @param billingId The billing record ID
     * @return Updated EnterpriseUsageBillingDto with Stripe invoice reference
     */
    EnterpriseUsageBillingDto createStripeInvoice(Long billingId);

    /**
     * Create Stripe invoices for all calculated billing records.
     * Called by invoice generation cron job (1st of month 00:10 UTC).
     *
     * @return Number of invoices created
     */
    int createAllInvoices();

    /**
     * Track usage for an enterprise customer.
     * Architecture Plan: EnterpriseUsageService — "Always allow, just track"
     * Enterprise is POSTPAID — no blocking, just increment counters.
     * FIXED: usageType param changed to BillingUsageLog.UsageType enum;
     *        count param changed from Long to Integer.
     *
     * @param companyId The company ID
     * @param usageType Type of usage event
     * @param count     Quantity to track
     */
    void trackUsage(Long companyId, BillingUsageLog.UsageType usageType, Integer count);

    /**
     * Get total invoiced revenue for all enterprise companies in a period.
     * FIXED: return type changed from Long to Integer (schema uses INTEGER cents).
     *
     * @param startDate Start of period
     * @param endDate   End of period
     * @return Total revenue in cents
     */
    Integer getTotalRevenueInPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get total lifetime invoiced revenue for a specific company.
     * FIXED: return type changed from Long to Integer.
     *
     * @param companyId The company ID
     * @return Total lifetime revenue in cents
     */
    Integer getTotalRevenueByCompanyId(Long companyId);

    /**
     * Get a specific billing record by ID.
     *
     * @param billingId The billing record ID
     * @return EnterpriseUsageBillingDto
     */
    EnterpriseUsageBillingDto getBillingRecord(Long billingId);

    /**
     * Calculate average monthly revenue for a company over recent months.
     * Used by sales for account health reporting.
     * FIXED: return type changed from Long to Integer.
     *
     * @param companyId The company ID
     * @param months    Number of months to average over
     * @return Average monthly revenue in cents
     */
    Integer getAverageMonthlyRevenue(Long companyId, int months);
}