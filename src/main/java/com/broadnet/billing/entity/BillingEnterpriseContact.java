package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Entity for billing_enterprise_contacts table.
 * Tracks enterprise contact requests and the sales qualification pipeline.
 *
 * Architecture Plan: UI Extension Tables §7
 *
 * CHANGES FROM ORIGINAL:
 * - contactType ENUM field added (was missing — schema has contact_type ENUM(...) NOT NULL)
 * - RENAMED: contactName → name (schema column is 'name')
 * - RENAMED: contactEmail → email (schema column is 'email')
 * - RENAMED: contactPhone → phone (schema column is 'phone')
 * - ADDED: jobTitle field (was missing — schema has job_title VARCHAR(255))
 * - ADDED: companySize ENUM field (was missing — schema has company_size ENUM(...))
 * - ADDED: message field (was missing — schema has message TEXT NOT NULL)
 * - ADDED: estimatedUsage JSON field (replaces the 4 separate estimated_* Long fields)
 * - ADDED: preferredContactMethod ENUM field (was missing — schema has this)
 * - ADDED: preferredContactTime field (was missing — schema has this)
 * - ADDED: assignedAt field (was missing — schema has assigned_at DATETIME(6))
 * - ADDED: firstContactedAt field (was missing — schema has first_contacted_at DATETIME(6))
 * - ADDED: outcome ENUM field (was missing — schema has outcome ENUM(...))
 * - ADDED: metadata JSON field (was missing — schema has metadata JSON)
 * - REMOVED: estimatedAnswers, estimatedKbPages, estimatedAgents, estimatedUsers
 *   (replaced by estimatedUsage JSON — matches schema exactly)
 * - REMOVED: contactedAt, qualifiedAt (replaced by firstContactedAt, closedAt)
 * - status changed from plain String to @Enumerated ContactStatus
 * - Updated @Table indexes to match schema
 */
@Entity
@Table(
        name = "billing_enterprise_contacts",
        indexes = {
                @Index(name = "idx_company",  columnList = "company_id"),
                @Index(name = "idx_status",   columnList = "status, created_at"),
                @Index(name = "idx_assigned", columnList = "assigned_to, status")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingEnterpriseContact {

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
     * ADDED: Was missing. Schema: contact_type ENUM('enterprise_inquiry','pricing_request',
     * 'custom_plan_request','support_request') NOT NULL
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 30)
    private ContactType contactType;

    /**
     * RENAMED: Was contactName. Schema column is 'name'.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * RENAMED: Was contactEmail. Schema column is 'email'.
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * RENAMED: Was contactPhone. Schema column is 'phone' (length 50, not 20).
     */
    @Column(name = "phone", length = 50)
    private String phone;

    /**
     * ADDED: Was missing. Schema: job_title VARCHAR(255) NULL.
     */
    @Column(name = "job_title", length = 255)
    private String jobTitle;

    /**
     * Denormalized from company table for quick reference.
     * Schema: company_name VARCHAR(255) NOT NULL
     */
    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    /**
     * ADDED: Was missing. Schema: company_size ENUM('1-10','11-50','51-200','201-500','501-1000','1000+')
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "company_size", length = 20)
    private CompanySize companySize;

    /**
     * ADDED: Was missing. Schema: message TEXT NOT NULL.
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * FIXED: Replaces 4 separate estimated_* Long fields.
     * Schema: estimated_usage JSON NULL
     * e.g. {"answers": 100000, "kb_pages": 5000, "agents": 20, "users": 100}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "estimated_usage", columnDefinition = "JSON")
    private String estimatedUsage;

    @Column(name = "budget_range", length = 100)
    private String budgetRange;

    /**
     * ADDED: Was missing. Schema: preferred_contact_method ENUM('email','phone','video_call') NOT NULL DEFAULT 'email'
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_contact_method", nullable = false, length = 20)
    @Builder.Default
    private PreferredContactMethod preferredContactMethod = PreferredContactMethod.email;

    /**
     * ADDED: Was missing. Schema: preferred_contact_time VARCHAR(100) NULL.
     */
    @Column(name = "preferred_contact_time", length = 100)
    private String preferredContactTime;

    /**
     * FIXED: Was plain String. Schema defines full ENUM.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ContactStatus status = ContactStatus.pending;

    @Column(name = "assigned_to")
    private Long assignedTo;

    /**
     * ADDED: Was missing. Schema: assigned_at DATETIME(6) NULL.
     */
    @Column(name = "assigned_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime assignedAt;

    /**
     * ADDED: Was missing. Schema: first_contacted_at DATETIME(6) NULL.
     */
    @Column(name = "first_contacted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime firstContactedAt;

    @Column(name = "closed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime closedAt;

    /**
     * ADDED: Was missing. Schema: outcome ENUM('signed','declined','no_response','not_qualified') NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private Outcome outcome;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
    // Enums — all matching schema ENUM definitions exactly
    // -------------------------------------------------------------------------

    public enum ContactType {
        enterprise_inquiry, pricing_request, custom_plan_request, support_request
    }

    public enum CompanySize {
        // Note: These values contain hyphens/+ so we use @Column(name) mapping via EnumType.STRING
        // MySQL stores them as-is from toString()
        SIZE_1_10("1-10"),
        SIZE_11_50("11-50"),
        SIZE_51_200("51-200"),
        SIZE_201_500("201-500"),
        SIZE_501_1000("501-1000"),
        SIZE_1000_PLUS("1000+");

        private final String value;
        CompanySize(String value) { this.value = value; }

        @Override
        public String toString() { return value; }

        @jakarta.persistence.Converter(autoApply = false)
        public static class CompanySizeConverter
                implements jakarta.persistence.AttributeConverter<CompanySize, String> {
            @Override
            public String convertToDatabaseColumn(CompanySize attr) {
                return attr == null ? null : attr.value;
            }
            @Override
            public CompanySize convertToEntityAttribute(String dbVal) {
                if (dbVal == null) return null;
                for (CompanySize s : values()) {
                    if (s.value.equals(dbVal)) return s;
                }
                throw new IllegalArgumentException("Unknown company_size: " + dbVal);
            }
        }
    }

    public enum ContactStatus {
        pending, contacted, in_progress, qualified, closed, rejected
    }

    public enum PreferredContactMethod {
        email, phone, video_call
    }

    public enum Outcome {
        signed, declined, no_response, not_qualified
    }
}