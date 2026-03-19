package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingPlan;
import com.broadnet.billing.entity.BillingPlanLimit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_plan_limits table.
 *
 * CHANGES FROM ORIGINAL:
 * - All queries referencing bpl.planId changed to bpl.plan.id
 *   (entity field is now @ManyToOne BillingPlan plan — bare planId no longer exists)
 * - findByPlanId → findByPlan_Id (Spring Data derived query for nested FK)
 * - findByPlanIdAndIsActiveTrue → findByPlan_IdAndIsActiveTrue
 * - limitType and billingInterval method params changed to enum types:
 *   BillingPlanLimit.LimitType and BillingPlanLimit.BillingInterval
 * - findByPlanIdAndLimitTypeAndBillingInterval → uses plan.id + enum params
 * - existsByPlanIdAndLimitTypeAndBillingIntervalAndIsActiveTrue → enum params
 * - ADDED: findActiveLimitsByPlan (takes BillingPlan object — for use when plan entity is in memory)
 */
@Repository
public interface BillingPlanLimitsRepository extends JpaRepository<BillingPlanLimit, Long> {

    /**
     * Find all active, currently-effective limits for a plan.
     * This is the primary query used by EntitlementService to compute effective limits.
     * Architecture Plan: "only limits with is_active=TRUE and effective_from<=NOW() and effective_to IS NULL or >NOW()"
     *
     * FIXED: bpl.planId → bpl.plan.id
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.plan.id = :planId " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    List<BillingPlanLimit> findActiveLimitsByPlanId(
            @Param("planId") Long planId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Overload — accepts a BillingPlan object directly (avoids extra getId() call in service layer).
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.plan = :plan " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    List<BillingPlanLimit> findActiveLimitsByPlan(
            @Param("plan") BillingPlan plan,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find specific limit type for a plan — with pessimistic write lock.
     * Used by the plan-limit update flow (admin API) per architecture concurrency section.
     *
     * FIXED: bpl.planId → bpl.plan.id; param types → enums
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.plan.id = :planId " +
            "AND bpl.limitType = :limitType " +
            "AND bpl.billingInterval = :interval " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    Optional<BillingPlanLimit> findLimitWithLock(
            @Param("planId") Long planId,
            @Param("limitType") BillingPlanLimit.LimitType limitType,
            @Param("interval") BillingPlanLimit.BillingInterval interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find a specific limit by plan, type, and interval (no time filter — for admin queries).
     * FIXED: plan.id FK navigation + enum types
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.plan.id = :planId " +
            "AND bpl.limitType = :limitType " +
            "AND bpl.billingInterval = :billingInterval")
    Optional<BillingPlanLimit> findByPlanIdAndLimitTypeAndBillingInterval(
            @Param("planId") Long planId,
            @Param("limitType") BillingPlanLimit.LimitType limitType,
            @Param("billingInterval") BillingPlanLimit.BillingInterval billingInterval
    );

    /**
     * Find all limits for a plan (all versions, active and inactive — for admin audit).
     * FIXED: Spring Data derived query uses plan.id via underscore notation.
     */
    List<BillingPlanLimit> findByPlan_Id(Long planId);

    /**
     * Find active limits for a plan by billing interval.
     * FIXED: bpl.planId → bpl.plan.id; interval param → enum
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.plan.id = :planId " +
            "AND bpl.billingInterval = :interval " +
            "AND bpl.isActive = true " +
            "AND bpl.effectiveFrom <= :currentDate " +
            "AND (bpl.effectiveTo IS NULL OR bpl.effectiveTo > :currentDate)")
    List<BillingPlanLimit> findActiveLimitsByPlanIdAndInterval(
            @Param("planId") Long planId,
            @Param("interval") BillingPlanLimit.BillingInterval interval,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Check if an active limit exists for the given plan, type, and interval.
     * FIXED: Spring Data derived query — uses plan_Id FK path + enum params.
     */
    boolean existsByPlan_IdAndLimitTypeAndBillingIntervalAndIsActiveTrue(
            Long planId,
            BillingPlanLimit.LimitType limitType,
            BillingPlanLimit.BillingInterval billingInterval
    );

    /**
     * Find limits whose effective_to has passed and are still marked active.
     * Used by a cron job to soft-expire old limits.
     */
    @Query("SELECT bpl FROM BillingPlanLimit bpl WHERE bpl.isActive = true " +
            "AND bpl.effectiveTo IS NOT NULL " +
            "AND bpl.effectiveTo <= :currentDate")
    List<BillingPlanLimit> findLimitsToArchive(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find limits by plan ID and active status.
     * Required by PlanManagementServiceImpl.convertToPlanDto().
     * FIXED: Spring Data derived query uses plan.id FK path.
     */
    List<BillingPlanLimit> findByPlan_IdAndIsActiveTrue(Long planId);

    /**
     * Find all active limits for a plan — paginated (for admin listing).
     */
    Page<BillingPlanLimit> findByPlan_IdOrderByEffectiveFromDesc(Long planId, Pageable pageable);
}