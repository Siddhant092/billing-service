package com.broadnet.billing.repository;

import com.broadnet.billing.entity.CompanyBilling;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyBillingRepository extends JpaRepository<CompanyBilling, Long> {

    /**
     * Find billing by company ID
     */
    Optional<CompanyBilling> findByCompanyId(Long companyId);

    /**
     * Find billing by company ID with pessimistic write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.companyId = :companyId")
    Optional<CompanyBilling> findByCompanyIdWithLock(@Param("companyId") Long companyId);

    /**
     * Find billing by Stripe customer ID
     */
    Optional<CompanyBilling> findByStripeCustomerId(String stripeCustomerId);

    /**
     * Find billing by Stripe subscription ID
     */
    Optional<CompanyBilling> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Find all companies with active subscriptions
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.subscriptionStatus = 'active'")
    List<CompanyBilling> findAllActiveSubscriptions();

    /**
     * Find all companies with past_due subscriptions
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.subscriptionStatus = 'past_due'")
    List<CompanyBilling> findAllPastDueSubscriptions();

    /**
     * Find companies with subscriptions ending soon
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.periodEnd BETWEEN :startDate AND :endDate " +
            "AND cb.subscriptionStatus = 'active'")
    List<CompanyBilling> findSubscriptionsEndingSoon(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find companies with cancel_at_period_end = true
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.cancelAtPeriodEnd = true " +
            "AND cb.subscriptionStatus IN ('active', 'trialing')")
    List<CompanyBilling> findCancelingSubscriptions();

    /**
     * Find companies with pending plan changes
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.pendingPlanId IS NOT NULL " +
            "AND cb.pendingEffectiveDate <= :currentDate")
    List<CompanyBilling> findPendingPlanChanges(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find companies by billing mode
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.billingMode = :mode")
    List<CompanyBilling> findByBillingMode(@Param("mode") String mode);

    /**
     * Find enterprise customers
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.billingMode = 'postpaid'")
    List<CompanyBilling> findEnterpriseCustomers();

    /**
     * Find companies approaching answer limit
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.effectiveAnswersLimit > 0 " +
            "AND (CAST(cb.answersUsedInPeriod AS double) / cb.effectiveAnswersLimit) >= :threshold " +
            "AND cb.subscriptionStatus = 'active'")
    List<CompanyBilling> findApproachingAnswerLimit(@Param("threshold") Double threshold);

    /**
     * Find companies approaching KB pages limit
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.effectiveKbPagesLimit > 0 " +
            "AND (CAST(cb.kbPagesTotal AS double) / cb.effectiveKbPagesLimit) >= :threshold " +
            "AND cb.subscriptionStatus = 'active'")
    List<CompanyBilling> findApproachingKbPagesLimit(@Param("threshold") Double threshold);

    /**
     * Increment answer usage with optimistic lock
     */
    @Modifying
    @Query("UPDATE CompanyBilling cb SET cb.answersUsedInPeriod = cb.answersUsedInPeriod + :count, " +
            "cb.version = cb.version + 1, cb.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE cb.companyId = :companyId AND cb.version = :version")
    int incrementAnswerUsage(
            @Param("companyId") Long companyId,
            @Param("count") Integer count,
            @Param("version") Integer version
    );

    /**
     * Reset period usage counters
     */
    @Modifying
    @Query("UPDATE CompanyBilling cb SET cb.answersUsedInPeriod = 0, " +
            "cb.answersPeriodStart = :periodStart, " +
            "cb.version = cb.version + 1, cb.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE cb.companyId = :companyId")
    void resetPeriodUsage(
            @Param("companyId") Long companyId,
            @Param("periodStart") LocalDateTime periodStart
    );

    /**
     * Find companies by active plan code
     */
    List<CompanyBilling> findByActivePlanCode(String planCode);

    /**
     * Check if company has active billing
     */
    @Query("SELECT CASE WHEN COUNT(cb) > 0 THEN true ELSE false END FROM CompanyBilling cb " +
            "WHERE cb.companyId = :companyId AND cb.subscriptionStatus IN ('active', 'trialing')")
    boolean hasActiveSubscription(@Param("companyId") Long companyId);

    /**
     * Find companies needing usage reset (period_end passed)
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.periodEnd < :currentDate " +
            "AND cb.subscriptionStatus = 'active'")
    List<CompanyBilling> findCompaniesNeedingUsageReset(@Param("currentDate") LocalDateTime currentDate);

    // ⚠️ ADDED MISSING METHODS - Required by BillingScheduledJobsServiceImpl

    /**
     * Find companies by billing interval and period end before date
     * Required by BillingScheduledJobsServiceImpl.resetAnnualAnswerUsage()
     * Required by BillingScheduledJobsServiceImpl.resetMonthlyUsageCounters()
     */
    List<CompanyBilling> findByBillingIntervalAndPeriodEndBefore(
            String billingInterval,
            LocalDateTime date
    );

    /**
     * Find companies with payment failures before date and not yet restricted
     * Required by BillingScheduledJobsServiceImpl.applyPaymentFailureRestrictions()
     */
    List<CompanyBilling> findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNull(
            LocalDateTime date
    );

    /**
     * Find companies with pending plan changes due before date
     * Required by BillingScheduledJobsServiceImpl.applyPendingPlanChanges()
     * FIX: entity field is "pendingEffectiveDate" not "pendingPlanEffectiveDate"
     */
    List<CompanyBilling> findByPendingPlanCodeIsNotNullAndPendingEffectiveDateBefore(
            LocalDateTime date
    );

    /**
     * Find all companies with subscription IDs (for sync)
     * Required by BillingScheduledJobsServiceImpl.syncSubscriptionStatesFromStripe()
     */
    List<CompanyBilling> findByStripeSubscriptionIdIsNotNull();
}