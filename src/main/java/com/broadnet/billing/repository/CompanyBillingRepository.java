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

/**
 * Repository for company_billing table.
 * This is the most critical repository — all usage enforcement and webhook processing goes through it.
 *
 * CHANGES FROM ORIGINAL:
 * - findAllActiveSubscriptions: status literal changed from String to enum comparison
 *   (JPQL handles enum comparison natively — use the enum constant not a string)
 * - findAllPastDueSubscriptions: same fix
 * - findCancelingSubscriptions: enum literals for status values
 * - findPendingPlanChanges: cb.pendingPlanId → cb.pendingPlan.id
 *   (entity field is now @ManyToOne BillingPlan pendingPlan)
 * - findByBillingMode: param type changed to CompanyBilling.BillingMode enum
 * - findEnterpriseCustomers: billingMode literal → enum
 * - findCompaniesNeedingUsageReset: status → enum
 * - findByBillingIntervalAndPeriodEndBefore: interval param → CompanyBilling.BillingInterval enum
 * - findByBillingIntervalAndAnswersResetDay: interval param → enum
 * - findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNullAndSubscriptionStatusIn:
 *   statuses param changed to List<CompanyBilling.SubscriptionStatus>
 * - findByActivePlanCode: unchanged (activePlanCode is still a denormalized String column)
 * - incrementAnswerUsage: @Modifying query confirmed correct — uses version for optimistic lock
 * - REMOVED: countByStatus (was referencing bwe.status which doesn't exist on CompanyBilling)
 *
 * NOTE ON OPTIMISTIC LOCKING:
 * incrementAnswerUsage uses manual version check in JPQL. This is intentional — it allows
 * atomic increment+version-check in a single UPDATE, which is faster than the two-step
 * @Version approach for high-frequency usage increments.
 */
@Repository
public interface CompanyBillingRepository extends JpaRepository<CompanyBilling, Long> {

    /** Find billing record by company ID (1:1 relationship). */
    Optional<CompanyBilling> findByCompanyId(Long companyId);

    /**
     * Find billing record by company ID with PESSIMISTIC_WRITE lock.
     * Used by usage enforcement service before incrementing counters.
     * Architecture Plan: "Row-level locking for usage increments"
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.companyId = :companyId")
    Optional<CompanyBilling> findByCompanyIdWithLock(@Param("companyId") Long companyId);

    /** Find by Stripe customer ID — used by webhook handler to resolve company. */
    Optional<CompanyBilling> findByStripeCustomerId(String stripeCustomerId);

    /** Find by Stripe subscription ID — used by webhook handler. */
    Optional<CompanyBilling> findByStripeSubscriptionId(String stripeSubscriptionId);

    /**
     * Find all active subscriptions.
     * FIXED: JPQL enum literal — com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findAllActiveSubscriptions();

    /**
     * Find all past_due subscriptions.
     * FIXED: JPQL enum literal
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.past_due")
    List<CompanyBilling> findAllPastDueSubscriptions();

    /**
     * Find subscriptions ending soon (for renewal notification cron job).
     * Architecture Plan: Cron Jobs — renewal reminders
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.periodEnd BETWEEN :startDate AND :endDate " +
            "AND cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findSubscriptionsEndingSoon(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find subscriptions scheduled to cancel at period end.
     * Architecture Plan: Subscription Lifecycle — "cancel_at_period_end = true"
     * FIXED: JPQL enum literals for active and trialing
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.cancelAtPeriodEnd = true " +
            "AND cb.subscriptionStatus IN (" +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active, " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.trialing)")
    List<CompanyBilling> findCancelingSubscriptions();

    /**
     * Find companies with pending plan changes due now or in the past.
     * Used by BillingScheduledJobsServiceImpl.applyPendingPlanChanges().
     * FIXED: cb.pendingPlanId → cb.pendingPlan.id (entity field is @ManyToOne)
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.pendingPlan.id IS NOT NULL " +
            "AND cb.pendingEffectiveDate <= :currentDate")
    List<CompanyBilling> findPendingPlanChanges(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find companies by billing mode (prepaid vs postpaid).
     * FIXED: param type changed to CompanyBilling.BillingMode enum
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.billingMode = :mode")
    List<CompanyBilling> findByBillingMode(@Param("mode") CompanyBilling.BillingMode mode);

    /**
     * Find all enterprise (postpaid) customers.
     * FIXED: billingMode literal → enum constant
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.billingMode = " +
            "com.broadnet.billing.entity.CompanyBilling$BillingMode.postpaid")
    List<CompanyBilling> findEnterpriseCustomers();

    /**
     * Find companies approaching their answer usage limit.
     * threshold: 0.8 = 80%, 0.9 = 90%, etc.
     * Used by limit_warning notification cron job.
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.effectiveAnswersLimit > 0 " +
            "AND (CAST(cb.answersUsedInPeriod AS double) / cb.effectiveAnswersLimit) >= :threshold " +
            "AND cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findApproachingAnswerLimit(@Param("threshold") Double threshold);

    /**
     * Find companies approaching their KB pages limit.
     * threshold: 0.8 = 80%, 0.9 = 90%, etc.
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.effectiveKbPagesLimit > 0 " +
            "AND (CAST(cb.kbPagesTotal AS double) / cb.effectiveKbPagesLimit) >= :threshold " +
            "AND cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findApproachingKbPagesLimit(@Param("threshold") Double threshold);

    /**
     * Atomically increment answer usage with optimistic locking version check.
     * Architecture Plan: "Atomic increment" — UPDATE with version check in single query.
     * Returns 1 if update succeeded, 0 if version conflict (caller must retry).
     */
    @Modifying
    @Query("UPDATE CompanyBilling cb SET " +
            "cb.answersUsedInPeriod = cb.answersUsedInPeriod + :count, " +
            "cb.version = cb.version + 1, " +
            "cb.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE cb.companyId = :companyId AND cb.version = :version " +
            "AND cb.answersBlocked = false " +
            "AND cb.answersUsedInPeriod < cb.effectiveAnswersLimit")
    int incrementAnswerUsage(
            @Param("companyId") Long companyId,
            @Param("count") Integer count,
            @Param("version") Integer version
    );

    /**
     * Reset period usage counters (answers only — KB/agents/users are not periodic).
     * Called by annual answer reset cron job and monthly reset cron job.
     */
    @Modifying
    @Query("UPDATE CompanyBilling cb SET " +
            "cb.answersUsedInPeriod = 0, " +
            "cb.answersPeriodStart = :periodStart, " +
            "cb.answersBlocked = false, " +
            "cb.version = cb.version + 1, " +
            "cb.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE cb.companyId = :companyId")
    void resetPeriodUsage(
            @Param("companyId") Long companyId,
            @Param("periodStart") LocalDateTime periodStart
    );

    /**
     * Find companies by their active plan code (denormalized field).
     * Used to find all companies on a plan when plan limits change.
     */
    List<CompanyBilling> findByActivePlanCode(String planCode);

    /**
     * Check if a company has an active or trialing subscription.
     */
    @Query("SELECT CASE WHEN COUNT(cb) > 0 THEN true ELSE false END FROM CompanyBilling cb " +
            "WHERE cb.companyId = :companyId AND cb.subscriptionStatus IN (" +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active, " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.trialing)")
    boolean hasActiveSubscription(@Param("companyId") Long companyId);

    /**
     * Find companies whose subscription period has ended but usage hasn't been reset.
     * Used by usage reset cron job.
     * FIXED: status → enum literal
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.periodEnd < :currentDate " +
            "AND cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findCompaniesNeedingUsageReset(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find companies by billing interval whose period end has passed.
     * Used by BillingScheduledJobsServiceImpl.resetMonthlyUsageCounters().
     * FIXED: billingInterval param type → CompanyBilling.BillingInterval enum
     */
    List<CompanyBilling> findByBillingIntervalAndPeriodEndBefore(
            CompanyBilling.BillingInterval billingInterval,
            LocalDateTime date
    );

    /**
     * Find companies with payment failures older than the grace period (7 days) and not yet restricted.
     * Used by ApplyPaymentFailureRestrictions cron job.
     * Architecture Plan: "7 days passed → service restricted"
     */
    List<CompanyBilling> findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNull(
            LocalDateTime date
    );

    /**
     * Find companies with pending plan changes due before the given date.
     * Used by BillingScheduledJobsServiceImpl.applyPendingPlanChanges().
     * FIX: field name is pendingPlanCode (String column) not pendingPlanId (now a relationship).
     */
    List<CompanyBilling> findByPendingPlanCodeIsNotNullAndPendingEffectiveDateBefore(
            LocalDateTime date
    );

    /**
     * Find all companies with a Stripe subscription ID (for periodic sync).
     * Used by BillingScheduledJobsServiceImpl.syncSubscriptionStatesFromStripe().
     */
    List<CompanyBilling> findByStripeSubscriptionIdIsNotNull();

    /**
     * Find annual-plan companies whose answer reset day matches today.
     * Used by the annual answer reset cron job (daily at 00:05 UTC).
     * Architecture Plan: "Daily at 00:05 UTC — Query accounts where today matches reset day"
     * FIXED: interval param → CompanyBilling.BillingInterval enum
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.billingInterval = :interval " +
            "AND cb.answersResetDay = :resetDay " +
            "AND cb.subscriptionStatus = " +
            "com.broadnet.billing.entity.CompanyBilling$SubscriptionStatus.active")
    List<CompanyBilling> findByBillingIntervalAndAnswersResetDay(
            @Param("interval") CompanyBilling.BillingInterval interval,
            @Param("resetDay") Integer resetDay
    );

    /**
     * Find companies with payment failures — filtered by specific subscription statuses.
     * More targeted than findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNull.
     * Architecture Plan: "past_due, unpaid statuses" for restriction trigger.
     * FIXED: statuses param changed to List<CompanyBilling.SubscriptionStatus>
     */
    @Query("SELECT cb FROM CompanyBilling cb WHERE cb.paymentFailureDate < :date " +
            "AND cb.serviceRestrictedAt IS NULL " +
            "AND cb.subscriptionStatus IN :statuses")
    List<CompanyBilling> findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNullAndSubscriptionStatusIn(
            @Param("date") LocalDateTime date,
            @Param("statuses") List<CompanyBilling.SubscriptionStatus> statuses
    );
}