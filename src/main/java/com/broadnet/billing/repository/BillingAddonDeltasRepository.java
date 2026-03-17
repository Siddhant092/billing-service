package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingAddonDelta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAddonDeltasRepository extends JpaRepository<BillingAddonDelta, Long> {

    /**
     * Find active deltas for a specific addon
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addonId = :addonId " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonId(
            @Param("addonId") Long addonId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find specific delta type for an addon
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addonId = :addonId " +
            "AND bad.deltaType = :deltaType " +
            "AND bad.billingInterval = :interval " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    Optional<BillingAddonDelta> findActiveDelta(
            @Param("addonId") Long addonId,
            @Param("deltaType") String deltaType,
            @Param("interval") String interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find all deltas for an addon
     */
    List<BillingAddonDelta> findByAddonId(Long addonId);

    /**
     * Find deltas by addon, type, and interval
     */
    Optional<BillingAddonDelta> findByAddonIdAndDeltaTypeAndBillingInterval(
            Long addonId,
            String deltaType,
            String billingInterval
    );

    /**
     * Find all active deltas for addon by billing interval
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addonId = :addonId " +
            "AND bad.billingInterval = :interval " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonIdAndInterval(
            @Param("addonId") Long addonId,
            @Param("interval") String interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find deltas for multiple addons
     */
    @Query("SELECT bad FROM BillingAddonDelta bad WHERE bad.addonId IN :addonIds " +
            "AND bad.isActive = true " +
            "AND bad.effectiveFrom <= :currentDate " +
            "AND (bad.effectiveTo IS NULL OR bad.effectiveTo > :currentDate)")
    List<BillingAddonDelta> findActiveDeltasByAddonIds(
            @Param("addonIds") List<Long> addonIds,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Check if delta exists for addon and type
     */
    boolean existsByAddonIdAndDeltaTypeAndBillingIntervalAndIsActiveTrue(
            Long addonId,
            String deltaType,
            String billingInterval
    );
}