package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for billing_usage_logs table
 * Tracks detailed usage audit trail
 */
@Entity
@Table(name = "billing_usage_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "usage_type", nullable = false, length = 30)
    private String usageType; // answer, kb_page_added, kb_page_updated, agent_created, user_created

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 1;

    @Column(name = "before_count")
    private Integer beforeCount;

    @Column(name = "after_count")
    private Integer afterCount;

    @Column(name = "was_blocked")
    private Boolean wasBlocked;

    @Column(name = "block_reason")
    private String blockReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "JSON")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;
}
