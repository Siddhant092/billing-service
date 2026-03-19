package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_webhook_events table.
 * Idempotency store for Stripe webhook payloads.
 *
 * Architecture Plan: Core Tables §8
 *
 * CHANGES FROM ORIGINAL:
 * - REMOVED: status field (not in architecture plan schema — was an invention)
 * - REMOVED: lastRetryAt field (not in architecture plan schema — was an invention)
 * - payload column definition corrected: schema says JSON NOT NULL, not TEXT
 *   (kept as String in Java — Hibernate maps String + columnDefinition JSON correctly)
 * - Added @Table indexes to match schema: idx_processed, idx_subscription
 * - Added @Table unique constraint: uk_stripe_event_id
 *
 * NOTE: Do NOT add fields not in the architecture plan without explicit approval.
 * The 'status' and 'lastRetryAt' fields were additions not defined in the schema.
 */
@Entity
@Table(
        name = "billing_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_stripe_event_id", columnNames = "stripe_event_id")
        },
        indexes = {
                @Index(name = "idx_processed",   columnList = "processed, created_at"),
                @Index(name = "idx_subscription", columnList = "stripe_subscription_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false, unique = true)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    /**
     * Full Stripe webhook JSON payload stored as text.
     * Schema: payload JSON NOT NULL
     * Stored as String in Java — enables replay and debugging.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}