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

/**
 * Repository for billing_webhook_events table.
 * Idempotency store and retry queue for Stripe webhook events.
 *
 * CHANGES FROM ORIGINAL:
 * - REMOVED: countByStatus(@Param("status") String status)
 *   The 'status' field was removed from BillingWebhookEvent entity (not in architecture plan schema).
 *   This method referenced bwe.status which no longer exists.
 * - All other methods confirmed correct against the corrected entity.
 * - findFailedWebhooks: confirmed correct (uses processed, errorMessage, retryCount — all present).
 * - incrementRetryCount: confirmed correct.
 * - deleteOldProcessedEvents / deleteByCreatedAtBefore: confirmed correct.
 */
@Repository
public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, Long> {

    /**
     * Find webhook event by Stripe event ID.
     * Primary idempotency check — called before any webhook processing.
     * Architecture Plan: "Check idempotency (stripe_event_id)" — first step in webhook flow.
     */
    Optional<BillingWebhookEvent> findByStripeEventId(String stripeEventId);

    /**
     * Check if a Stripe event has already been stored.
     * Faster than findByStripeEventId when you only need existence.
     */
    boolean existsByStripeEventId(String stripeEventId);

    /**
     * Find all unprocessed webhook events ordered by creation time (FIFO).
     * Used by webhook retry job.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findUnprocessedEvents();

    /**
     * Find failed webhook events (unprocessed + have an error message).
     * Used by webhook retry job to identify events that need to be retried.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "AND bwe.errorMessage IS NOT NULL ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findFailedEvents();

    /**
     * Find webhook events by Stripe customer ID — paginated.
     * Used by admin to inspect all events for a specific customer.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.stripeCustomerId = :customerId " +
            "ORDER BY bwe.createdAt DESC")
    Page<BillingWebhookEvent> findByStripeCustomerId(
            @Param("customerId") String customerId,
            Pageable pageable
    );

    /**
     * Find all webhook events for a Stripe subscription ID.
     * Used by admin to audit a specific subscription's event history.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.stripeSubscriptionId = :subscriptionId " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findByStripeSubscriptionId(@Param("subscriptionId") String subscriptionId);

    /**
     * Find webhook events by event type — paginated.
     * Used by admin to inspect all events of a specific type (e.g. all invoice.payment_failed).
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.eventType = :eventType " +
            "ORDER BY bwe.createdAt DESC")
    Page<BillingWebhookEvent> findByEventType(
            @Param("eventType") String eventType,
            Pageable pageable
    );

    /**
     * Find events created after a given time (recent events window).
     * Used for monitoring webhook processing latency.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.createdAt >= :since " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findRecentEvents(@Param("since") LocalDateTime since);

    /**
     * Find unprocessed events with high retry counts that need manual attention.
     * Architecture Plan: "Retry up to 3 times, then alert"
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.retryCount >= :threshold " +
            "AND bwe.processed = false ORDER BY bwe.retryCount DESC, bwe.createdAt ASC")
    List<BillingWebhookEvent> findHighRetryCountEvents(@Param("threshold") Integer threshold);

    /**
     * Mark a webhook event as successfully processed.
     * Called at the end of successful webhook processing.
     */
    @Modifying
    @Query("UPDATE BillingWebhookEvent bwe SET bwe.processed = true, " +
            "bwe.processedAt = :processedAt WHERE bwe.id = :id")
    void markAsProcessed(@Param("id") Long id, @Param("processedAt") LocalDateTime processedAt);

    /**
     * Increment retry count and record the latest error message.
     * Called when webhook processing fails.
     */
    @Modifying
    @Query("UPDATE BillingWebhookEvent bwe SET bwe.retryCount = bwe.retryCount + 1, " +
            "bwe.errorMessage = :errorMessage WHERE bwe.id = :id")
    void incrementRetryCount(@Param("id") Long id, @Param("errorMessage") String errorMessage);

    /**
     * Find webhook events in a date range (admin/audit view).
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.createdAt BETWEEN :startDate AND :endDate " +
            "ORDER BY bwe.createdAt DESC")
    List<BillingWebhookEvent> findByDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count events grouped by processed status.
     * Returns List<Object[]> with [Boolean processed, Long count].
     * Used by monitoring dashboard to see processing backlog.
     */
    @Query("SELECT bwe.processed, COUNT(bwe) FROM BillingWebhookEvent bwe GROUP BY bwe.processed")
    List<Object[]> countByProcessedStatus();

    /**
     * Count events grouped by event type.
     * Returns List<Object[]> with [String eventType, Long count].
     * Used by monitoring dashboard to see event distribution.
     */
    @Query("SELECT bwe.eventType, COUNT(bwe) FROM BillingWebhookEvent bwe GROUP BY bwe.eventType")
    List<Object[]> countByEventType();

    /**
     * Delete old processed events for storage cleanup.
     * Architecture Plan: "Enable replay and debugging" — keep for a retention window, then purge.
     */
    @Modifying
    @Query("DELETE FROM BillingWebhookEvent bwe WHERE bwe.processed = true " +
            "AND bwe.processedAt < :cutoffDate")
    void deleteOldProcessedEvents(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete processed events created before a cutoff date.
     * Alternative cleanup method — used by BillingScheduledJobsServiceImpl.cleanupOldWebhookEvents().
     */
    @Modifying
    @Query("DELETE FROM BillingWebhookEvent bwe WHERE bwe.createdAt < :cutoffDate AND bwe.processed = true")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find failed webhook events eligible for retry (under max retry limit).
     * Architecture Plan: "Retry up to 3 times" — webhook retry job.
     */
    @Query("SELECT bwe FROM BillingWebhookEvent bwe WHERE bwe.processed = false " +
            "AND bwe.errorMessage IS NOT NULL AND bwe.retryCount < :maxRetries ORDER BY bwe.createdAt ASC")
    List<BillingWebhookEvent> findFailedWebhooks(@Param("maxRetries") int maxRetries);
}