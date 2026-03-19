package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for company_billing table.
 * Central billing hub: one row per company.
 * Stores Stripe state, computed entitlement snapshot, usage counters, and enterprise config.
 *
 * Architecture Plan: Core Tables §6
 *
 * CHANGES FROM ORIGINAL:
 * - subscriptionStatus changed from plain String to @Enumerated SubscriptionStatus
 * - billingInterval changed from plain String to @Enumerated BillingInterval
 * - restrictionReason changed from plain String to @Enumerated RestrictionReason
 * - billingMode changed from plain String to @Enumerated BillingMode
 * - activePlanId (Long) replaced with proper @ManyToOne BillingPlan (active_plan_id)
 * - pendingPlanId (Long) replaced with proper @ManyToOne BillingPlan (pending_plan_id)
 * - answersResetDay: type corrected to Integer (TINYINT in schema, 1-28)
 * - Added all missing @Table indexes from schema
 * - Added missing @Table unique constraints from schema
 * - enterprisePricingId kept as bare Long (no bidirectional nav needed from this side)
 *
 * NOTE ON @Version:
 *   JPA @Version is used. Spring automatically increments version on every save().
 *   On conflict, throws ObjectOptimisticLockingFailureException — catch and retry in service layer.
 */
@Entity
@Table(
        name = "company_billing",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_company_id",        columnNames = "company_id"),
                @UniqueConstraint(name = "uk_stripe_customer",   columnNames = "stripe_customer_id"),
                @UniqueConstraint(name = "uk_stripe_subscription", columnNames = "stripe_subscription_id")
        },
        indexes = {
                @Index(name = "idx_subscription_status",  columnList = "subscription_status"),
                @Index(name = "idx_service_restricted",   columnList = "service_restricted_at"),
                @Index(name = "idx_payment_failure",      columnList = "payment_failure_date"),
                @Index(name = "idx_answers_reset_day",    columnList = "billing_interval, answers_reset_day")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id) ON DELETE CASCADE.
     * company_id is managed externally; we reference it as a bare Long
     * (no @ManyToOne here since Company entity is being fixed separately).
     * Once Company entity is confirmed correct, this can be promoted to @ManyToOne.
     */
    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    // -------------------------------------------------------------------------
    // Stripe Identifiers
    // -------------------------------------------------------------------------

    @Column(name = "stripe_customer_id", nullable = false, unique = true)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    @Column(name = "stripe_schedule_id")
    private String stripeScheduleId;

    // -------------------------------------------------------------------------
    // Subscription State
    // -------------------------------------------------------------------------

    /**
     * FIXED: Was plain String.
     * Schema ENUM: active, past_due, canceled, trialing, unpaid, incomplete, incomplete_expired
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", length = 30)
    private SubscriptionStatus subscriptionStatus;

    /**
     * FIXED: Was plain String. Schema defines ENUM('month','year').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 10)
    private BillingInterval billingInterval;

    @Column(name = "period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodStart;

    @Column(name = "period_end", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodEnd;

    // -------------------------------------------------------------------------
    // Cancellation State
    // -------------------------------------------------------------------------

    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @Column(name = "cancel_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelAt;

    @Column(name = "canceled_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime canceledAt;

    // -------------------------------------------------------------------------
    // Active Entitlements (Computed Snapshot — updated by webhook handlers)
    // -------------------------------------------------------------------------

    /**
     * FIXED: Was a bare Long activePlanId.
     * FK: fk_company_billing_plan → billing_plans(id) ON DELETE SET NULL
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_plan_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_company_billing_plan"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingPlan activePlan;

    /**
     * Denormalized plan_code for fast reads — kept in sync with activePlan.
     */
    @Column(name = "active_plan_code", length = 50)
    private String activePlanCode;

    @Column(name = "effective_answers_limit", nullable = false)
    @Builder.Default
    private Integer effectiveAnswersLimit = 0;

    @Column(name = "effective_kb_pages_limit", nullable = false)
    @Builder.Default
    private Integer effectiveKbPagesLimit = 0;

    @Column(name = "effective_agents_limit", nullable = false)
    @Builder.Default
    private Integer effectiveAgentsLimit = 0;

    @Column(name = "effective_users_limit", nullable = false)
    @Builder.Default
    private Integer effectiveUsersLimit = 0;

    /**
     * JSON array of active addon codes, e.g. ["answers_boost_m", "kb_boost_s"].
     * Not a join table — intentionally denormalized as JSON snapshot.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_addon_codes", columnDefinition = "JSON")
    private List<String> activeAddonCodes;

    // -------------------------------------------------------------------------
    // Pending Changes (for annual plan downgrades via subscription schedule)
    // -------------------------------------------------------------------------

    /**
     * FIXED: Was a bare Long pendingPlanId.
     * FK: fk_company_billing_pending_plan → billing_plans(id) ON DELETE SET NULL
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pending_plan_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_company_billing_pending_plan"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingPlan pendingPlan;

    @Column(name = "pending_plan_code", length = 50)
    private String pendingPlanCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_addon_codes", columnDefinition = "JSON")
    private List<String> pendingAddonCodes;

    @Column(name = "pending_effective_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime pendingEffectiveDate;

    // -------------------------------------------------------------------------
    // Usage Counters (updated atomically — see architecture locking section)
    // -------------------------------------------------------------------------

    @Column(name = "answers_used_in_period", nullable = false)
    @Builder.Default
    private Integer answersUsedInPeriod = 0;

    @Column(name = "answers_period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime answersPeriodStart;

    /**
     * Schema: TINYINT, values 1–28. Day-of-month when annual plan resets.
     * columnDefinition = "TINYINT" required so Hibernate schema-validation
     * sees TINYINT instead of expecting INTEGER.
     */
    @Column(name = "answers_reset_day", columnDefinition = "TINYINT")
    private Integer answersResetDay;

    @Column(name = "kb_pages_total", nullable = false)
    @Builder.Default
    private Integer kbPagesTotal = 0;

    @Column(name = "agents_total", nullable = false)
    @Builder.Default
    private Integer agentsTotal = 0;

    @Column(name = "users_total", nullable = false)
    @Builder.Default
    private Integer usersTotal = 0;

    // -------------------------------------------------------------------------
    // Grace Period & Restriction Tracking
    // -------------------------------------------------------------------------

    @Column(name = "payment_failure_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime paymentFailureDate;

    @Column(name = "service_restricted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime serviceRestrictedAt;

    /**
     * FIXED: Was plain String. Schema defines ENUM('payment_failed','canceled','admin').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_reason", length = 20)
    private RestrictionReason restrictionReason;

    // -------------------------------------------------------------------------
    // Block Flags
    // -------------------------------------------------------------------------

    @Column(name = "answers_blocked", nullable = false)
    @Builder.Default
    private Boolean answersBlocked = false;

    // -------------------------------------------------------------------------
    // Enterprise / Post-Paid Billing
    // -------------------------------------------------------------------------

    /**
     * FIXED: Was plain String. Schema defines ENUM('prepaid','postpaid').
     * prepaid  = subscription-based (Stripe)
     * postpaid = enterprise usage-based (invoice at period end)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 10)
    @Builder.Default
    private BillingMode billingMode = BillingMode.prepaid;

    /**
     * FK → billing_enterprise_pricing(id).
     * Kept as bare Long (no navigation needed in this direction).
     */
    @Column(name = "enterprise_pricing_id")
    private Long enterprisePricingId;

    /**
     * Billing period for post-paid (enterprise) customers.
     * Managed by cron job, not by Stripe subscription period.
     */
    @Column(name = "current_billing_period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime currentBillingPeriodStart;

    @Column(name = "current_billing_period_end", columnDefinition = "DATETIME(6)")
    private LocalDateTime currentBillingPeriodEnd;

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    @Column(name = "last_webhook_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastWebhookAt;

    @Column(name = "last_sync_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastSyncAt;

    /**
     * Optimistic locking — JPA manages increment on every save().
     * On conflict: ObjectOptimisticLockingFailureException → catch in service and retry.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Enums — all matching schema ENUM definitions exactly
    // -------------------------------------------------------------------------

    public enum SubscriptionStatus {
        active, past_due, canceled, trialing, unpaid, incomplete, incomplete_expired
    }

    public enum BillingInterval {
        month, year
    }

    public enum RestrictionReason {
        payment_failed, canceled, admin
    }

    public enum BillingMode {
        prepaid, postpaid
    }
}