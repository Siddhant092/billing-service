package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_stripe_prices table.
 * Maps Stripe Price IDs to internal plans OR add-ons (mutually exclusive).
 *
 * Architecture Plan: Core Tables §5
 *
 * CHANGES FROM ORIGINAL:
 * - planId (Long) replaced with proper @ManyToOne BillingPlan (nullable — NULL if addon)
 * - addonId (Long) replaced with proper @ManyToOne BillingAddon (nullable — NULL if plan)
 * - billingInterval changed from plain String to @Enumerated BillingInterval
 * - Added @Table indexes to match schema idx_plan, idx_addon
 * - Added @Table unique constraints to match schema uk_stripe_price_id, uk_lookup_key
 *
 * IMPORTANT — mutual exclusivity rule (from schema CHECK constraint):
 *   Either plan_id IS NULL (addon price) OR addon_id IS NULL (plan price), never both null or both set.
 *   This is enforced at DB level via CHECK constraint. At application level, validate in service layer.
 *
 * FK behaviours from schema:
 *   fk_stripe_price_plan  → billing_plans(id)  ON DELETE SET NULL
 *   fk_stripe_price_addon → billing_addons(id) ON DELETE SET NULL
 */
@Entity
@Table(
        name = "billing_stripe_prices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stripe_price_id", columnNames = "stripe_price_id"),
                @UniqueConstraint(name = "uk_lookup_key",      columnNames = "lookup_key")
        },
        indexes = {
                @Index(name = "idx_plan",  columnList = "plan_id"),
                @Index(name = "idx_addon", columnList = "addon_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingStripePrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_price_id", nullable = false, unique = true)
    private String stripePriceId;

    /**
     * lookup_key format: "plan_starter_monthly", "addon_answers_boost_m_monthly"
     */
    @Column(name = "lookup_key", nullable = false, unique = true, length = 100)
    private String lookupKey;

    /**
     * FIXED: Was a bare Long. Nullable because this is NULL when it's an addon price.
     * ON DELETE SET NULL — plan deletion nullifies this FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_stripe_price_plan"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingPlan plan;

    /**
     * FIXED: Was a bare Long. Nullable because this is NULL when it's a plan price.
     * ON DELETE SET NULL — addon deletion nullifies this FK.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "addon_id", nullable = true,
            foreignKey = @ForeignKey(name = "fk_stripe_price_addon"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingAddon addon;

    /**
     * FIXED: Was plain String. Schema defines ENUM('month','year').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    private BillingInterval billingInterval;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "usd";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM('month','year')
    // -------------------------------------------------------------------------

    public enum BillingInterval {
        month, year
    }
}