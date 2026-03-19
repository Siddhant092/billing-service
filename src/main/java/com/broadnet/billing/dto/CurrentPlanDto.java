package com.broadnet.billing.dto;

import com.broadnet.billing.entity.CompanyBilling;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Current plan details response DTO.
 * Architecture Plan §1.2 GET /api/billing/current-plan.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentPlanDto {

    private String planCode;
    private String planName;
    private String description;
    private CompanyBilling.BillingInterval billingInterval;
    private String billingCycle;
    private LocalDateTime renewalDate;
    private CompanyBilling.SubscriptionStatus status;
    private Boolean cancelAtPeriodEnd;
    private LocalDateTime cancelAt;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    private Integer answersPerPeriod;
    private Integer kbPages;
    private Integer agents;
    private Integer users;

    /** Pending plan change details (if subscription_schedule is active). */
    private PendingChangeDto pendingChange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingChangeDto {
        private String pendingPlanCode;
        private String pendingPlanName;
        private LocalDateTime effectiveDate;
    }
}