package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for payment method details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMethodDto {
    
    private Long id;
    private String stripePaymentMethodId;
    private String type; // card, sepa_debit, etc.
    private Boolean isDefault;
    
    // Card Details
    private String cardBrand; // visa, mastercard, amex
    private String cardLast4;
    private Integer cardExpMonth;
    private Integer cardExpYear;
    private Boolean isExpired;
    private Boolean isExpiringSoon; // within 30 days
    
    // Billing Details
    private String billingName;
    private String billingEmail;
}
