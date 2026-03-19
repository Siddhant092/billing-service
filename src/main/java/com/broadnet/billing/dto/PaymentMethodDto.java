package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingPaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment method response DTO.
 * Architecture Plan §1.3 billing-snapshot.paymentMethod.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethodDto {

    private Long id;
    private String stripePaymentMethodId;
    private BillingPaymentMethod.PaymentMethodType type;
    private Boolean isDefault;
    private String cardBrand;
    private String cardLast4;
    private Integer cardExpMonth;
    private Integer cardExpYear;
    private Boolean isExpired;

    /** True if card expires within 30 days. */
    private Boolean isExpiringSoon;
}