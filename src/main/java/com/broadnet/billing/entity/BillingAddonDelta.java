package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for billing_addon_deltas table.
 * Represents the limit increase/delta each add-on provides.
 *
 * Architecture Plan: Core Tables §4
 *
 * CHANGES FROM ORIGINAL:
 * - addonId (Long) replaced with proper @ManyToOne BillingAddon relationship
 * - deltaType changed from plain String to @Enumerated DeltaType (matches schema ENUM)
 * - billingInterval changed from plain String to @Enumerated BillingInterval (matches schema ENUM)
 * - Added @Table uniqueConstraint to match schema uk_addon_delta_type_interval
 * - Added @Table index to match schema idx_addon_active
 * - effectiveFrom @Builder.Default set to now() to match schema DEFAULT CURRENT_TIMESTAMP(6)
 */
@Entity
@Table(
        name = "billing_addon_deltas",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_addon_delta_type_interval",
                        columnNames = {"addon_id", "delta_type", "billing_interval", "effective_from"}
                )
        },
        indexes = {
                @Index(name = "idx_addon_active", columnList = "addon_id, is_active, effective_from")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAddonDelta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * FIXED: Was a bare Long addonId. Must be a proper FK relationship.
     * FK constraint: fk_addon_deltas_addon → billing_addons(id) ON DELETE RESTRICT
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "addon_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_addon_deltas_addon"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BillingAddon addon;

    /**
     * FIXED: Was plain String. Schema defines ENUM('answers_per_period','kb_pages').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delta_type", nullable = false, length = 30)
    private DeltaType deltaType;

    @Column(name = "delta_value", nullable = false)
    private Integer deltaValue;

    /**
     * FIXED: Was plain String. Schema defines ENUM('month','year').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", nullable = false, length = 10)
    @Builder.Default
    private BillingInterval billingInterval = BillingInterval.month;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * FIXED: Defaulting to now() to match schema DEFAULT CURRENT_TIMESTAMP(6).
     */
    @Column(name = "effective_from", columnDefinition = "DATETIME(6)")
    @Builder.Default
    private LocalDateTime effectiveFrom = LocalDateTime.now();

    @Column(name = "effective_to", columnDefinition = "DATETIME(6)")
    private LocalDateTime effectiveTo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    /**
     * Optimistic locking. JPA @Version manages increment automatically.
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    // -------------------------------------------------------------------------
    // Enums matching schema definitions exactly
    // -------------------------------------------------------------------------

    public enum DeltaType {
        answers_per_period, kb_pages
    }

    public enum BillingInterval {
        month, year
    }
}