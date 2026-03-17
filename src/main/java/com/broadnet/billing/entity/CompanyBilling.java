package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for company_billing table
 * Main table storing company billing state and entitlements
 */
@Entity
@Table(name = "company_billing")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    // Stripe Identifiers
    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "stripe_schedule_id")
    private String stripeScheduleId;

    // Subscription State
    @Column(name = "subscription_status", length = 30)
    private String subscriptionStatus; // active, past_due, canceled, trialing, unpaid, incomplete, incomplete_expired

    @Column(name = "billing_interval", length = 10)
    private String billingInterval; // month, year

    @Column(name = "period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodStart;

    @Column(name = "period_end", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodEnd;

    // Cancellation State
    @Column(name = "cancel_at_period_end")
    private Boolean cancelAtPeriodEnd;

    @Column(name = "cancel_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime cancelAt;

    @Column(name = "canceled_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime canceledAt;

    // Active Entitlements (Computed Snapshot)
    @Column(name = "active_plan_id")
    private Long activePlanId;

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

    // Active Add-ons (JSON array of addon codes)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "active_addon_codes", columnDefinition = "JSON")
    private List<String> activeAddonCodes;

    // Pending Changes (Annual Plans)
    @Column(name = "pending_plan_id")
    private Long pendingPlanId;

    @Column(name = "pending_plan_code", length = 50)
    private String pendingPlanCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_addon_codes", columnDefinition = "JSON")
    private List<String> pendingAddonCodes;

    @Column(name = "pending_effective_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime pendingEffectiveDate;

    // Usage Counters
    @Column(name = "answers_used_in_period", nullable = false)
    @Builder.Default
    private Integer answersUsedInPeriod = 0;

    @Column(name = "answers_period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime answersPeriodStart;

    @Column(name = "answers_reset_day")
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

    // Grace Period Tracking
    @Column(name = "payment_failure_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime paymentFailureDate;

    @Column(name = "service_restricted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime serviceRestrictedAt;

    @Column(name = "restriction_reason", length = 20)
    private String restrictionReason; // payment_failed, canceled, admin

    // Block Flags
    @Column(name = "answers_blocked", nullable = false)
    @Builder.Default
    private Boolean answersBlocked = false;

    // Enterprise/Post-Paid Billing
    @Column(name = "billing_mode", nullable = false, length = 10)
    @Builder.Default
    private String billingMode = "prepaid"; // prepaid, postpaid

    @Column(name = "enterprise_pricing_id")
    private Long enterprisePricingId;

    @Column(name = "current_billing_period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime currentBillingPeriodStart;

    @Column(name = "current_billing_period_end", columnDefinition = "DATETIME(6)")
    private LocalDateTime currentBillingPeriodEnd;

    // Metadata
    @Column(name = "last_webhook_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastWebhookAt;

    @Column(name = "last_sync_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastSyncAt;

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
}
