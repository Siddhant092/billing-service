package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity for billing_payment_methods table.
 * Tracks Stripe payment methods attached to companies.
 * Populated by payment_method.attached and payment_method.updated webhook events.
 *
 * Architecture Plan: UI Extension Tables §3
 *
 * STATUS: NEW — This entity did not exist at all in the original codebase.
 *
 * Key behaviours:
 * - is_default = TRUE → the active payment method used for billing
 * - is_expired = TRUE → card has passed expiry date
 * - Populated exclusively by Stripe webhooks (never manually)
 *
 * FK: fk_pm_company → company(id) ON DELETE CASCADE
 */
@Entity
@Table(
        name = "billing_payment_methods",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stripe_pm", columnNames = "stripe_payment_method_id")
        },
        indexes = {
                @Index(name = "idx_company_default", columnList = "company_id, is_default"),
                @Index(name = "idx_expired",         columnList = "is_expired, card_exp_year, card_exp_month")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingPaymentMethod {

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
     * Stripe payment method ID (e.g. pm_1234567890).
     */
    @Column(name = "stripe_payment_method_id", nullable = false, unique = true, length = 255)
    private String stripePaymentMethodId;

    /**
     * Schema ENUM('card','bank_account','sepa_debit')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PaymentMethodType type;

    /**
     * Only one payment method per company should have is_default = TRUE.
     * Enforced at service layer when attaching new default.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /** e.g. visa, mastercard, amex */
    @Column(name = "card_brand", length = 50)
    private String cardBrand;

    /** Last 4 digits of card number. */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    /**
     * Expiry month (1-12).
     * Schema: card_exp_month TINYINT — columnDefinition required so Hibernate
     * schema-validation does not expect INTEGER.
     */
    @Column(name = "card_exp_month", columnDefinition = "TINYINT")
    private Integer cardExpMonth;

    /**
     * Expiry year (e.g. 2026).
     * Schema: card_exp_year SMALLINT — columnDefinition required so Hibernate
     * schema-validation does not expect INTEGER.
     */
    @Column(name = "card_exp_year", columnDefinition = "SMALLINT")
    private Integer cardExpYear;

    /**
     * Set to TRUE when card expiry date has passed.
     * Updated by cron job or payment_method.updated webhook.
     */
    @Column(name = "is_expired", nullable = false)
    @Builder.Default
    private Boolean isExpired = false;

    /**
     * Name, email, address from Stripe billing_details object.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "billing_details", columnDefinition = "JSON")
    private String billingDetails;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM('card','bank_account','sepa_debit')
    // -------------------------------------------------------------------------

    public enum PaymentMethodType {
        card, bank_account, sepa_debit
    }
}