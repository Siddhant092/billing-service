package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for invoice details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDto {
    
    private Long id;
    private String stripeInvoiceId;
    private String invoiceNumber;
    private String status; // paid, open, void, uncollectible
    
    private Integer amountDue;
    private Integer amountPaid;
    private Integer subtotal;
    private Integer taxAmount;
    private Integer total;
    private String currency;
    
    private LocalDateTime invoiceDate;
    private LocalDateTime dueDate;
    private LocalDateTime paidAt;
    
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    
    private String hostedInvoiceUrl;
    private String invoicePdfUrl;
    
    private String description;
}
