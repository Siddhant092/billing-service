package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingPlanLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPlanLimitsRepository extends JpaRepository<BillingPlanLimit, Long> {

    /**
     * Find active limits for a specific plan
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.planId = :planId " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    List<BillingPlanLimit> findActiveLimitsByPlanId(
            @Param("planId") Long planId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find specific limit type for a plan with pessimistic lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.planId = :planId " +
            "AND bpl.limitType = :limitType " +
            "AND bpl.billingInterval = :interval " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    Optional<BillingPlanLimit> findLimitWithLock(
            @Param("planId") Long planId,
            @Param("limitType") String limitType,
            @Param("interval") String interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find limit by plan, type, and interval
     */
    Optional<BillingPlanLimit> findByPlanIdAndLimitTypeAndBillingInterval(
            Long planId,
            String limitType,
            String billingInterval
    );

    /**
     * Find all limits for a plan
     */
    List<BillingPlanLimit> findByPlanId(Long planId);

    /**
     * Find all active limits for a plan by billing interval
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.planId = :planId " +
            "AND bpl.billingInterval = :interval " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    List<BillingPlanLimit> findActiveLimitsByPlanIdAndInterval(
            @Param("planId") Long planId,
            @Param("interval") String interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Check if a limit exists for plan and type
     */
    boolean existsByPlanIdAndLimitTypeAndBillingIntervalAndIsActiveTrue(
            Long planId,
            String limitType,
            String billingInterval
    );

    /**
     * Find limits that need to be archived (past effective_to date)
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.isActive = true " +
            "AND bpl.effectiveTo IS NOT NULL " +
            "AND bpl.effectiveTo <= :currentDate")
    List<BillingPlanLimit> findLimitsToArchive(@Param("currentDate") LocalDateTime currentDate);

    // ⚠️ ADDED MISSING METHOD - Required by PlanManagementServiceImpl

    /**
     * Find limits by plan ID and active status
     * Required by PlanManagementServiceImpl.convertToPlanDto()
     */
    List<BillingPlanLimit> findByPlanIdAndIsActiveTrue(Long planId);
}