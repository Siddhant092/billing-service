package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EnterpriseContactDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingEnterpriseContactServiceImpl implements BillingEnterpriseContactService {

    private final BillingEnterpriseContactsRepository contactsRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingNotificationService notificationService;

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterpriseContactDto createContact(Long companyId, EnterpriseContactDto dto) {
        // Validate company exists
        companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        BillingEnterpriseContact contact = BillingEnterpriseContact.builder()
                .companyId(companyId)
                .contactType(dto.getContactType() != null
                        ? dto.getContactType()
                        : BillingEnterpriseContact.ContactType.enterprise_inquiry)
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .jobTitle(dto.getJobTitle())
                .companyName(dto.getCompanyName())
                .companySize(dto.getCompanySize())
                .message(dto.getMessage())
                .estimatedUsage(dto.getEstimatedUsage())
                .budgetRange(dto.getBudgetRange())
                .preferredContactMethod(dto.getPreferredContactMethod() != null
                        ? dto.getPreferredContactMethod()
                        : BillingEnterpriseContact.PreferredContactMethod.email)
                .preferredContactTime(dto.getPreferredContactTime())
                .status(BillingEnterpriseContact.ContactStatus.pending)
                .notes(dto.getNotes())
                .build();

        BillingEnterpriseContact saved = contactsRepository.save(contact);
        log.info("Created enterprise contact id={} for companyId={}", saved.getId(), companyId);
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getContactsByCompanyId(Long companyId) {
        return contactsRepository.findByCompanyId(companyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EnterpriseContactDto getContact(Long contactId) {
        return contactsRepository.findById(contactId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterpriseContact", "id", contactId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnterpriseContactDto> getAllContacts(int page, int size) {
        return contactsRepository.findAllContacts(PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnterpriseContactDto> getContactsByStatus(
            BillingEnterpriseContact.ContactStatus status, int page, int size) {
        return contactsRepository.findByStatus(status, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getContactsByAssignedUser(Long userId) {
        return contactsRepository.findByAssignedTo(userId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getQualifiedContacts() {
        return contactsRepository.findQualifiedContacts()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getInProgressContacts() {
        return contactsRepository.findInProgressContacts()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getPendingContacts() {
        return contactsRepository.findPendingContacts()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getUnassignedContacts() {
        return contactsRepository.findUnassignedContacts()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getContactsNeedingFollowUp(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return contactsRepository.findNeedingFollowUp(cutoff)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getRecentlyContacted(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return contactsRepository.findRecentlyContacted(since)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getHighValueContacts() {
        // Service-layer JSON filtering — fetch all with estimated_usage then filter
        return contactsRepository.findContactsWithEstimatedUsage()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseContactDto> getContactsByCreatedPeriod(LocalDateTime startDate,
                                                                 LocalDateTime endDate) {
        return contactsRepository.findByCreatedAtBetween(startDate, endDate)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Pipeline mutations
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterpriseContactDto assignContact(Long contactId, Long userId) {
        BillingEnterpriseContact contact = getContactEntity(contactId);
        contact.setAssignedTo(userId);
        contact.setAssignedAt(LocalDateTime.now());
        return toDto(contactsRepository.save(contact));
    }

    @Override
    @Transactional
    public EnterpriseContactDto updateContactStatus(Long contactId,
                                                    BillingEnterpriseContact.ContactStatus status,
                                                    String notes) {
        BillingEnterpriseContact contact = getContactEntity(contactId);
        contact.setStatus(status);
        if (notes != null && !notes.isBlank()) {
            String existing = contact.getNotes();
            contact.setNotes(existing != null
                    ? existing + "\n---\n" + notes : notes);
        }
        return toDto(contactsRepository.save(contact));
    }

    @Override
    @Transactional
    public EnterpriseContactDto markAsContacted(Long contactId) {
        BillingEnterpriseContact contact = getContactEntity(contactId);
        contact.setStatus(BillingEnterpriseContact.ContactStatus.contacted);
        if (contact.getFirstContactedAt() == null) {
            contact.setFirstContactedAt(LocalDateTime.now());
        }
        return toDto(contactsRepository.save(contact));
    }

    @Override
    @Transactional
    public EnterpriseContactDto qualifyContact(Long contactId) {
        BillingEnterpriseContact contact = getContactEntity(contactId);
        contact.setStatus(BillingEnterpriseContact.ContactStatus.qualified);
        return toDto(contactsRepository.save(contact));
    }

    @Override
    @Transactional
    public EnterpriseContactDto closeContact(Long contactId,
                                             BillingEnterpriseContact.Outcome outcome) {
        BillingEnterpriseContact contact = getContactEntity(contactId);
        contact.setStatus(BillingEnterpriseContact.ContactStatus.closed);
        contact.setOutcome(outcome);
        contact.setClosedAt(LocalDateTime.now());
        return toDto(contactsRepository.save(contact));
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getPipelineMetrics() {
        return contactsRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? row[0].toString() : "unknown",
                        row -> row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Long> getTeamMetrics() {
        return contactsRepository.countByAssignedTo().stream()
                .collect(Collectors.toMap(
                        row -> row[0] != null ? ((Number) row[0]).longValue() : 0L,
                        row -> row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L));
    }

    // -------------------------------------------------------------------------
    // Convert to enterprise customer
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public boolean convertToCustomer(Long contactId) {
        BillingEnterpriseContact contact = getContactEntity(contactId);

        CompanyBilling billing = companyBillingRepository
                .findByCompanyId(contact.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", contact.getCompanyId()));

        // Set billing mode to postpaid
        billing.setBillingMode(CompanyBilling.BillingMode.postpaid);
        billing.setActivePlanCode("enterprise");
        billing.setSubscriptionStatus(CompanyBilling.SubscriptionStatus.active);
        billing.setCurrentBillingPeriodStart(LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0));
        billing.setCurrentBillingPeriodEnd(billing.getCurrentBillingPeriodStart().plusMonths(1));
        // Set very high limits for enterprise (unlimited effectively)
        billing.setEffectiveAnswersLimit(Integer.MAX_VALUE);
        billing.setEffectiveKbPagesLimit(Integer.MAX_VALUE);
        billing.setEffectiveAgentsLimit(Integer.MAX_VALUE);
        billing.setEffectiveUsersLimit(Integer.MAX_VALUE);
        companyBillingRepository.save(billing);

        // Close the contact as signed
        contact.setStatus(BillingEnterpriseContact.ContactStatus.closed);
        contact.setOutcome(BillingEnterpriseContact.Outcome.signed);
        contact.setClosedAt(LocalDateTime.now());
        contactsRepository.save(contact);

        log.info("Converted contact {} to enterprise customer for companyId={}",
                contactId, contact.getCompanyId());
        return true;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BillingEnterpriseContact getContactEntity(Long contactId) {
        return contactsRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterpriseContact", "id", contactId));
    }

    private EnterpriseContactDto toDto(BillingEnterpriseContact c) {
        return EnterpriseContactDto.builder()
                .id(c.getId())
                .companyId(c.getCompanyId())
                .contactType(c.getContactType())
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .jobTitle(c.getJobTitle())
                .companyName(c.getCompanyName())
                .companySize(c.getCompanySize())
                .message(c.getMessage())
                .estimatedUsage(c.getEstimatedUsage())
                .budgetRange(c.getBudgetRange())
                .preferredContactMethod(c.getPreferredContactMethod())
                .preferredContactTime(c.getPreferredContactTime())
                .status(c.getStatus())
                .assignedTo(c.getAssignedTo())
                .assignedAt(c.getAssignedAt())
                .firstContactedAt(c.getFirstContactedAt())
                .closedAt(c.getClosedAt())
                .outcome(c.getOutcome())
                .notes(c.getNotes())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}