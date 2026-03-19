package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingPlanLimit;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Request DTO for updating a plan limit.
 * Architecture Plan: PUT /api/admin/billing/plans/{plan_code}/limits
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanLimitDto {

    private BillingPlanLimit.LimitType limitType;
    private Integer limitValue;
    private BillingPlanLimit.BillingInterval billingInterval;

    /**
     * When the new limit takes effect.
     * Architecture Plan: "effective_from" — enables scheduled limit changes.
     */
    private LocalDateTime effectiveFrom;
}