package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Upcoming invoice preview from Stripe API.
 * Architecture Plan §1.3 billing-snapshot.nextInvoice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpcomingInvoiceDto {

    private Integer amount;
    private String amountFormatted;
    private Integer subtotal;
    private String subtotalFormatted;
    private Integer taxAmount;
    private String taxAmountFormatted;
    private LocalDateTime invoiceDate;
    private String currency;
}