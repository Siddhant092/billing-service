package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_plans table.
 *
 * CHANGES FROM ORIGINAL:
 * - findBySupportTier: param type changed from String to BillingPlan.SupportTier enum
 *   (entity field is now @Enumerated — Spring Data derives the correct query automatically)
 */
@Repository
public interface BillingPlansRepository extends JpaRepository<BillingPlan, Long> {

    /** Find plan by plan code. */
    Optional<BillingPlan> findByPlanCode(String planCode);

    /** Find all active plans. */
    List<BillingPlan> findByIsActiveTrue();

    /** Find all enterprise plans. */
    List<BillingPlan> findByIsEnterpriseTrue();

    /** Find active non-enterprise plans (shown in public pricing page). */
    List<BillingPlan> findByIsActiveTrueAndIsEnterpriseFalse();

    /** Check if plan code exists. */
    boolean existsByPlanCode(String planCode);

    /** Find plan by code only if active. */
    Optional<BillingPlan> findByPlanCodeAndIsActiveTrue(String planCode);

    /** Find all plans ordered newest-first. */
    List<BillingPlan> findAllByOrderByCreatedAtDesc();

    /**
     * Find active plans by support tier.
     * FIXED: parameter type is BillingPlan.SupportTier (was String — entity is now @Enumerated).
     */
    @Query("SELECT bp FROM BillingPlan bp WHERE bp.supportTier = :tier AND bp.isActive = true")
    List<BillingPlan> findBySupportTier(@Param("tier") BillingPlan.SupportTier tier);
}