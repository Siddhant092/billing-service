package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity for billing_enterprise_usage_billing table.
 * Tracks usage for a billing period for enterprise/postpaid customers.
 * One record per (company, billing_period_start, billing_period_end).
 *
 * Architecture Plan: UI Extension Tables §5
 *
 * CHANGES FROM ORIGINAL:
 * - REMOVED: enterprisePricingId field (not in architecture plan schema for this table)
 * - billingStatus changed from plain String to @Enumerated BillingStatus (matches schema ENUM)
 * - All monetary/usage fields changed from Long to Integer (schema uses INTEGER, not BIGINT)
 * - ADDED: invoiceId field (was missing — schema has invoice_id BIGINT FK to billing_invoices)
 * - ADDED: calculationNotes field (was missing — schema has calculation_notes TEXT)
 * - ADDED: metadata JSON field (was missing — schema has metadata JSON)
 * - REMOVED: @Version (not in architecture plan schema for this table)
 * - Added unique constraint uk_company_period
 * - Updated @Table indexes to match schema
 */
@Entity
@Table(
        name = "billing_enterprise_usage_billing",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_period",
                        columnNames = {"company_id", "billing_period_start", "billing_period_end"}
                )
        },
        indexes = {
                @Index(name = "idx_billing_status",  columnList = "billing_status, billing_period_end"),
                @Index(name = "idx_invoice",         columnList = "invoice_id"),
                @Index(name = "idx_stripe_invoice",  columnList = "stripe_invoice_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEnterpriseUsageBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id) ON DELETE CASCADE.
     * Kept as bare Long until Company entity is confirmed.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "billing_period_start", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime billingPeriodEnd;

    /**
     * FIXED: Was plain String. Schema defines ENUM('pending','calculated','invoiced','paid').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_status", nullable = false, length = 20)
    @Builder.Default
    private BillingStatus billingStatus = BillingStatus.pending;

    // -------------------------------------------------------------------------
    // Usage Metrics (FIXED: Long → Integer to match schema INTEGER type)
    // -------------------------------------------------------------------------

    @Column(name = "answers_used", nullable = false)
    @Builder.Default
    private Integer answersUsed = 0;

    @Column(name = "kb_pages_used", nullable = false)
    @Builder.Default
    private Integer kbPagesUsed = 0;

    @Column(name = "agents_used", nullable = false)
    @Builder.Default
    private Integer agentsUsed = 0;

    @Column(name = "users_used", nullable = false)
    @Builder.Default
    private Integer usersUsed = 0;

    // -------------------------------------------------------------------------
    // Pricing snapshot at time of billing (copied from billing_enterprise_pricing)
    // -------------------------------------------------------------------------

    @Column(name = "answers_rate_cents", nullable = false)
    private Integer answersRateCents;

    @Column(name = "kb_pages_rate_cents", nullable = false)
    private Integer kbPagesRateCents;

    @Column(name = "agents_rate_cents", nullable = false)
    private Integer agentsRateCents;

    @Column(name = "users_rate_cents", nullable = false)
    private Integer usersRateCents;

    // -------------------------------------------------------------------------
    // Calculated amounts (FIXED: Long → Integer to match schema INTEGER type)
    // -------------------------------------------------------------------------

    @Column(name = "answers_amount_cents", nullable = false)
    @Builder.Default
    private Integer answersAmountCents = 0;

    @Column(name = "kb_pages_amount_cents", nullable = false)
    @Builder.Default
    private Integer kbPagesAmountCents = 0;

    @Column(name = "agents_amount_cents", nullable = false)
    @Builder.Default
    private Integer agentsAmountCents = 0;

    @Column(name = "users_amount_cents", nullable = false)
    @Builder.Default
    private Integer usersAmountCents = 0;

    @Column(name = "subtotal_cents", nullable = false)
    @Builder.Default
    private Integer subtotalCents = 0;

    @Column(name = "tax_amount_cents", nullable = false)
    @Builder.Default
    private Integer taxAmountCents = 0;

    @Column(name = "total_cents", nullable = false)
    @Builder.Default
    private Integer totalCents = 0;

    // -------------------------------------------------------------------------
    // Invoice Reference
    // -------------------------------------------------------------------------

    @Column(name = "stripe_invoice_id", length = 255)
    private String stripeInvoiceId;

    /**
     * ADDED: Was missing. Schema: invoice_id BIGINT NULL FK to billing_invoices ON DELETE SET NULL.
     */
    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(name = "invoiced_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime invoicedAt;

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    /**
     * ADDED: Was missing. Schema: calculation_notes TEXT NULL.
     */
    @Column(name = "calculation_notes", columnDefinition = "TEXT")
    private String calculationNotes;

    /**
     * ADDED: Was missing. Schema: metadata JSON NULL.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM('pending','calculated','invoiced','paid')
    // -------------------------------------------------------------------------

    public enum BillingStatus {
        pending, calculated, invoiced, paid
    }
}