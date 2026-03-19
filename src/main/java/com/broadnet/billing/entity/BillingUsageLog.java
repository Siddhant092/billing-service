package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for billing_usage_logs table.
 * Append-only audit trail of every usage event (answers, KB pages, agents, users).
 *
 * Architecture Plan: Core Tables §7
 *
 * CHANGES FROM ORIGINAL:
 * - usageType changed from plain String to @Enumerated UsageType (matches schema ENUM)
 * - Added @Table indexes to match schema idx_company_type_created, idx_created_at
 * - This is an INSERT-only table — no @UpdateTimestamp needed (correct in original, confirmed)
 */
@Entity
@Table(
        name = "billing_usage_logs",
        indexes = {
                @Index(name = "idx_company_type_created", columnList = "company_id, usage_type, created_at"),
                @Index(name = "idx_created_at",           columnList = "created_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingUsageLog {

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
     * FIXED: Was plain String.
     * Schema ENUM: answer, kb_page_added, kb_page_updated, agent_created, user_created
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 30)
    private UsageType usageType;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 1;

    @Column(name = "before_count")
    private Integer beforeCount;

    @Column(name = "after_count")
    private Integer afterCount;

    @Column(name = "was_blocked")
    private Boolean wasBlocked;

    @Column(name = "block_reason", length = 255)
    private String blockReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    // -------------------------------------------------------------------------
    // Enum matching schema ENUM definition exactly
    // -------------------------------------------------------------------------

    public enum UsageType {
        answer, kb_page_added, kb_page_updated, agent_created, user_created
    }
}