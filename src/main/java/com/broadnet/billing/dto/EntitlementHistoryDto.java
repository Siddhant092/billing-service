package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingEntitlementHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entitlement change history record DTO.
 * Returned by BillingDashboardService.getEntitlementHistory().
 * Maps to billing_entitlement_history table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementHistoryDto {

    private Long id;
    private Long companyId;
    private BillingEntitlementHistory.ChangeType changeType;

    private String oldPlanCode;
    private String newPlanCode;

    private List<String> oldAddonCodes;
    private List<String> newAddonCodes;

    private Integer oldAnswersLimit;
    private Integer newAnswersLimit;
    private Integer oldKbPagesLimit;
    private Integer newKbPagesLimit;
    private Integer oldAgentsLimit;
    private Integer newAgentsLimit;
    private Integer oldUsersLimit;
    private Integer newUsersLimit;

    private BillingEntitlementHistory.TriggeredBy triggeredBy;
    private String stripeEventId;
    private LocalDateTime effectiveDate;
    private LocalDateTime createdAt;
}
