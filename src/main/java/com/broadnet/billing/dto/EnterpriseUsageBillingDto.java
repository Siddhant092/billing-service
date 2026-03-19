package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingEnterpriseUsageBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enterprise usage billing record DTO.
 * Returned by BillingEnterpriseUsageService methods.
 *
 * Architecture Plan: billing_enterprise_usage_billing table.
 * Status flow: pending → calculated → invoiced → paid.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseUsageBillingDto {

    private Long id;
    private Long companyId;

    private LocalDateTime billingPeriodStart;
    private LocalDateTime billingPeriodEnd;
    private BillingEnterpriseUsageBilling.BillingStatus billingStatus;

    /** Usage quantities. */
    private Integer answersUsed;
    private Integer kbPagesUsed;
    private Integer agentsUsed;
    private Integer usersUsed;

    /** Pricing rates snapshot (copied from billing_enterprise_pricing at time of calculation). */
    private Integer answersRateCents;
    private Integer kbPagesRateCents;
    private Integer agentsRateCents;
    private Integer usersRateCents;

    /** Calculated monetary amounts. */
    private Integer answersAmountCents;
    private Integer kbPagesAmountCents;
    private Integer agentsAmountCents;
    private Integer usersAmountCents;
    private Integer subtotalCents;
    private Integer taxAmountCents;
    private Integer totalCents;

    /** Formatted totals for display. */
    private String totalFormatted;

    /** Invoice references. */
    private String stripeInvoiceId;
    private Long invoiceId;
    private LocalDateTime invoicedAt;

    private String calculationNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
