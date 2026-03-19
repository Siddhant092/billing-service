package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Computed entitlements snapshot.
 * Returned by EntitlementService.computeEntitlements().
 * Values are stored in company_billing.effective_*_limit fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementsDto {

    private String planCode;
    private String billingInterval;

    /** Effective answers limit (base plan + addon deltas). */
    private Integer answersLimit;

    /** Effective KB pages limit. */
    private Integer kbPagesLimit;

    /** Effective agents limit. */
    private Integer agentsLimit;

    /** Effective users limit. */
    private Integer usersLimit;
}