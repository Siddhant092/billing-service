package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for billing_entitlement_history table.
 * Append-only audit log of every entitlement change.
 *
 * Architecture Plan: Core Tables §9
 *
 * CHANGES FROM ORIGINAL:
 * - changeType changed from plain String to @Enumerated ChangeType (matches schema ENUM)
 * - triggeredBy changed from plain String to @Enumerated TriggeredBy (matches schema ENUM)
 * - Added @Table indexes to match schema: idx_company_created, idx_stripe_event
 * - This is an INSERT-only audit table — no @UpdateTimestamp (correct, confirmed)
 */
@Entity
@Table(
        name = "billing_entitlement_history",
        indexes = {
                @Index(name = "idx_company_created", columnList = "company_id, created_at"),
                @Index(name = "idx_stripe_event",    columnList = "stripe_event_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEntitlementHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id) ON DELETE CASCADE.
     * Kept as bare Long until Company entity is confirmed.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * FIXED: Was plain String.
     * Schema ENUM: plan_change, addon_added, addon_removed, addon_upgraded, addon_downgraded, limit_update
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 30)
    private ChangeType changeType;

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

    /**
     * FIXED: Was plain String. Schema defines ENUM('webhook','admin','api').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false, length = 20)
    private TriggeredBy triggeredBy;

    @Column(name = "stripe_event_id", length = 255)
    private String stripeEventId;

    @Column(name = "effective_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    // -------------------------------------------------------------------------
    // Enums matching schema ENUM definitions exactly
    // -------------------------------------------------------------------------

    public enum ChangeType {
        plan_change, addon_added, addon_removed, addon_upgraded, addon_downgraded, limit_update
    }

    public enum TriggeredBy {
        webhook, admin, api
    }
}