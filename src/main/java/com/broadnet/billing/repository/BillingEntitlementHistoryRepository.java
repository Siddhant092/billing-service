package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingEntitlementHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_entitlement_history table.
 * Append-only audit log — no updates or deletes.
 *
 * CHANGES FROM ORIGINAL:
 * - findByCompanyIdAndChangeType: changeType param changed to BillingEntitlementHistory.ChangeType enum
 * - findPlanChangesByCompanyId: JPQL string literal 'plan_change' → enum constant
 * - findAddonChangesByCompanyId: JPQL string literals for addon types → enum constants
 * - findByTriggeredBy: triggeredBy param changed to BillingEntitlementHistory.TriggeredBy enum
 * - findLatestByCompanyId: fixed to return Optional correctly — added LIMIT 1 via JPQL
 * - All other methods confirmed correct.
 */
@Repository
public interface BillingEntitlementHistoryRepository extends JpaRepository<BillingEntitlementHistory, Long> {

    /**
     * Find all entitlement changes for a company — newest-first.
     * Used by admin to audit a company's entitlement history.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find entitlement changes by type for a company.
     * FIXED: changeType param type changed to BillingEntitlementHistory.ChangeType enum.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType = :changeType ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByCompanyIdAndChangeType(
            @Param("companyId") Long companyId,
            @Param("changeType") BillingEntitlementHistory.ChangeType changeType
    );

    /**
     * Find history record by Stripe event ID.
     * Used to check if a webhook event has already been logged (idempotency).
     */
    Optional<BillingEntitlementHistory> findByStripeEventId(String stripeEventId);

    /**
     * Find recent entitlement changes for a company since a given date.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.createdAt >= :since ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findRecentChangesByCompanyId(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since
    );

    /**
     * Find all plan change records for a company.
     * FIXED: string literal 'plan_change' → enum constant reference.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType = com.broadnet.billing.entity.BillingEntitlementHistory$ChangeType.plan_change " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findPlanChangesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find the most recent entitlement change for a company.
     * FIXED: added ORDER BY + LIMIT equivalent (Spring Data JPQL first result).
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "ORDER BY beh.createdAt DESC LIMIT 1")
    Optional<BillingEntitlementHistory> findLatestByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find entitlement changes across all companies in a date range (platform-wide audit).
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh " +
            "WHERE beh.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all addon-related changes for a company (added, removed, upgraded, downgraded).
     * FIXED: string literals → enum constants.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType IN (" +
            "com.broadnet.billing.entity.BillingEntitlementHistory$ChangeType.addon_added, " +
            "com.broadnet.billing.entity.BillingEntitlementHistory$ChangeType.addon_removed, " +
            "com.broadnet.billing.entity.BillingEntitlementHistory$ChangeType.addon_upgraded, " +
            "com.broadnet.billing.entity.BillingEntitlementHistory$ChangeType.addon_downgraded) " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findAddonChangesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count changes grouped by change type (analytics).
     * Returns List<Object[]> with [ChangeType, Long count].
     */
    @Query("SELECT beh.changeType, COUNT(beh) FROM BillingEntitlementHistory beh GROUP BY beh.changeType")
    List<Object[]> countByChangeType();

    /**
     * Find changes triggered by a specific source (webhook/admin/api).
     * FIXED: triggeredBy param type changed to BillingEntitlementHistory.TriggeredBy enum.
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.triggeredBy = :triggeredBy " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByTriggeredBy(
            @Param("triggeredBy") BillingEntitlementHistory.TriggeredBy triggeredBy
    );

    /**
     * Find entitlement history for a company — paginated, newest-first.
     * Required by BillingDashboardServiceImpl.getEntitlementHistory().
     */
    Page<BillingEntitlementHistory> findByCompanyIdOrderByCreatedAtDesc(
            Long companyId,
            Pageable pageable
    );
}