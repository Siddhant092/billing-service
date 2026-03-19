package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * All available plans with current plan context.
 * Returned by SubscriptionManagementService.getAvailablePlans().
 *
 * Architecture Plan §2.1 GET /api/billing/subscription/plans response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailablePlansDto {

    /** The company's currently active plan code. */
    private String currentPlanCode;

    /** The company's current billing interval. */
    private String currentBillingInterval;

    /** All active plans with limits, pricing, and upgrade/downgrade flags. */
    private List<PlanDto> plans;
}
