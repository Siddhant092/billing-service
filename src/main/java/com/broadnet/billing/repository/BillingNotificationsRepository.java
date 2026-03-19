package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for billing_notifications table.
 *
 * CHANGES FROM ORIGINAL:
 * - findByCompanyIdAndType: notificationType param type changed to BillingNotification.NotificationType enum
 * - findByCompanyIdAndSeverity: severity param type changed to BillingNotification.Severity enum
 * - findCriticalUnreadByCompanyId: 'critical' severity literal removed — schema has no 'critical'.
 *   Replaced with 'error' severity (the closest equivalent per architecture plan).
 * - All @Modifying queries confirmed correct — markAsRead, markAllAsRead, deleteExpired.
 */
@Repository
public interface BillingNotificationsRepository extends JpaRepository<BillingNotification, Long> {

    /**
     * Find all notifications for a company — paginated, newest-first.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId ORDER BY bn.createdAt DESC")
    Page<BillingNotification> findByCompanyId(
            @Param("companyId") Long companyId,
            Pageable pageable
    );

    /**
     * Find unread notifications for a company — newest-first.
     * Architecture Plan: "Query billing_notifications where company_id=? and is_read=FALSE"
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.isRead = false ORDER BY bn.createdAt DESC")
    List<BillingNotification> findUnreadByCompanyId(@Param("companyId") Long companyId);

    /**
     * Count unread notifications for a company.
     * Used by the notifications API (returns unreadCount in response).
     */
    @Query("SELECT COUNT(bn) FROM BillingNotification bn WHERE bn.companyId = :companyId AND bn.isRead = false")
    Long countUnreadByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find notifications by type for a company.
     * FIXED: type param changed to BillingNotification.NotificationType enum.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.notificationType = :type ORDER BY bn.createdAt DESC")
    List<BillingNotification> findByCompanyIdAndType(
            @Param("companyId") Long companyId,
            @Param("type") BillingNotification.NotificationType type
    );

    /**
     * Find notifications by severity for a company.
     * FIXED: severity param changed to BillingNotification.Severity enum.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.severity = :severity ORDER BY bn.createdAt DESC")
    List<BillingNotification> findByCompanyIdAndSeverity(
            @Param("companyId") Long companyId,
            @Param("severity") BillingNotification.Severity severity
    );

    /**
     * Find unread error-severity notifications for a company.
     * FIXED: 'critical' does not exist in schema enum — replaced with 'error'.
     * Architecture Plan Severity enum: info, warning, error, success.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.severity = com.broadnet.billing.entity.BillingNotification$Severity.error " +
            "AND bn.isRead = false ORDER BY bn.createdAt DESC")
    List<BillingNotification> findErrorUnreadByCompanyId(@Param("companyId") Long companyId);

    /**
     * Find active (non-expired) notifications for a company.
     * Used by dashboard to show relevant notifications only.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND (bn.expiresAt IS NULL OR bn.expiresAt > :currentDate) ORDER BY bn.createdAt DESC")
    List<BillingNotification> findActiveByCompanyId(
            @Param("companyId") Long companyId,
            @Param("currentDate") LocalDateTime currentDate
    );

    /**
     * Find expired notifications for cleanup cron job.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.expiresAt IS NOT NULL AND bn.expiresAt <= :currentDate")
    List<BillingNotification> findExpiredNotifications(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Mark a single notification as read.
     */
    @Modifying
    @Query("UPDATE BillingNotification bn SET bn.isRead = true, bn.readAt = :readAt WHERE bn.id = :id")
    void markAsRead(@Param("id") Long id, @Param("readAt") LocalDateTime readAt);

    /**
     * Mark all unread notifications as read for a company.
     * Returns count of rows updated.
     */
    @Modifying
    @Query("UPDATE BillingNotification bn SET bn.isRead = true, bn.readAt = :readAt " +
            "WHERE bn.companyId = :companyId AND bn.isRead = false")
    int markAllAsRead(@Param("companyId") Long companyId, @Param("readAt") LocalDateTime readAt);

    /**
     * Delete expired notifications (cleanup cron job).
     * Returns count of rows deleted.
     */
    @Modifying
    @Query("DELETE FROM BillingNotification bn WHERE bn.expiresAt IS NOT NULL AND bn.expiresAt <= :currentDate")
    int deleteExpired(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Find notifications for a company in a date range.
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.createdAt BETWEEN :startDate AND :endDate ORDER BY bn.createdAt DESC")
    List<BillingNotification> findByCompanyIdAndDateRange(
            @Param("companyId") Long companyId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find notifications with a specific action URL for a company.
     * Used to check for duplicate notifications before creating new ones (deduplication).
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.actionUrl = :actionUrl ORDER BY bn.createdAt DESC")
    List<BillingNotification> findByActionUrl(
            @Param("companyId") Long companyId,
            @Param("actionUrl") String actionUrl
    );

    /**
     * Find unread notifications by type for deduplication.
     * Architecture Plan: "Same notification type within 24 hours: update existing instead of creating new"
     */
    @Query("SELECT bn FROM BillingNotification bn WHERE bn.companyId = :companyId " +
            "AND bn.notificationType = :type AND bn.isRead = false " +
            "AND bn.createdAt >= :since ORDER BY bn.createdAt DESC")
    List<BillingNotification> findRecentUnreadByType(
            @Param("companyId") Long companyId,
            @Param("type") BillingNotification.NotificationType type,
            @Param("since") LocalDateTime since
    );
}