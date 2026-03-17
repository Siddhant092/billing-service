package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for billing_entitlement_history table
 * Audit trail for entitlement changes
 */
@Entity
@Table(name = "billing_entitlement_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEntitlementHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType; // plan_change, addon_added, addon_removed, addon_upgraded, addon_downgraded, limit_update

    @Column(name = "old_plan_code", length = 50)
    private String oldPlanCode;

    @Column(name = "new_plan_code", length = 50)
    private String newPlanCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_addon_codes", columnDefinition = "JSON")
    private List<String> oldAddonCodes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_addon_codes", columnDefinition = "JSON")
    private List<String> newAddonCodes;

    @Column(name = "old_answers_limit")
    private Integer oldAnswersLimit;

    @Column(name = "new_answers_limit")
    private Integer newAnswersLimit;

    @Column(name = "old_kb_pages_limit")
    private Integer oldKbPagesLimit;

    @Column(name = "new_kb_pages_limit")
    private Integer newKbPagesLimit;

    @Column(name = "old_agents_limit")
    private Integer oldAgentsLimit;

    @Column(name = "new_agents_limit")
    private Integer newAgentsLimit;

    @Column(name = "old_users_limit")
    private Integer oldUsersLimit;

    @Column(name = "new_users_limit")
    private Integer newUsersLimit;

    @Column(name = "triggered_by", nullable = false, length = 20)
    private String triggeredBy; // webhook, admin, api

    @Column(name = "stripe_event_id")
    private String stripeEventId;

    @Column(name = "effective_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
