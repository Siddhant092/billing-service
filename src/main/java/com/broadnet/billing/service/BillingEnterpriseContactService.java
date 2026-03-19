package com.broadnet.billing.service;

import com.broadnet.billing.dto.EnterpriseContactDto;
import com.broadnet.billing.entity.BillingEnterpriseContact;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for enterprise contact/lead management.
 * Handles the sales pipeline and qualification tracking.
 *
 * Architecture Plan: billing_enterprise_contacts table
 * Admin API: GET /api/admin/enterprise/contacts + PATCH /api/admin/enterprise/contacts/{id}
 *
 * CHANGES FROM ORIGINAL:
 * - getContactByCompanyId: return type changed from single EnterpriseContactDto to List
 *   (a company can submit multiple contact requests — schema has no UNIQUE on company_id)
 * - getContactsByStatus: status param changed to BillingEnterpriseContact.ContactStatus enum
 * - updateContactStatus: status param changed to BillingEnterpriseContact.ContactStatus enum
 * - getHighValueContacts: kept but implementation note updated — filtering on JSON estimated_usage
 *   must be done in service layer (can't query JSON in JPQL)
 * - getPipelineMetrics: return type remains Map<String, Long> but note:
 *   keys are ContactStatus enum names
 * - REMOVED: addNote — not defined in architecture plan; notes field exists on entity
 *   but no separate "add note" API endpoint is specified. Use updateContactStatus with notes param.
 * - All other methods confirmed architecturally correct.
 */
public interface BillingEnterpriseContactService {

    /**
     * Create an enterprise contact request.
     * Architecture Plan: POST /api/admin/enterprise/contacts (from customer side)
     *
     * @param companyId The company ID
     * @param dto       Contact details (name, email, message, estimated usage, etc.)
     * @return Created EnterpriseContactDto
     */
    EnterpriseContactDto createContact(Long companyId, EnterpriseContactDto dto);

    /**
     * Get all contacts for a company.
     * FIXED: Changed from single Optional to List — a company can submit multiple contact requests.
     *
     * @param companyId The company ID
     * @return List of EnterpriseContactDto for the company
     */
    List<EnterpriseContactDto> getContactsByCompanyId(Long companyId);

    /**
     * Get a specific contact by ID.
     *
     * @param contactId The contact ID
     * @return EnterpriseContactDto
     */
    EnterpriseContactDto getContact(Long contactId);

    /**
     * Get all contacts — paginated, newest-first.
     * Architecture Plan: GET /api/admin/enterprise/contacts
     *
     * @param page Page number
     * @param size Page size
     * @return Page of EnterpriseContactDto
     */
    Page<EnterpriseContactDto> getAllContacts(int page, int size);

    /**
     * Get contacts by status — paginated.
     * Architecture Plan: GET /api/admin/enterprise/contacts?status=pending
     * FIXED: status param changed to BillingEnterpriseContact.ContactStatus enum.
     *
     * @param status Contact pipeline status
     * @param page   Page number
     * @param size   Page size
     * @return Page of contacts with given status
     */
    Page<EnterpriseContactDto> getContactsByStatus(
            BillingEnterpriseContact.ContactStatus status,
            int page,
            int size
    );

    /**
     * Get contacts assigned to a sales user.
     *
     * @param userId The sales user ID
     * @return List of assigned contacts
     */
    List<EnterpriseContactDto> getContactsByAssignedUser(Long userId);

    /**
     * Get contacts in 'qualified' status.
     *
     * @return List of qualified contacts
     */
    List<EnterpriseContactDto> getQualifiedContacts();

    /**
     * Get contacts in 'in_progress' status.
     *
     * @return List of in-progress contacts
     */
    List<EnterpriseContactDto> getInProgressContacts();

    /**
     * Get contacts in 'pending' status (not yet contacted).
     *
     * @return List of pending contacts — ordered oldest-first (prioritization)
     */
    List<EnterpriseContactDto> getPendingContacts();

    /**
     * Get contacts with no assigned sales rep.
     *
     * @return List of unassigned contacts
     */
    List<EnterpriseContactDto> getUnassignedContacts();

    /**
     * Assign a contact to a sales user.
     * Architecture Plan: PATCH /api/admin/enterprise/contacts/{contactId} with assignedTo
     *
     * @param contactId The contact ID
     * @param userId    The sales user ID to assign to
     * @return Updated EnterpriseContactDto
     */
    EnterpriseContactDto assignContact(Long contactId, Long userId);

    /**
     * Update contact status and notes.
     * Architecture Plan: PATCH /api/admin/enterprise/contacts/{contactId}
     * FIXED: status param changed to BillingEnterpriseContact.ContactStatus enum.
     *
     * @param contactId The contact ID
     * @param status    New status
     * @param notes     Update notes (appended or replaced)
     * @return Updated EnterpriseContactDto
     */
    EnterpriseContactDto updateContactStatus(
            Long contactId,
            BillingEnterpriseContact.ContactStatus status,
            String notes
    );

    /**
     * Mark a contact as first-contacted (sets first_contacted_at = now, status = contacted).
     *
     * @param contactId The contact ID
     * @return Updated EnterpriseContactDto
     */
    EnterpriseContactDto markAsContacted(Long contactId);

    /**
     * Mark a contact as qualified (sets status = qualified).
     *
     * @param contactId The contact ID
     * @return Updated EnterpriseContactDto
     */
    EnterpriseContactDto qualifyContact(Long contactId);

    /**
     * Close a contact with an outcome.
     *
     * @param contactId The contact ID
     * @param outcome   The outcome (signed/declined/no_response/not_qualified)
     * @return Updated EnterpriseContactDto
     */
    EnterpriseContactDto closeContact(Long contactId, BillingEnterpriseContact.Outcome outcome);

    /**
     * Get contacts that haven't had any activity in N days.
     *
     * @param days Number of days of inactivity threshold
     * @return List of contacts needing follow-up
     */
    List<EnterpriseContactDto> getContactsNeedingFollowUp(int days);

    /**
     * Get contacts that were first-contacted within the last N days.
     *
     * @param days Number of days to look back
     * @return List of recently contacted contacts
     */
    List<EnterpriseContactDto> getRecentlyContacted(int days);

    /**
     * Get contacts that have estimated usage data.
     * Note: Complex filtering on estimated_usage JSON is done in service layer.
     *
     * @return List of contacts with estimated usage data
     */
    List<EnterpriseContactDto> getHighValueContacts();

    /**
     * Get contacts created in a specific date range.
     *
     * @param startDate Start date
     * @param endDate   End date
     * @return List of contacts created in range
     */
    List<EnterpriseContactDto> getContactsByCreatedPeriod(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Get pipeline metrics — count per status.
     * Architecture Plan: GET /api/admin/enterprise/contacts includes status counts.
     * Returns Map of status name → count.
     *
     * @return Map of ContactStatus name to count
     */
    Map<String, Long> getPipelineMetrics();

    /**
     * Get team metrics — contact count per assigned sales user.
     *
     * @return Map of userId to contact count
     */
    Map<Long, Long> getTeamMetrics();

    /**
     * Convert a qualified contact to an enterprise customer.
     * Creates/updates company_billing with enterprise settings:
     * Sets billing_mode=postpaid, active_plan_code=enterprise, links enterprise_pricing_id.
     *
     * @param contactId The contact ID to convert
     * @return true if conversion succeeded
     */
    boolean convertToCustomer(Long contactId);
}