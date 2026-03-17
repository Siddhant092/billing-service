package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPlansRepository extends JpaRepository<BillingPlan, Long> {

    /**
     * Find plan by plan code
     */
    Optional<BillingPlan> findByPlanCode(String planCode);

    /**
     * Find all active plans
     */
    List<BillingPlan> findByIsActiveTrue();

    /**
     * Find all enterprise plans
     */
    List<BillingPlan> findByIsEnterpriseTrue();

    /**
     * Find active non-enterprise plans
     */
    List<BillingPlan> findByIsActiveTrueAndIsEnterpriseFalse();

    /**
     * Check if plan code exists
     */
    boolean existsByPlanCode(String planCode);

    /**
     * Find plan by plan code and active status
     */
    Optional<BillingPlan> findByPlanCodeAndIsActiveTrue(String planCode);

    /**
     * Find all plans ordered by creation date
     */
    List<BillingPlan> findAllByOrderByCreatedAtDesc();

    /**
     * Find plans by support tier
     */
    @Query("SELECT bp FROM BillingPlan bp WHERE bp.supportTier = :tier AND bp.isActive = true")
    List<BillingPlan> findBySupportTier(@Param("tier") String tier);
}