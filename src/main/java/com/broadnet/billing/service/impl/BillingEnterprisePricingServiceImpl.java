package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EnterprisePricingDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.BillingEnterprisePricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingEnterprisePricingServiceImpl implements BillingEnterprisePricingService {

    private final BillingEnterprisePricingRepository pricingRepository;
    private final CompanyBillingRepository companyBillingRepository;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public EnterprisePricingDto getActivePricing(Long companyId) {
        return pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing", "companyId", companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public EnterprisePricingDto getLatestPricing(Long companyId) {
        return pricingRepository.findLatestByCompanyId(companyId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing", "companyId", companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterprisePricingDto> getPricingHistory(Long companyId) {
        return pricingRepository.findAllByCompanyId(companyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EnterprisePricingDto getPricingOnDate(Long companyId, LocalDateTime date) {
        return pricingRepository.findActivePricingByCompanyId(companyId, date)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing",
                        "companyId+date", companyId + " @ " + date));
    }

    @Override
    @Transactional(readOnly = true)
    public EnterprisePricingDto getPricingByContractReference(String contractReference) {
        return pricingRepository.findByContractReference(contractReference)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing", "contractReference", contractReference));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterprisePricingDto> getAllActivePricing() {
        return pricingRepository.findActivePricing(LocalDateTime.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getCustomersNeedingPricingUpdate() {
        // Find enterprise companies whose active pricing has expired or has no pricing
        return pricingRepository.findExpiredPricing(LocalDateTime.now())
                .stream()
                .map(BillingEnterprisePricing::getCompanyId)
                .distinct()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Create / update pricing
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterprisePricingDto setPricing(Long companyId, EnterprisePricingDto dto,
                                           Long approvedBy) {
        // 1. Validate company exists and is enterprise
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getBillingMode() != CompanyBilling.BillingMode.postpaid) {
            throw new EnterpriseSetupException("NOT_POSTPAID",
                    "Cannot set enterprise pricing: company " + companyId
                            + " is not in postpaid billing mode");
        }

        // 2. Expire any currently active pricing for this company
        pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                .ifPresent(existing -> {
                    existing.setIsActive(false);
                    existing.setEffectiveTo(dto.getEffectiveFrom() != null
                            ? dto.getEffectiveFrom().minusSeconds(1) : LocalDateTime.now());
                    pricingRepository.save(existing);
                });

        // 3. Create new pricing record
        BillingEnterprisePricing pricing = BillingEnterprisePricing.builder()
                .companyId(companyId)
                .pricingTier(dto.getPricingTier() != null
                        ? dto.getPricingTier() : BillingEnterprisePricing.PricingTier.custom)
                .answersRateCents(dto.getAnswersRateCents())
                .kbPagesRateCents(dto.getKbPagesRateCents())
                .agentsRateCents(dto.getAgentsRateCents())
                .usersRateCents(dto.getUsersRateCents())
                .answersVolumeDiscountTiers(dto.getAnswersVolumeDiscountTiers())
                .kbPagesVolumeDiscountTiers(dto.getKbPagesVolumeDiscountTiers())
                .minimumMonthlyCommitmentCents(dto.getMinimumMonthlyCommitmentCents())
                .minimumAnswersCommitment(dto.getMinimumAnswersCommitment())
                .effectiveFrom(dto.getEffectiveFrom() != null
                        ? dto.getEffectiveFrom() : LocalDateTime.now())
                .effectiveTo(dto.getEffectiveTo())
                .isActive(true)
                .approvedBy(approvedBy)
                .approvedAt(LocalDateTime.now())
                .contractReference(dto.getContractReference())
                .notes(dto.getNotes())
                .build();

        BillingEnterprisePricing saved = pricingRepository.save(pricing);

        // 4. Link to company_billing
        billing.setEnterprisePricingId(saved.getId());
        companyBillingRepository.save(billing);

        log.info("Set enterprise pricing for companyId={}, pricingId={}", companyId, saved.getId());
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void expirePricing(Long pricingId) {
        BillingEnterprisePricing pricing = pricingRepository.findById(pricingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing", "id", pricingId));
        pricing.setIsActive(false);
        pricing.setEffectiveTo(LocalDateTime.now());
        pricingRepository.save(pricing);
        log.info("Expired enterprise pricing id={}", pricingId);
    }

    @Override
    @Transactional
    public void deactivatePricing(Long pricingId) {
        BillingEnterprisePricing pricing = pricingRepository.findById(pricingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterprisePricing", "id", pricingId));
        pricing.setIsActive(false);
        pricingRepository.save(pricing);
        log.info("Deactivated enterprise pricing id={}", pricingId);
    }

    // -------------------------------------------------------------------------
    // Billing calculation
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Integer calculateAmountDue(Long companyId, Integer answersUsed,
                                      Integer kbPagesUsed, Integer agentsUsed,
                                      Integer usersUsed) {
        BillingEnterprisePricing pricing =
                pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                        .orElseThrow(() -> new EnterpriseSetupException("NO_ACTIVE_PRICING",
                                "No active enterprise pricing for companyId: " + companyId));

        // Calculate amounts per metric
        // Answers: priced per 1000
        int answersAmount = pricing.getAnswersRateCents() != null && answersUsed != null
                ? (int) Math.ceil((answersUsed / 1000.0) * pricing.getAnswersRateCents()) : 0;
        int kbAmount      = pricing.getKbPagesRateCents() != null && kbPagesUsed != null
                ? kbPagesUsed * pricing.getKbPagesRateCents() : 0;
        int agentsAmount  = pricing.getAgentsRateCents() != null && agentsUsed != null
                ? agentsUsed * pricing.getAgentsRateCents() : 0;
        int usersAmount   = pricing.getUsersRateCents() != null && usersUsed != null
                ? usersUsed * pricing.getUsersRateCents() : 0;

        int total = answersAmount + kbAmount + agentsAmount + usersAmount;

        // Apply minimum monthly commitment
        if (pricing.getMinimumMonthlyCommitmentCents() != null
                && total < pricing.getMinimumMonthlyCommitmentCents()) {
            total = pricing.getMinimumMonthlyCommitmentCents();
        }

        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMinimumCommitmentMet(Long companyId, Integer amountDue) {
        return pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                .map(p -> p.getMinimumMonthlyCommitmentCents() == null
                        || amountDue >= p.getMinimumMonthlyCommitmentCents())
                .orElse(true);
    }

    // -------------------------------------------------------------------------
    // Converter
    // -------------------------------------------------------------------------

    private EnterprisePricingDto toDto(BillingEnterprisePricing p) {
        return EnterprisePricingDto.builder()
                .id(p.getId())
                .companyId(p.getCompanyId())
                .pricingTier(p.getPricingTier())
                .answersRateCents(p.getAnswersRateCents())
                .kbPagesRateCents(p.getKbPagesRateCents())
                .agentsRateCents(p.getAgentsRateCents())
                .usersRateCents(p.getUsersRateCents())
                .answersVolumeDiscountTiers(p.getAnswersVolumeDiscountTiers())
                .kbPagesVolumeDiscountTiers(p.getKbPagesVolumeDiscountTiers())
                .minimumMonthlyCommitmentCents(p.getMinimumMonthlyCommitmentCents())
                .minimumAnswersCommitment(p.getMinimumAnswersCommitment())
                .effectiveFrom(p.getEffectiveFrom())
                .effectiveTo(p.getEffectiveTo())
                .isActive(p.getIsActive())
                .approvedBy(p.getApprovedBy())
                .approvedAt(p.getApprovedAt())
                .contractReference(p.getContractReference())
                .notes(p.getNotes())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}