package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingEnterpriseContact;
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
 * Repository for billing_enterprise_contacts table.
 *
 * CHANGES FROM ORIGINAL:
 * - findByStatus: status param type changed to BillingEnterpriseContact.ContactStatus enum
 * - findQualifiedContacts: 'qualified' literal → enum constant; bec.qualifiedAt → removed
 *   (field was renamed to firstContactedAt and closedAt — qualifiedAt does not exist in corrected entity)
 * - findInProgressContacts: 'in_progress' literal → enum constant
 * - findPendingContacts: 'pending' literal → enum constant
 * - findClosedContacts: 'closed' literal → enum constant; bec.closedAt → correct field exists
 * - findRecentlyContacted: bec.contactedAt → bec.firstContactedAt (field was renamed in entity)
 * - findHighValueContacts: REMOVED — queried estimatedAnswers/KbPages/Agents/Users fields which
 *   were replaced by estimatedUsage JSON column. This query cannot be expressed in JPQL against JSON.
 *   Replaced with findByEstimatedUsageIsNotNull for basic filtering; complex filtering must be done in service layer.
 * - findByContactEmail: field renamed in entity — bec.contactEmail → bec.email
 * - findByCompanyId: returns List (a company can have multiple contacts) — changed from Optional
 * - REMOVED: findNeedingFollowUp references bec.updatedAt — this field exists, so kept.
 *   But removed status string literals → enum constants.
 */
@Repository
public interface BillingEnterpriseContactsRepository extends JpaRepository<BillingEnterpriseContact, Long> {

    /**
     * Find all contacts for a company.
     * FIXED: changed from Optional to List — a company can submit multiple contact requests.
     */
    List<BillingEnterpriseContact> findByCompanyId(Long companyId);

    /**
     * Find all contacts — paginated, newest-first.
     * Used by /api/admin/enterprise/contacts.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec ORDER BY bec.createdAt DESC")
    Page<BillingEnterpriseContact> findAllContacts(Pageable pageable);

    /**
     * Find contacts by status — paginated.
     * FIXED: status param type changed to BillingEnterpriseContact.ContactStatus enum.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status = :status ORDER BY bec.createdAt DESC")
    Page<BillingEnterpriseContact> findByStatus(
            @Param("status") BillingEnterpriseContact.ContactStatus status,
            Pageable pageable
    );

    /**
     * Find contacts assigned to a specific sales user.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.assignedTo = :userId ORDER BY bec.createdAt DESC")
    List<BillingEnterpriseContact> findByAssignedTo(@Param("userId") Long userId);

    /**
     * Find qualified contacts — sorted newest-first.
     * FIXED: 'qualified' → enum constant; removed bec.qualifiedAt (field doesn't exist in corrected entity).
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status = " +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.qualified " +
            "ORDER BY bec.updatedAt DESC")
    List<BillingEnterpriseContact> findQualifiedContacts();

    /**
     * Find in-progress contacts.
     * FIXED: 'in_progress' → enum constant.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status = " +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.in_progress " +
            "ORDER BY bec.updatedAt DESC")
    List<BillingEnterpriseContact> findInProgressContacts();

    /**
     * Find pending contacts (not yet contacted) — oldest-first for prioritization.
     * FIXED: 'pending' → enum constant.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status = " +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.pending " +
            "ORDER BY bec.createdAt ASC")
    List<BillingEnterpriseContact> findPendingContacts();

    /**
     * Find closed contacts.
     * FIXED: 'closed' → enum constant.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status = " +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.closed " +
            "ORDER BY bec.closedAt DESC")
    List<BillingEnterpriseContact> findClosedContacts();

    /**
     * Find recently first-contacted records.
     * FIXED: bec.contactedAt → bec.firstContactedAt (field was renamed in corrected entity).
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.firstContactedAt >= :since " +
            "ORDER BY bec.firstContactedAt DESC")
    List<BillingEnterpriseContact> findRecentlyContacted(@Param("since") LocalDateTime since);

    /**
     * Find contacts created in a date range.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec " +
            "WHERE bec.createdAt BETWEEN :startDate AND :endDate ORDER BY bec.createdAt DESC")
    List<BillingEnterpriseContact> findByCreatedAtBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find contacts that have estimated usage data (for high-value lead identification).
     * REPLACES: findHighValueContacts — the original queried separate Long fields that no longer exist.
     * Those fields were replaced by estimatedUsage JSON in the corrected entity.
     * Complex filtering on JSON fields must be done in the service layer after fetching.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.estimatedUsage IS NOT NULL " +
            "ORDER BY bec.createdAt DESC")
    List<BillingEnterpriseContact> findContactsWithEstimatedUsage();

    /**
     * Count contacts grouped by status.
     * Returns List<Object[]> with [ContactStatus enum, Long count].
     */
    @Query("SELECT bec.status, COUNT(bec) FROM BillingEnterpriseContact bec GROUP BY bec.status")
    List<Object[]> countByStatus();

    /**
     * Count contacts grouped by assigned sales user.
     * Returns List<Object[]> with [Long assignedTo, Long count].
     */
    @Query("SELECT bec.assignedTo, COUNT(bec) FROM BillingEnterpriseContact bec " +
            "WHERE bec.assignedTo IS NOT NULL GROUP BY bec.assignedTo")
    List<Object[]> countByAssignedTo();

    /**
     * Find contacts with no assigned sales rep.
     * Used by admin to identify unassigned leads.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.assignedTo IS NULL ORDER BY bec.createdAt ASC")
    List<BillingEnterpriseContact> findUnassignedContacts();

    /**
     * Find contacts needing follow-up (in active statuses, not updated recently).
     * FIXED: status string literals → enum constants.
     */
    @Query("SELECT bec FROM BillingEnterpriseContact bec WHERE bec.status IN (" +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.pending, " +
            "com.broadnet.billing.entity.BillingEnterpriseContact$ContactStatus.in_progress) " +
            "AND bec.updatedAt <= :cutoffDate ORDER BY bec.updatedAt ASC")
    List<BillingEnterpriseContact> findNeedingFollowUp(@Param("cutoffDate") LocalDateTime cutoffDate);

    /** Check if any contact exists for a company. */
    boolean existsByCompanyId(Long companyId);

    /**
     * Find contact by email address.
     * FIXED: field renamed contactEmail → email in corrected entity.
     */
    Optional<BillingEnterpriseContact> findByEmail(String email);
}