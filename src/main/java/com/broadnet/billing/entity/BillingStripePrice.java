package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_stripe_prices table
 * Maps Stripe price IDs to internal plans and add-ons
 */
@Entity
@Table(name = "billing_stripe_prices")
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

    @Column(name = "lookup_key", nullable = false, unique = true, length = 100)
    private String lookupKey;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "addon_id")
    private Long addonId;

    @Column(name = "billing_interval", nullable = false, length = 10)
    private String billingInterval; // month, year

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
}
