package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.BillingEntitlementHistory;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.repository.BillingEntitlementHistoryRepository;
import com.broadnet.billing.service.BillingDashboardService;
import com.broadnet.billing.service.CompanyBillingService;
import com.broadnet.billing.service.EntitlementService;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.InvoiceListParams;
import com.stripe.param.PaymentMethodListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of BillingDashboardService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingDashboardServiceImpl implements BillingDashboardService {

    private final CompanyBillingService companyBillingService;
    private final EntitlementService entitlementService;
    private final BillingEntitlementHistoryRepository entitlementHistoryRepository;

    @Override
    public BillingSnapshotDto getBillingSnapshot(Long companyId) {
        log.info("Fetching billing snapshot for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        // Get usage metrics
        UsageMetricsDto usageMetrics = getUsageMetrics(companyId);

        // Get payment method
        PaymentMethodDto paymentMethod = getDefaultPaymentMethod(billing.getStripeCustomerId());

        // Get upcoming invoice
        UpcomingInvoiceDto upcomingInvoice = getUpcomingInvoice(billing.getStripeCustomerId());

        // Build alerts
        List<String> alerts = buildAlerts(billing, usageMetrics);

        return BillingSnapshotDto.builder()
                .planCode(billing.getActivePlanCode())
                .subscriptionStatus(billing.getSubscriptionStatus())
                .billingInterval(billing.getBillingInterval())
                .renewalDate(billing.getPeriodEnd())
                .cancelAtPeriodEnd(billing.getCancelAtPeriodEnd())
                .usageMetrics(usageMetrics)
                .defaultPaymentMethod(paymentMethod)
                .upcomingInvoice(upcomingInvoice)
                .activeAddonCodes(billing.getActiveAddonCodes())
                .pendingPlanCode(billing.getPendingPlanCode())
                .pendingEffectiveDate(billing.getPendingEffectiveDate())
                .alerts(alerts)
                .stripeCustomerId(billing.getStripeCustomerId())
                .stripeSubscriptionId(billing.getStripeSubscriptionId())
                .build();
    }

    @Override
    public UsageMetricsDto getUsageMetrics(Long companyId) {
        log.debug("Fetching usage metrics for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        long daysUntilReset = billing.getPeriodEnd() != null ?
                ChronoUnit.DAYS.between(LocalDateTime.now(), billing.getPeriodEnd()) : 0;

        return UsageMetricsDto.builder()
                // Answers
                .answersUsed(billing.getAnswersUsedInPeriod())
                .answersLimit(billing.getEffectiveAnswersLimit())
                .answersRemaining(Math.max(0, billing.getEffectiveAnswersLimit() -
                        billing.getAnswersUsedInPeriod()))
                .answersPercentageUsed(calculatePercentage(
                        billing.getAnswersUsedInPeriod(), billing.getEffectiveAnswersLimit()))
                .answersBlocked(billing.getAnswersBlocked())
                // KB Pages
                .kbPagesTotal(billing.getKbPagesTotal())
                .kbPagesLimit(billing.getEffectiveKbPagesLimit())
                .kbPagesRemaining(Math.max(0, billing.getEffectiveKbPagesLimit() -
                        billing.getKbPagesTotal()))
                .kbPagesPercentageUsed(calculatePercentage(
                        billing.getKbPagesTotal(), billing.getEffectiveKbPagesLimit()))
                // Agents
                .agentsTotal(billing.getAgentsTotal())
                .agentsLimit(billing.getEffectiveAgentsLimit())
                .agentsRemaining(Math.max(0, billing.getEffectiveAgentsLimit() -
                        billing.getAgentsTotal()))
                .agentsPercentageUsed(calculatePercentage(
                        billing.getAgentsTotal(), billing.getEffectiveAgentsLimit()))
                // Users
                .usersTotal(billing.getUsersTotal())
                .usersLimit(billing.getEffectiveUsersLimit())
                .usersRemaining(Math.max(0, billing.getEffectiveUsersLimit() -
                        billing.getUsersTotal()))
                .usersPercentageUsed(calculatePercentage(
                        billing.getUsersTotal(), billing.getEffectiveUsersLimit()))
                // Period Info
                .periodStart(billing.getPeriodStart())
                .periodEnd(billing.getPeriodEnd())
                .daysUntilReset((int) daysUntilReset)
                .build();
    }

    @Override
    public CurrentPlanDto getCurrentPlan(Long companyId) {
        log.debug("Fetching current plan for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        return CurrentPlanDto.builder()
                .planCode(billing.getActivePlanCode())
                .billingInterval(billing.getBillingInterval())
                .renewalDate(billing.getPeriodEnd())
                .cancelAtPeriodEnd(billing.getCancelAtPeriodEnd())
                .build();
    }

    @Override
    public Page<InvoiceDto> getInvoices(Long companyId, int page, int size) {
        log.info("Fetching invoices for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            InvoiceListParams params = InvoiceListParams.builder()
                    .setCustomer(billing.getStripeCustomerId())
                    .setLimit((long) size)
                    .build();

            List<Invoice> invoices = Invoice.list(params).getData();

            List<InvoiceDto> invoiceDtos = invoices.stream()
                    .map(this::convertToInvoiceDto)
                    .collect(Collectors.toList());

            return new PageImpl<>(invoiceDtos, PageRequest.of(page, size), invoiceDtos.size());

        } catch (StripeException e) {
            log.error("Failed to fetch invoices", e);
            return Page.empty();
        }
    }

    @Override
    public List<PaymentMethodDto> getPaymentMethods(Long companyId) {
        log.info("Fetching payment methods for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(billing.getStripeCustomerId())
                    .setType(PaymentMethodListParams.Type.CARD)
                    .build();

            List<PaymentMethod> paymentMethods = PaymentMethod.list(params).getData();

            return paymentMethods.stream()
                    .map(this::convertToPaymentMethodDto)
                    .collect(Collectors.toList());

        } catch (StripeException e) {
            log.error("Failed to fetch payment methods", e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<NotificationDto> getNotifications(Long companyId, boolean unreadOnly) {
        log.info("Fetching notifications for company: {}", companyId);

        // TODO: Implement notifications table and fetch from there
        // For now, return empty list
        return Collections.emptyList();
    }

    @Override
    public void markNotificationAsRead(Long notificationId) {
        log.info("Marking notification {} as read", notificationId);

        // TODO: Implement notification update
    }

    @Override
    public UsageHistoryDto getUsageHistory(Long companyId, int days) {
        log.info("Fetching usage history for company {} ({} days)", companyId, days);

        // TODO: Implement by querying billing_usage_logs
        // For now, return empty history

        return UsageHistoryDto.builder()
                .dailyAnswerCounts(new HashMap<>())
                .dailyKbPageChanges(new HashMap<>())
                .totalAnswers(0)
                .totalKbPages(0)
                .averageAnswersPerDay(0.0)
                .trend("stable")
                .build();
    }

    @Override
    public Page<EntitlementHistoryDto> getEntitlementHistory(Long companyId, int page, int size) {
        log.info("Fetching entitlement history for company: {}", companyId);

        Page<BillingEntitlementHistory> historyPage = entitlementHistoryRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size));

        List<EntitlementHistoryDto> dtos = historyPage.getContent().stream()
                .map(this::convertToEntitlementHistoryDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, PageRequest.of(page, size), historyPage.getTotalElements());
    }

    private PaymentMethodDto getDefaultPaymentMethod(String customerId) {
        try {
            Customer customer = Customer.retrieve(customerId);
            String defaultPmId = customer.getInvoiceSettings().getDefaultPaymentMethod();

            if (defaultPmId != null) {
                PaymentMethod pm = PaymentMethod.retrieve(defaultPmId);
                return convertToPaymentMethodDto(pm);
            }
        } catch (StripeException e) {
            log.error("Failed to fetch default payment method", e);
        }

        return null;
    }

    private UpcomingInvoiceDto getUpcomingInvoice(String customerId) {
        try {
            com.stripe.param.InvoiceUpcomingParams params =
                    com.stripe.param.InvoiceUpcomingParams.builder()
                            .setCustomer(customerId)
                            .build();

            Invoice invoice = Invoice.upcoming(params);

            return UpcomingInvoiceDto.builder()
                    .amountDue((int) (long)invoice.getAmountDue())
                    .subtotal((int)(long) invoice.getSubtotal())
                    .total((int)(long) invoice.getTotal())
                    .currency(invoice.getCurrency())
                    .periodStart(LocalDateTime.ofEpochSecond(
                            invoice.getPeriodStart(), 0, java.time.ZoneOffset.UTC))
                    .periodEnd(LocalDateTime.ofEpochSecond(
                            invoice.getPeriodEnd(), 0, java.time.ZoneOffset.UTC))
                    .build();

        } catch (StripeException e) {
            log.error("Failed to fetch upcoming invoice", e);
            return null;
        }
    }

    private List<String> buildAlerts(CompanyBilling billing, UsageMetricsDto metrics) {
        List<String> alerts = new ArrayList<>();

        if (billing.getAnswersBlocked()) {
            alerts.add("Answer generation is blocked. Upgrade your plan to continue.");
        }

        if (metrics.getAnswersPercentageUsed() > 80) {
            alerts.add("You've used " + metrics.getAnswersPercentageUsed() +
                    "% of your answer limit.");
        }

        if (billing.getCancelAtPeriodEnd()) {
            alerts.add("Your subscription will cancel on " + billing.getPeriodEnd());
        }

        if (billing.getPaymentFailureDate() != null) {
            alerts.add("Payment failed. Please update your payment method.");
        }

        return alerts;
    }

    private Double calculatePercentage(Integer used, Integer limit) {
        if (limit == null || limit == 0) return 0.0;
        return (used.doubleValue() / limit.doubleValue()) * 100;
    }

    private InvoiceDto convertToInvoiceDto(Invoice invoice) {
        return InvoiceDto.builder()
                .stripeInvoiceId(invoice.getId())
                .invoiceNumber(invoice.getNumber())
                .status(invoice.getStatus())
                .amountDue((int)(long) invoice.getAmountDue())
                .amountPaid((int)(long) invoice.getAmountPaid())
                .subtotal((int)(long) invoice.getSubtotal())
                .total((int)(long) invoice.getTotal())
                .currency(invoice.getCurrency())
                .invoiceDate(LocalDateTime.ofEpochSecond(
                        invoice.getCreated(), 0, java.time.ZoneOffset.UTC))
                .hostedInvoiceUrl(invoice.getHostedInvoiceUrl())
                .invoicePdfUrl(invoice.getInvoicePdf())
                .build();
    }

    private PaymentMethodDto convertToPaymentMethodDto(PaymentMethod pm) {
        PaymentMethod.Card card = pm.getCard();

        return PaymentMethodDto.builder()
                .stripePaymentMethodId(pm.getId())
                .type(pm.getType())
                .cardBrand(card.getBrand())
                .cardLast4(card.getLast4())
                .cardExpMonth(card.getExpMonth().intValue())
                .cardExpYear(card.getExpYear().intValue())
                .build();
    }

    private EntitlementHistoryDto convertToEntitlementHistoryDto(BillingEntitlementHistory history) {
        return EntitlementHistoryDto.builder()
                .id(history.getId())
                .changeType(history.getChangeType())
                .oldPlanCode(history.getOldPlanCode())
                .newPlanCode(history.getNewPlanCode())
                .oldAddonCodes(history.getOldAddonCodes())
                .newAddonCodes(history.getNewAddonCodes())
                .oldAnswersLimit(history.getOldAnswersLimit())
                .newAnswersLimit(history.getNewAnswersLimit())
                .oldKbPagesLimit(history.getOldKbPagesLimit())
                .newKbPagesLimit(history.getNewKbPagesLimit())
                .oldAgentsLimit(history.getOldAgentsLimit())
                .newAgentsLimit(history.getNewAgentsLimit())
                .oldUsersLimit(history.getOldUsersLimit())
                .newUsersLimit(history.getNewUsersLimit())
                .triggeredBy(history.getTriggeredBy())
                .stripeEventId(history.getStripeEventId())
                .effectiveDate(history.getEffectiveDate())
                .createdAt(history.getCreatedAt())
                .build();
    }

    @Override
    public UpcomingInvoiceDto getUpcomingInvoice(Long companyId) {
        log.info("Fetching upcoming invoice for company: {}", companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            throw new RuntimeException("No active subscription found");
        }

        try {
            com.stripe.param.InvoiceUpcomingParams params =
                    com.stripe.param.InvoiceUpcomingParams.builder()
                            .setCustomer(billing.getStripeCustomerId())
                            .setSubscription(billing.getStripeSubscriptionId())
                            .build();

            com.stripe.model.Invoice upcomingInvoice = com.stripe.model.Invoice.upcoming(params);

            return UpcomingInvoiceDto.builder()
                    .amountDue(upcomingInvoice.getAmountDue() != null ?
                            upcomingInvoice.getAmountDue().intValue() : 0)
                    .currency(upcomingInvoice.getCurrency())
                    .periodStart(LocalDateTime.ofEpochSecond(
                            upcomingInvoice.getPeriodStart(), 0, java.time.ZoneOffset.UTC))
                    .periodEnd(LocalDateTime.ofEpochSecond(
                            upcomingInvoice.getPeriodEnd(), 0, java.time.ZoneOffset.UTC))
                    .build();

        } catch (com.stripe.exception.StripeException e) {
            log.error("Failed to fetch upcoming invoice for company {}", companyId, e);
            throw new RuntimeException("Failed to fetch upcoming invoice: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadInvoicePdf(Long companyId, String invoiceId) {
        log.info("Downloading invoice PDF {} for company {}", invoiceId, companyId);

        CompanyBilling billing = companyBillingService.getCompanyBilling(companyId);

        try {
            com.stripe.model.Invoice invoice = com.stripe.model.Invoice.retrieve(invoiceId);

            // Verify invoice belongs to this customer
            if (!invoice.getCustomer().equals(billing.getStripeCustomerId())) {
                throw new RuntimeException("Invoice does not belong to this company");
            }

            // Get PDF URL and download
            String pdfUrl = invoice.getInvoicePdf();
            if (pdfUrl == null) {
                throw new RuntimeException("PDF not available for this invoice");
            }

            // Download PDF bytes
            java.net.URL url = java.net.URI.create(pdfUrl).toURL();
            try (java.io.InputStream in = url.openStream()) {
                return in.readAllBytes();
            }

        } catch (Exception e) {
            log.error("Failed to download invoice PDF for company {}", companyId, e);
            throw new RuntimeException("Failed to download invoice PDF: " + e.getMessage());
        }
    }

}