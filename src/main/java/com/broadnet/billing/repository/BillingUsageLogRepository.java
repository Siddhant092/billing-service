package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BillingUsageLogRepository extends JpaRepository<BillingUsageLog, Long> {

    /**
     * Find all usage logs for a company
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "ORDER BY bul.createdAt DESC")
    Page<BillingUsageLog> findByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

    /**
     * Find usage logs by company and type
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.usageType = :usageType ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findByCompanyIdAndUsageType(
            @Param("companyId") Long companyId,
            @Param("usageType") String usageType
    );

    /**
     * Find blocked usage attempts for a company
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.wasBlocked = true ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findBlockedUsageByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find usage logs in date range
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count usage by type for a company in a date range
     */
    @Query("SELECT bul.usageType, SUM(bul.usageCount) FROM BillingUsageLog bul " +
            "WHERE bul.companyId = :companyId " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY bul.usageType")
    List<Object[]> countUsageByTypeInDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find recent usage logs (last N hours)
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.createdAt >= :since ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findRecentUsageByCompanyId(
            @Param("companyId") Long companyId,
            @Param("since") LocalDateTime since
    );

    /**
     * Count blocked attempts by company
     */
    @Query("SELECT COUNT(bul) FROM BillingUsageLog bul WHERE bul.companyId = :companyId " +
            "AND bul.wasBlocked = true")
    Long countBlockedAttemptsByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find usage logs by usage type in date range (for analytics)
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.usageType = :usageType " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt ASC")
    List<BillingUsageLog> findByUsageTypeAndDateRange(
            @Param("usageType") String usageType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get usage summary for a company
     */
    @Query("SELECT bul.usageType, COUNT(bul), SUM(bul.usageCount) FROM BillingUsageLog bul " +
            "WHERE bul.companyId = :companyId GROUP BY bul.usageType")
    List<Object[]> getUsageSummaryByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find all blocked usage attempts in date range (for monitoring)
     */
    @Query("SELECT bul FROM BillingUsageLog bul WHERE bul.wasBlocked = true " +
            "AND bul.createdAt BETWEEN :startDate AND :endDate ORDER BY bul.createdAt DESC")
    List<BillingUsageLog> findAllBlockedUsageInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    // ⚠️ ADDED MISSING METHODS - Required by UsageAnalyticsServiceImpl

    /**
     * Find usage logs by company ID and created at between dates
     * Required by UsageAnalyticsServiceImpl.getUsageStats()
     * Required by UsageAnalyticsServiceImpl.getDailyAnswerUsage()
     */
    List<BillingUsageLog> findByCompanyIdAndCreatedAtBetween(
            Long companyId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Find usage logs by company ID and usage type with pagination
     * Required by UsageAnalyticsServiceImpl.getUsageLogs()
     */
    Page<BillingUsageLog> findByCompanyIdAndUsageTypeOrderByCreatedAtDesc(
            Long companyId,
            String usageType,
            Pageable pageable
    );

    /**
     * Find usage logs by company ID with pagination
     * Required by UsageAnalyticsServiceImpl.getUsageLogs()
     */
    Page<BillingUsageLog> findByCompanyIdOrderByCreatedAtDesc(
            Long companyId,
            Pageable pageable
    );
}