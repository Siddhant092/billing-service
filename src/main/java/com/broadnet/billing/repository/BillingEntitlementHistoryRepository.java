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

@Repository
public interface BillingEntitlementHistoryRepository extends JpaRepository<BillingEntitlementHistory, Long> {

    /**
     * Find all entitlement changes for a company
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find entitlement changes by change type
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType = :changeType ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByCompanyIdAndChangeType(
            @Param("companyId") Long companyId,
            @Param("changeType") String changeType
    );

    /**
     * Find entitlement changes by Stripe event ID
     */
    Optional<BillingEntitlementHistory> findByStripeEventId(String stripeEventId);

    /**
     * Find recent entitlement changes (last N days)
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.createdAt >= :since ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findRecentChangesByCompanyId(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since
    );

    /**
     * Find all plan changes for a company
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType = 'plan_change' ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findPlanChangesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find latest entitlement change for a company
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "ORDER BY beh.createdAt DESC")
    Optional<BillingEntitlementHistory> findLatestByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find entitlement changes in date range
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find addon-related changes for a company
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.companyId = :companyId " +
            "AND beh.changeType IN ('addon_added', 'addon_removed', 'addon_upgraded', 'addon_downgraded') " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findAddonChangesByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count changes by change type
     */
    @Query("SELECT beh.changeType, COUNT(beh) FROM BillingEntitlementHistory beh GROUP BY beh.changeType")
    List<Object[]> countByChangeType();

    /**
     * Find changes triggered by specific source
     */
    @Query("SELECT beh FROM BillingEntitlementHistory beh WHERE beh.triggeredBy = :triggeredBy " +
            "ORDER BY beh.createdAt DESC")
    List<BillingEntitlementHistory> findByTriggeredBy(@Param("triggeredBy") String triggeredBy);

    // ⚠️ ADDED MISSING METHOD - Required by BillingDashboardServiceImpl

    /**
     * Find entitlement history for a company with pagination
     * Required by BillingDashboardServiceImpl.getEntitlementHistory()
     */
    Page<BillingEntitlementHistory> findByCompanyIdOrderByCreatedAtDesc(
            Long companyId,
            Pageable pageable
    );
}