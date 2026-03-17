package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_webhook_events table
 * Stores Stripe webhook events for idempotency and debugging
 */
@Entity
@Table(name = "billing_webhook_events")
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

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;  // ✅ FIXED: Changed from Map to String (stores JSON as text)

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

    // ✅ NEW FIELD: Added for tracking processing status
    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "pending";  // pending, processing, completed, failed

    // ✅ NEW FIELD: Added for retry tracking
    @Column(name = "last_retry_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastRetryAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}