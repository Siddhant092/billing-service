package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.EnterpriseUsageBillingDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceFinalizeInvoiceParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingEnterpriseUsageServiceImpl implements BillingEnterpriseUsageService {

    private final BillingEnterpriseUsageBillingRepository usageBillingRepository;
    private final BillingEnterprisePricingRepository pricingRepository;
    private final CompanyBillingRepository companyBillingRepository;
    private final BillingUsageLogRepository usageLogRepository;
    private final BillingInvoiceServiceImpl invoiceService;
    private final BillingNotificationService notificationService;
    private final BillingEnterprisePricingService pricingService;

    // -------------------------------------------------------------------------
    // Get or create billing record
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterpriseUsageBillingDto getOrCreateBillingRecord(Long companyId,
                                                              LocalDateTime periodStart,
                                                              LocalDateTime periodEnd) {
        return usageBillingRepository
                .findByCompanyIdAndPeriod(companyId, periodStart, periodEnd)
                .map(this::toDto)
                .orElseGet(() -> {
                    BillingEnterpriseUsageBilling record = BillingEnterpriseUsageBilling.builder()
                            .companyId(companyId)
                            .billingPeriodStart(periodStart)
                            .billingPeriodEnd(periodEnd)
                            .billingStatus(BillingEnterpriseUsageBilling.BillingStatus.pending)
                            .answersUsed(0).kbPagesUsed(0).agentsUsed(0).usersUsed(0)
                            .answersAmountCents(0).kbPagesAmountCents(0)
                            .agentsAmountCents(0).usersAmountCents(0)
                            .subtotalCents(0).taxAmountCents(0).totalCents(0)
                            // Snapshot pricing rates at record creation
                            .answersRateCents(getPricingRate(companyId, "answers"))
                            .kbPagesRateCents(getPricingRate(companyId, "kb_pages"))
                            .agentsRateCents(getPricingRate(companyId, "agents"))
                            .usersRateCents(getPricingRate(companyId, "users"))
                            .build();
                    return toDto(usageBillingRepository.save(record));
                });
    }

    @Override
    @Transactional
    public EnterpriseUsageBillingDto initializeCurrentPeriod(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getBillingMode() != CompanyBilling.BillingMode.postpaid) {
            throw new EnterpriseSetupException("NOT_POSTPAID",
                    "Cannot initialize enterprise period: company is not postpaid");
        }

        LocalDateTime periodStart = billing.getCurrentBillingPeriodStart() != null
                ? billing.getCurrentBillingPeriodStart() : LocalDateTime.now().withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime periodEnd = billing.getCurrentBillingPeriodEnd() != null
                ? billing.getCurrentBillingPeriodEnd() : periodStart.plusMonths(1);

        return getOrCreateBillingRecord(companyId, periodStart, periodEnd);
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<EnterpriseUsageBillingDto> getBillingHistory(Long companyId, int page, int size) {
        return usageBillingRepository.findByCompanyId(companyId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseUsageBillingDto> getPendingBillingRecords() {
        return usageBillingRepository.findPendingBillingRecords()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnterpriseUsageBillingDto> getReadyToInvoice() {
        return usageBillingRepository.findCalculatedNotInvoiced()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public EnterpriseUsageBillingDto getBillingRecord(Long billingId) {
        return usageBillingRepository.findById(billingId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterpriseUsageBilling", "id", billingId));
    }

    // -------------------------------------------------------------------------
    // Calculate billing amounts
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterpriseUsageBillingDto calculateBilling(Long billingId) {
        BillingEnterpriseUsageBilling record = usageBillingRepository.findById(billingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterpriseUsageBilling", "id", billingId));

        if (record.getBillingStatus() != BillingEnterpriseUsageBilling.BillingStatus.pending) {
            throw new BillingStateException("INVALID_BILLING_STATUS",
                    "Cannot calculate: billing record " + billingId
                            + " is not in 'pending' status");
        }

        // Calculate per-metric amounts
        int answersAmount = calculateMetricAmount(
                record.getAnswersUsed(), record.getAnswersRateCents(), true);
        int kbAmount      = calculateMetricAmount(
                record.getKbPagesUsed(), record.getKbPagesRateCents(), false);
        int agentsAmount  = calculateMetricAmount(
                record.getAgentsUsed(), record.getAgentsRateCents(), false);
        int usersAmount   = calculateMetricAmount(
                record.getUsersUsed(), record.getUsersRateCents(), false);

        int subtotal = answersAmount + kbAmount + agentsAmount + usersAmount;

        // Apply minimum commitment
        if (!pricingService.isMinimumCommitmentMet(record.getCompanyId(), subtotal)) {
            subtotal = getMinimumCommitment(record.getCompanyId());
        }

        record.setAnswersAmountCents(answersAmount);
        record.setKbPagesAmountCents(kbAmount);
        record.setAgentsAmountCents(agentsAmount);
        record.setUsersAmountCents(usersAmount);
        record.setSubtotalCents(subtotal);
        record.setTaxAmountCents(0); // Tax applied externally via Stripe
        record.setTotalCents(subtotal);
        record.setBillingStatus(BillingEnterpriseUsageBilling.BillingStatus.calculated);

        BillingEnterpriseUsageBilling saved = usageBillingRepository.save(record);
        log.info("Calculated billing for id={} companyId={} total={}",
                billingId, record.getCompanyId(), subtotal);
        return toDto(saved);
    }

    @Override
    @Transactional
    public int calculateAllDueBillings() {
        List<BillingEnterpriseUsageBilling> due =
                usageBillingRepository.findDueBillingRecords(LocalDateTime.now());

        int count = 0;
        for (BillingEnterpriseUsageBilling record : due) {
            try {
                calculateBilling(record.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to calculate billing for id={}: {}", record.getId(), e.getMessage());
            }
        }
        log.info("Calculated {} enterprise billing records", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Stripe invoice generation
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public EnterpriseUsageBillingDto createStripeInvoice(Long billingId) {
        BillingEnterpriseUsageBilling record = usageBillingRepository.findById(billingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingEnterpriseUsageBilling", "id", billingId));

        if (record.getBillingStatus() != BillingEnterpriseUsageBilling.BillingStatus.calculated) {
            throw new BillingStateException("INVALID_BILLING_STATUS",
                    "Cannot invoice: billing record " + billingId
                            + " must be in 'calculated' status");
        }

        CompanyBilling billing = companyBillingRepository.findByCompanyId(record.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", record.getCompanyId()));

        try {
            // 1. Create Stripe Invoice with line items
            InvoiceCreateParams.Builder params = InvoiceCreateParams.builder()
                    .setCustomer(billing.getStripeCustomerId())
                    .setAutoAdvance(true) // auto-finalize
                    .putMetadata("company_id", String.valueOf(record.getCompanyId()))
                    .putMetadata("billing_record_id", String.valueOf(billingId));

            Invoice stripeInvoice = Invoice.create(params.build());

            // Add line items via InvoiceItem API
            addInvoiceLineItem(billing.getStripeCustomerId(), stripeInvoice.getId(),
                    "Answers (" + record.getAnswersUsed() + ")", record.getAnswersAmountCents());
            addInvoiceLineItem(billing.getStripeCustomerId(), stripeInvoice.getId(),
                    "KB Pages (" + record.getKbPagesUsed() + ")", record.getKbPagesAmountCents());
            addInvoiceLineItem(billing.getStripeCustomerId(), stripeInvoice.getId(),
                    "Agents (" + record.getAgentsUsed() + ")", record.getAgentsAmountCents());
            addInvoiceLineItem(billing.getStripeCustomerId(), stripeInvoice.getId(),
                    "Users (" + record.getUsersUsed() + ")", record.getUsersAmountCents());

            // 2. Finalize (auto-send to customer)
            stripeInvoice = stripeInvoice.finalizeInvoice(
                    InvoiceFinalizeInvoiceParams.builder().build());

            // 3. Update billing record
            record.setStripeInvoiceId(stripeInvoice.getId());
            record.setBillingStatus(BillingEnterpriseUsageBilling.BillingStatus.invoiced);
            record.setInvoicedAt(LocalDateTime.now());
            usageBillingRepository.save(record);

            // 4. Create local invoice record
            com.broadnet.billing.dto.InvoiceDto invoiceDto =
                    invoiceService.upsertFromStripeInvoice(record.getCompanyId(), stripeInvoice);

            // 5. Notify
            notificationService.notifyInvoiceCreated(
                    record.getCompanyId(), invoiceDto.getId(),
                    record.getTotalCents(), null, null);

            log.info("Created Stripe invoice {} for billingId={}", stripeInvoice.getId(), billingId);
            return toDto(record);

        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to create Stripe invoice for billing record: " + billingId, e);
        }
    }

    @Override
    @Transactional
    public int createAllInvoices() {
        List<BillingEnterpriseUsageBilling> ready =
                usageBillingRepository.findCalculatedNotInvoiced();

        int count = 0;
        for (BillingEnterpriseUsageBilling record : ready) {
            try {
                createStripeInvoice(record.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to create invoice for billingId={}: {}",
                        record.getId(), e.getMessage());
            }
        }
        log.info("Created {} enterprise Stripe invoices", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Usage tracking (enterprise — no blocking)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void trackUsage(Long companyId, BillingUsageLog.UsageType usageType, Integer count) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));

        if (billing.getBillingMode() != CompanyBilling.BillingMode.postpaid) {
            throw new EnterpriseSetupException("NOT_POSTPAID",
                    "trackUsage only applies to postpaid enterprise companies");
        }

        // Architecture Plan: "Enterprise: Always allow, just track"
        int before = getCurrentCount(billing, usageType);
        switch (usageType) {
            case answer          -> billing.setAnswersUsedInPeriod(
                    billing.getAnswersUsedInPeriod() + count);
            case kb_page_added, kb_page_updated -> billing.setKbPagesTotal(
                    billing.getKbPagesTotal() + count);
            case agent_created   -> billing.setAgentsTotal(billing.getAgentsTotal() + count);
            case user_created    -> billing.setUsersTotal(billing.getUsersTotal() + count);
        }
        companyBillingRepository.save(billing);

        // Log the usage event
        usageLogRepository.save(BillingUsageLog.builder()
                .companyId(companyId)
                .usageType(usageType)
                .usageCount(count)
                .beforeCount(before)
                .afterCount(before + count)
                .wasBlocked(false)
                .build());
    }

    // -------------------------------------------------------------------------
    // Revenue queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalRevenueInPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        return usageBillingRepository.getTotalRevenueInPeriod(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalRevenueByCompanyId(Long companyId) {
        return usageBillingRepository.getTotalRevenueByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getAverageMonthlyRevenue(Long companyId, int months) {
        Integer total = usageBillingRepository.getTotalRevenueByCompanyId(companyId);
        if (total == null || months <= 0) return 0;
        return total / months;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int calculateMetricAmount(Integer used, Integer rateCents, boolean perThousand) {
        if (used == null || rateCents == null || used == 0) return 0;
        if (perThousand) {
            return (int) Math.ceil((used / 1000.0) * rateCents);
        }
        return used * rateCents;
    }

    private int getCurrentCount(CompanyBilling b, BillingUsageLog.UsageType type) {
        return switch (type) {
            case answer          -> b.getAnswersUsedInPeriod();
            case kb_page_added, kb_page_updated -> b.getKbPagesTotal();
            case agent_created   -> b.getAgentsTotal();
            case user_created    -> b.getUsersTotal();
        };
    }

    private Integer getPricingRate(Long companyId, String metric) {
        return pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                .map(p -> switch (metric) {
                    case "answers"  -> p.getAnswersRateCents();
                    case "kb_pages" -> p.getKbPagesRateCents();
                    case "agents"   -> p.getAgentsRateCents();
                    case "users"    -> p.getUsersRateCents();
                    default         -> 0;
                }).orElse(0);
    }

    private Integer getMinimumCommitment(Long companyId) {
        return pricingRepository.findActivePricingByCompanyId(companyId, LocalDateTime.now())
                .map(p -> p.getMinimumMonthlyCommitmentCents() != null
                        ? p.getMinimumMonthlyCommitmentCents() : 0)
                .orElse(0);
    }

    private void addInvoiceLineItem(String customerId, String invoiceId,
                                    String description, Integer amountCents) throws StripeException {
        if (amountCents == null || amountCents == 0) return;
        com.stripe.model.InvoiceItem.create(
                com.stripe.param.InvoiceItemCreateParams.builder()
                        .setCustomer(customerId)
                        .setInvoice(invoiceId)
                        .setDescription(description)
                        .setAmount(amountCents.longValue())
                        .setCurrency("usd")
                        .build());
    }

    private EnterpriseUsageBillingDto toDto(BillingEnterpriseUsageBilling r) {
        return EnterpriseUsageBillingDto.builder()
                .id(r.getId())
                .companyId(r.getCompanyId())
                .billingPeriodStart(r.getBillingPeriodStart())
                .billingPeriodEnd(r.getBillingPeriodEnd())
                .billingStatus(r.getBillingStatus())
                .answersUsed(r.getAnswersUsed())
                .kbPagesUsed(r.getKbPagesUsed())
                .agentsUsed(r.getAgentsUsed())
                .usersUsed(r.getUsersUsed())
                .answersRateCents(r.getAnswersRateCents())
                .kbPagesRateCents(r.getKbPagesRateCents())
                .agentsRateCents(r.getAgentsRateCents())
                .usersRateCents(r.getUsersRateCents())
                .answersAmountCents(r.getAnswersAmountCents())
                .kbPagesAmountCents(r.getKbPagesAmountCents())
                .agentsAmountCents(r.getAgentsAmountCents())
                .usersAmountCents(r.getUsersAmountCents())
                .subtotalCents(r.getSubtotalCents())
                .taxAmountCents(r.getTaxAmountCents())
                .totalCents(r.getTotalCents())
                .totalFormatted(formatCents(r.getTotalCents()))
                .stripeInvoiceId(r.getStripeInvoiceId())
                .invoiceId(r.getInvoiceId())
                .invoicedAt(r.getInvoicedAt())
                .calculationNotes(r.getCalculationNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }

    private String formatCents(Integer cents) {
        if (cents == null) return "$0.00";
        return String.format("$%.2f", cents / 100.0);
    }
}