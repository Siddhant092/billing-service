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
 * Entity for billing_addons table
 * Represents add-on product definitions
 */
@Entity
@Table(name = "billing_addons")
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

    @Column(name = "category", nullable = false, length = 20)
    private String category; // answers, kb

    @Column(name = "tier", nullable = false, length = 20)
    private String tier; // small, medium, large

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
}
