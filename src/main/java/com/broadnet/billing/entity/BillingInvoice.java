package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity for billing_invoices table.
 * Local copy of Stripe invoices — populated by webhook handlers.
 *
 * Architecture Plan: UI Extension Tables §2
 *
 * CHANGES FROM ORIGINAL:
 * - status changed from plain String to @Enumerated InvoiceStatus (matches schema ENUM)
 * - RENAMED: tax → taxAmount (schema column is tax_amount, original had wrong field name 'tax'
 *   and wrong column mapping)
 * - ADDED: lineItems JSON field (was missing — schema has line_items JSON NOT NULL)
 * - ADDED: metadata JSON field (was missing — schema has metadata JSON)
 * - REMOVED: description field (not in architecture plan schema)
 * - period_start and period_end made nullable (schema allows NULL — "Billing period start/end")
 * - invoice_number made nullable (schema: invoice_number VARCHAR(100) NULL)
 * - Updated @Table indexes and unique constraints to match schema
 * - All monetary fields kept as Integer to match schema (cents, INTEGER type)
 */
@Entity
@Table(
        name = "billing_invoices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stripe_invoice", columnNames = "stripe_invoice_id")
        },
        indexes = {
                @Index(name = "idx_company_status", columnList = "company_id, status, invoice_date"),
                @Index(name = "idx_invoice_date",   columnList = "invoice_date")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FK → company(id) ON DELETE CASCADE.
     * Kept as bare Long until Company entity is confirmed.
     */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "stripe_invoice_id", nullable = false, unique = true, length = 255)
    private String stripeInvoiceId;

    /**
     * FIXED: Schema defines this as nullable: invoice_number VARCHAR(100) NULL
     */
    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    /**
     * FIXED: Was plain String. Schema defines ENUM('draft','open','paid','void','uncollectible').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InvoiceStatus status;

    /** Amount in cents. */
    @Column(name = "amount_due", nullable = false)
    private Integer amountDue;

    @Column(name = "amount_paid", nullable = false)
    @Builder.Default
    private Integer amountPaid = 0;

    @Column(name = "subtotal", nullable = false)
    private Integer subtotal;

    /**
     * FIXED: Original had wrong field name 'tax' mapping to wrong column.
     * Schema column: tax_amount INTEGER NOT NULL DEFAULT 0
     */
    @Column(name = "tax_amount", nullable = false)
    @Builder.Default
    private Integer taxAmount = 0;

    @Column(name = "total", nullable = false)
    private Integer total;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "usd";

    @Column(name = "invoice_date", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime invoiceDate;

    @Column(name = "due_date", columnDefinition = "DATETIME(6)")
    private LocalDateTime dueDate;

    @Column(name = "paid_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime paidAt;

    /**
     * FIXED: Both made nullable to match schema (NULL is valid — "Billing period start/end").
     */
    @Column(name = "period_start", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodStart;

    @Column(name = "period_end", columnDefinition = "DATETIME(6)")
    private LocalDateTime periodEnd;

    @Column(name = "hosted_invoice_url", length = 500)
    private String hostedInvoiceUrl;

    @Column(name = "invoice_pdf_url", length = 500)
    private String invoicePdfUrl;

    /**
     * ADDED: Was missing from original. Schema: line_items JSON NOT NULL.
     * Stores Stripe invoice line items array.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "JSON")
    private String lineItems;

    /**
     * ADDED: Was missing from original. Schema: metadata JSON NULL.
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
    // Enum matching schema ENUM('draft','open','paid','void','uncollectible')
    // -------------------------------------------------------------------------

    public enum InvoiceStatus {
        draft, open, paid, void_, uncollectible;

        // Override to handle 'void' which is a Java keyword
        @Override
        public String toString() {
            return this == void_ ? "void" : this.name();
        }
    }
}