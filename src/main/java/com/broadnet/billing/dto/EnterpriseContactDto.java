package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingEnterpriseContact;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Enterprise contact request DTO.
 * Used for both request (create) and response (get/update).
 *
 * Architecture Plan: POST + GET /api/admin/enterprise/contacts
 * Schema: billing_enterprise_contacts table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseContactDto {

    private Long id;
    private Long companyId;

    private BillingEnterpriseContact.ContactType contactType;
    private String name;
    private String email;
    private String phone;
    private String jobTitle;
    private String companyName;
    private BillingEnterpriseContact.CompanySize companySize;

    private String message;

    /**
     * JSON string of estimated usage.
     * e.g. {"answers": 100000, "kb_pages": 5000, "agents": 20, "users": 100}
     */
    private String estimatedUsage;

    private String budgetRange;
    private BillingEnterpriseContact.PreferredContactMethod preferredContactMethod;
    private String preferredContactTime;

    private BillingEnterpriseContact.ContactStatus status;
    private Long assignedTo;
    private LocalDateTime assignedAt;
    private LocalDateTime firstContactedAt;
    private LocalDateTime closedAt;

    private BillingEnterpriseContact.Outcome outcome;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
