package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Billing snapshot response DTO.
 * Architecture Plan §1.3 GET /api/billing/billing-snapshot.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSnapshotDto {

    /** Next invoice preview from Stripe API. */
    private UpcomingInvoiceDto nextInvoice;

    /** Most recent invoice from billing_invoices table. */
    private InvoiceDto latestInvoice;

    /** Default payment method from billing_payment_methods. */
    private PaymentMethodDto paymentMethod;
}