package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for upcoming invoice preview
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpcomingInvoiceDto {
    
    private Integer amountDue;
    private Integer subtotal;
    private Integer taxAmount;
    private Integer total;
    private String currency;
    
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private LocalDateTime dueDate;
    
    private String description;
}
