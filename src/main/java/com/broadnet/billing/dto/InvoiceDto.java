package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingInvoice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Invoice response DTO.
 * Architecture Plan §3 GET /api/billing/invoices response item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDto {

    private Long id;
    private Long companyId;
    private String stripeInvoiceId;
    private String invoiceNumber;
    private BillingInvoice.InvoiceStatus status;

    private Integer amountDue;
    private String amountDueFormatted;
    private Integer amountPaid;
    private String amountPaidFormatted;
    private Integer subtotal;
    private Integer taxAmount;
    private Integer total;
    private String totalFormatted;
    private String currency;

    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private String hostedInvoiceUrl;
    private String invoicePdfUrl;

    /** JSON string of line items from Stripe. */
    private String lineItems;

    private LocalDateTime createdAt;
}