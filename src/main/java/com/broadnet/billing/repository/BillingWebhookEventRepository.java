package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingWebhookEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, Long> {

    /**
     * Find webhook event by Stripe event ID (for idempotency)
     */
    Optional<BillingWebhookEvent> findByStripeEventId(String stripeEventId);

    /**
     * Check if event has been processed (for idempotency)
     */
    boolean existsByStripeEventId(String stripeEventId);

    /**
     * Find unprocessed webhook events
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findUnprocessedEvents();

    /**
     * Find failed webhook events (for retry)
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "AND bwe.errorMessage IS NOT NULL ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findFailedEvents();

    /**
     * Find webhook events by customer ID
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.stripeCustomerId = :customerId " +
            "ORDER BY bwe.createdAt DESC")
    Page<BillingWebhookEvent> findByStripeCustomerId(
            @Param("customerId") String customerId,
            Pageable pageable
    );

    /**
     * Find webhook events by subscription ID
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.stripeSubscriptionId = :subscriptionId " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findByStripeSubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Find webhook events by event type
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.eventType = :eventType " +
            "ORDER BY bwe.createdAt DESC")
    Page<BillingWebhookEvent> findByEventType(
            @Param("eventType") String eventType,
            Pageable pageable
    );

    /**
     * Find recent webhook events (last N hours)
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.createdAt >= :since " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findRecentEvents(@Param("since") LocalDateTime since);

    /**
     * Find events with high retry count (needs attention)
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.retryCount >= :threshold " +
            "AND bwe.processed = false ORDER BY bwe.retryCount DESC, bwe.createdAt ASC")
    List<BillingWebhookEvent> findHighRetryCountEvents(@Param("threshold") Integer threshold);

    /**
     * Mark event as processed
     */
    @Modifying
    @Query("UPDATE BillingWebhookEvent bwe SET bwe.processed = true, " +
            "bwe.processedAt = :processedAt WHERE bwe.id = :id")
    void markAsProcessed(@Param("id") Long id, @Param("processedAt") LocalDateTime processedAt);

    /**
     * Increment retry count
     */
    @Modifying
    @Query("UPDATE BillingWebhookEvent bwe SET bwe.retryCount = bwe.retryCount + 1, " +
            "bwe.errorMessage = :errorMessage WHERE bwe.id = :id")
    void incrementRetryCount(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    /**
     * Find webhook events in date range
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count processed vs unprocessed events
     */
    @Query("SELECT bwe.processed, COUNT(bwe) FROM BillingWebhookEvent bwe GROUP BY bwe.processed")
    List<Object[]> countByProcessedStatus();

    /**
     * Count events by event type
     */
    @Query("SELECT bwe.eventType, COUNT(bwe) FROM BillingWebhookEvent bwe GROUP BY bwe.eventType")
    List<Object[]> countByEventType();

    /**
     * Delete old processed events (for cleanup)
     */
    @Modifying
    @Query("DELETE FROM BillingWebhookEvent bwe WHERE bwe.processed = true " +
            "AND bwe.processedAt < :cutoffDate")
    void deleteOldProcessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ⚠️ ADDED MISSING METHOD - Required by BillingScheduledJobsServiceImpl

    /**
     * Delete events created before date (alternative cleanup method)
     * Required by BillingScheduledJobsServiceImpl.cleanupOldWebhookEvents()
     */
    @Modifying
    @Query("DELETE FROM BillingWebhookEvent bwe WHERE bwe.createdAt < :cutoffDate AND bwe.processed = true")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "AND bwe.errorMessage IS NOT NULL AND bwe.retryCount < :maxRetries ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findFailedWebhooks(@Param("maxRetries") int maxRetries);

    @Query("SELECT COUNT(bwe) FROM BillingWebhookEvent bwe WHERE bwe.status = :status")
    long countByStatus(@Param("status") String status);
}