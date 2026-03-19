package com.broadnet.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for billing_addons table.
 * Represents add-on product definitions (answers_boost_s, kb_boost_m, etc.).
 *
 * Architecture Plan: Core Tables §3
 *
 * CHANGES FROM ORIGINAL:
 * - category changed from plain String to @Enumerated AddonCategory (matches schema ENUM('answers','kb'))
 * - tier changed from plain String to @Enumerated AddonTier (matches schema ENUM('small','medium','large'))
 * - Added @OneToMany to BillingAddonDelta (missing entirely)
 * - Added @OneToMany to BillingStripePrice (missing entirely)
 * - Added @Table indexes/unique constraints to match schema
 */
@Entity
@Table(
        name = "billing_addons",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_addon_code", columnNames = "addon_code")
        },
        indexes = {
                @Index(name = "idx_category_active", columnList = "category, is_active")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingAddon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "addon_code", nullable = false, unique = true, length = 50)
    private String addonCode;

    @Column(name = "addon_name", nullable = false)
    private String addonName;

    /**
     * FIXED: Was plain String. Schema defines ENUM('answers','kb').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private AddonCategory category;

    /**
     * FIXED: Was plain String. Schema defines ENUM('small','medium','large').
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    private AddonTier tier;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
    // ADDED: Relationships — were completely missing from original
    // -------------------------------------------------------------------------

    /**
     * An addon has many time-versioned deltas (one per billing_interval).
     * FK: fk_addon_deltas_addon → billing_addons(id) ON DELETE RESTRICT
     */
    @OneToMany(mappedBy = "addon", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BillingAddonDelta> deltas;

    /**
     * An addon maps to one or more Stripe prices.
     */
    @OneToMany(mappedBy = "addon", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<BillingStripePrice> stripePrices;

    // -------------------------------------------------------------------------
    // Enums matching schema definitions exactly
    // -------------------------------------------------------------------------

    public enum AddonCategory {
        answers, kb
    }

    public enum AddonTier {
        small, medium, large
    }
}