package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingAddon;
import com.broadnet.billing.entity.BillingAddonDelta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_addon_deltas table.
 *
 * CHANGES FROM ORIGINAL:
 * - ALL queries referencing bad.addonId changed to bad.addon.id
 *   (entity field is now @ManyToOne BillingAddon addon — bare addonId no longer exists)
 * - findByAddonId → findByAddon_Id (Spring Data derived query for nested FK)
 * - deltaType and billingInterval params changed to enum types:
 *   BillingAddonDelta.DeltaType and BillingAddonDelta.BillingInterval
 * - existsByAddonIdAndDeltaTypeAndBillingIntervalAndIsActiveTrue → enum params + addon.id path
 * - ADDED: findActiveDeltasByAddon (accepts BillingAddon object — avoids extra getId())
 */
@Repository
public interface BillingAddonDeltasRepository extends JpaRepository<BillingAddonDelta, Long> {

    /**
     * Find all active, currently-effective deltas for a specific addon.
     * Primary query used by EntitlementService when computing effective limits.
     *
     * FIXED: bad.addonId → bad.addon.id
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon.id = :addonId " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonId(
            @Param("addonId") Long addonId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Overload — accepts a BillingAddon object directly.
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon = :addon " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddon(
            @Param("addon") BillingAddon addon,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find a specific delta for an addon by type and interval.
     * FIXED: bad.addonId → bad.addon.id; params → enum types
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon.id = :addonId " +
            "AND bad.deltaType = :deltaType " +
            "AND bad.billingInterval = :interval " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    Optional<BillingAddonDelta> findActiveDelta(
            @Param("addonId") Long addonId,
            @Param("deltaType") BillingAddonDelta.DeltaType deltaType,
            @Param("interval") BillingAddonDelta.BillingInterval interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find all deltas for an addon — all versions (admin/audit view).
     * FIXED: Spring Data derived query uses addon.id via underscore notation.
     */
    List<BillingAddonDelta> findByAddon_Id(Long addonId);

    /**
     * Find a specific delta by addon, type, and interval — no time filter (admin use).
     * FIXED: uses addon.id path + enum params.
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon.id = :addonId " +
            "AND bad.deltaType = :deltaType " +
            "AND bad.billingInterval = :billingInterval")
    Optional<BillingAddonDelta> findByAddonIdAndDeltaTypeAndBillingInterval(
            @Param("addonId") Long addonId,
            @Param("deltaType") BillingAddonDelta.DeltaType deltaType,
            @Param("billingInterval") BillingAddonDelta.BillingInterval billingInterval
    );

    /**
     * Find active deltas for an addon filtered by billing interval.
     * FIXED: bad.addonId → bad.addon.id; interval → enum
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon.id = :addonId " +
            "AND bad.billingInterval = :interval " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonIdAndInterval(
            @Param("addonId") Long addonId,
            @Param("interval") BillingAddonDelta.BillingInterval interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find active deltas for multiple addons in one query (batch lookup).
     * Used by EntitlementService when a company has multiple active addons.
     * FIXED: bad.addonId → bad.addon.id
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addon.id IN :addonIds " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonIds(
            @Param("addonIds") List<Long> addonIds,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Check if an active delta already exists for the given addon, type, and interval.
     * Used for validation before inserting a new delta.
     * FIXED: Spring Data derived query uses addon_Id path + enum params.
     */
    boolean existsByAddon_IdAndDeltaTypeAndBillingIntervalAndIsActiveTrue(
            Long addonId,
            BillingAddonDelta.DeltaType deltaType,
            BillingAddonDelta.BillingInterval billingInterval
    );
}