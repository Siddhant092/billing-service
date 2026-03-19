package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for billing_notifications table.
 * In-app billing alerts for customers (payment failures, limit warnings, plan changes, etc.).
 *
 * Architecture Plan: UI Extension Tables §1 (section: Database Schema Extensions)
 *
 * CHANGES FROM ORIGINAL:
 * - notificationType changed from plain String to @Enumerated NotificationType (matches schema ENUM exactly)
 * - severity changed from plain String to @Enumerated Severity — original had wrong values
 *   (original had 'critical', schema defines 'error')
 * - ADDED: actionText field (was missing — schema has action_text VARCHAR(100))
 * - ADDED: stripeEventId field (was missing — schema has stripe_event_id VARCHAR(255))
 * - ADDED: metadata JSON field (was missing — schema has metadata JSON)
 * - Updated @Table indexes to match schema exactly
 */
@Entity
@Table(
        name = "billing_notifications",
        indexes = {
                @Index(name = "idx_company_unread", columnList = "company_id, is_read, created_at"),
                @Index(name = "idx_expires",        columnList = "expires_at"),
                @Index(name = "idx_stripe_event",   columnList = "stripe_event_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingNotification {

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
     * FIXED: Was plain String. Schema defines a full ENUM of 15 notification types.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * FIXED: Was plain String with wrong values ('critical' doesn't exist in schema).
     * Schema defines ENUM('info','warning','error','success').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    @Builder.Default
    private Severity severity = Severity.info;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /**
     * ADDED: Was missing from original. Schema: action_text VARCHAR(100).
     * e.g. "Update Payment Method", "View Invoice", "Upgrade Plan"
     */
    @Column(name = "action_text", length = 100)
    private String actionText;

    /**
     * ADDED: Was missing from original. Schema: stripe_event_id VARCHAR(255).
     * Links notification back to the webhook event that created it.
     */
    @Column(name = "stripe_event_id", length = 255)
    private String stripeEventId;

    /**
     * ADDED: Was missing from original. Schema: metadata JSON.
     * Stores context-specific data (invoice amounts, plan codes, etc.).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @Column(name = "expires_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "read_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime readAt;

    // -------------------------------------------------------------------------
    // Enums — must match schema ENUM definitions exactly
    // -------------------------------------------------------------------------

    public enum NotificationType {
        subscription_active,
        subscription_inactive,
        payment_method_expired,
        payment_failed,
        payment_succeeded,
        subscription_canceled,
        subscription_renewed,
        plan_changed,
        addon_added,
        addon_removed,
        limit_warning,
        limit_exceeded,
        invoice_created,
        invoice_paid,
        invoice_failed
    }

    public enum Severity {
        info, warning, error, success
    }
}