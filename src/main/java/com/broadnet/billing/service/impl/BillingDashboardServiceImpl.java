package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.exception.StripeIntegrationException;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.param.InvoiceUpcomingParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingDashboardServiceImpl implements BillingDashboardService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final BillingEntitlementHistoryRepository entitlementHistoryRepository;
    private final BillingInvoiceService invoiceService;
    private final BillingNotificationService notificationService;
    private final PlanManagementService planManagementService;
    private final SubscriptionManagementService subscriptionManagementService;
    private final UsageAnalyticsService usageAnalyticsService;

    // -------------------------------------------------------------------------
    // §1.3 Billing Snapshot
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BillingSnapshotDto getBillingSnapshot(Long companyId) {
        CompanyBilling billing = getBilling(companyId);

        // 1. Latest invoice from local DB
        InvoiceDto latestInvoice = invoiceService.getLatestInvoice(companyId);

        // 2. Upcoming invoice from Stripe API
        UpcomingInvoiceDto upcoming = getUpcomingInvoice(companyId);

        // 3. Default payment method
        PaymentMethodDto paymentMethod = paymentMethodRepository
                .findByCompanyIdAndIsDefaultTrue(companyId)
                .map(this::toPaymentMethodDto)
                .orElse(null);

        return BillingSnapshotDto.builder()
                .latestInvoice(latestInvoice)
                .nextInvoice(upcoming)
                .paymentMethod(paymentMethod)
                .build();
    }

    // -------------------------------------------------------------------------
    // §1.4 Usage Metrics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageMetricsDto getUsageMetrics(Long companyId) {
        CompanyBilling b = getBilling(companyId);

        return UsageMetricsDto.builder()
                .answers(buildMetric(
                        b.getAnswersUsedInPeriod(), b.getEffectiveAnswersLimit(),
                        b.getAnswersBlocked(), b.getPeriodEnd()))
                .kbPages(buildMetric(
                        b.getKbPagesTotal(), b.getEffectiveKbPagesLimit(), false, null))
                .agents(buildMetric(
                        b.getAgentsTotal(), b.getEffectiveAgentsLimit(), false, null))
                .users(buildMetric(
                        b.getUsersTotal(), b.getEffectiveUsersLimit(), false, null))
                .build();
    }

    // -------------------------------------------------------------------------
    // §1.2 Current Plan
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CurrentPlanDto getCurrentPlan(Long companyId) {
        CompanyBilling b = getBilling(companyId);

        CurrentPlanDto.PendingChangeDto pendingChange = null;
        if (b.getPendingPlanCode() != null) {
            pendingChange = CurrentPlanDto.PendingChangeDto.builder()
                    .pendingPlanCode(b.getPendingPlanCode())
                    .effectiveDate(b.getPendingEffectiveDate())
                    .build();
        }

        // Fetch plan name from plan catalog
        String planName = null;
        if (b.getActivePlanCode() != null) {
            try {
                planName = planManagementService.getPlanByCode(b.getActivePlanCode()).getPlanName();
            } catch (ResourceNotFoundException e) {
                log.warn("Plan {} not found for dashboard", b.getActivePlanCode());
            }
        }

        return CurrentPlanDto.builder()
                .planCode(b.getActivePlanCode())
                .planName(planName)
                .billingInterval(b.getBillingInterval())
                .billingCycle(b.getBillingInterval() != null
                        ? (b.getBillingInterval() == CompanyBilling.BillingInterval.month
                           ? "Monthly" : "Annual") : null)
                .renewalDate(b.getPeriodEnd())
                .status(b.getSubscriptionStatus())
                .cancelAtPeriodEnd(b.getCancelAtPeriodEnd())
                .cancelAt(b.getCancelAt())
                .periodStart(b.getPeriodStart())
                .periodEnd(b.getPeriodEnd())
                .answersPerPeriod(b.getEffectiveAnswersLimit())
                .kbPages(b.getEffectiveKbPagesLimit())
                .agents(b.getEffectiveAgentsLimit())
                .users(b.getEffectiveUsersLimit())
                .pendingChange(pendingChange)
                .build();
    }

    // -------------------------------------------------------------------------
    // §2.1 Available Plans
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PlanDto> getAvailablePlans(Long companyId,
                                            BillingPlanLimit.BillingInterval billingInterval) {
        return subscriptionManagementService
                .getAvailablePlans(companyId, billingInterval)
                .getPlans();
    }

    // -------------------------------------------------------------------------
    // §1.5 Available Boosts
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<AddonDto> getAvailableBoosts(Long companyId,
                                              BillingPlanLimit.BillingInterval billingInterval) {
        return planManagementService.getAvailableBoosts(companyId, billingInterval);
    }

    // -------------------------------------------------------------------------
    // §1.6 Overview (aggregator)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BillingOverviewDto getOverview(Long companyId,
                                           BillingPlanLimit.BillingInterval billingInterval) {
        // Aggregate all dashboard data in one call
        List<NotificationDto> notifications = getNotifications(companyId, true, null, 20);
        Long unreadCount = (long) notifications.size();
        CurrentPlanDto currentPlan = getCurrentPlan(companyId);
        BillingSnapshotDto snapshot = getBillingSnapshot(companyId);
        UsageMetricsDto usageMetrics = getUsageMetrics(companyId);
        List<AddonDto> boosts = getAvailableBoosts(companyId, billingInterval);

        return BillingOverviewDto.builder()
                .notifications(notifications)
                .unreadCount(unreadCount)
                .currentPlan(currentPlan)
                .billingSnapshot(snapshot)
                .usageMetrics(usageMetrics)
                .availableBoosts(boosts)
                .build();
    }

    // -------------------------------------------------------------------------
    // §3 Invoices
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoices(Long companyId, int page, int size) {
        return invoiceService.getInvoicesByCompanyId(companyId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public UpcomingInvoiceDto getUpcomingInvoice(Long companyId) {
        CompanyBilling billing = getBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            return null;
        }

        try {
            Invoice upcoming = Invoice.upcoming(
                    InvoiceUpcomingParams.builder()
                            .setSubscription(billing.getStripeSubscriptionId())
                            .build());

            return UpcomingInvoiceDto.builder()
                    .amount(upcoming.getAmountDue() != null
                            ? upcoming.getAmountDue().intValue() : 0)
                    .amountFormatted(formatCents(upcoming.getAmountDue() != null
                            ? upcoming.getAmountDue().intValue() : 0))
                    .subtotal(upcoming.getSubtotal() != null
                            ? upcoming.getSubtotal().intValue() : 0)
                    .subtotalFormatted(formatCents(upcoming.getSubtotal() != null
                            ? upcoming.getSubtotal().intValue() : 0))
                    .taxAmount(upcoming.getTax() != null
                            ? upcoming.getTax().intValue() : 0)
                    .taxAmountFormatted(formatCents(upcoming.getTax() != null
                            ? upcoming.getTax().intValue() : 0))
                    .invoiceDate(upcoming.getPeriodEnd() != null
                            ? epochToLdt(upcoming.getPeriodEnd()) : null)
                    .currency(upcoming.getCurrency() != null ? upcoming.getCurrency() : "usd")
                    .build();

        } catch (StripeException e) {
            log.warn("Could not fetch upcoming invoice for companyId={}: {}", companyId, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePdf(Long companyId, String invoiceId) {
        return invoiceService.downloadInvoicePdf(companyId, invoiceId);
    }

    // -------------------------------------------------------------------------
    // Payment Methods
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PaymentMethodDto> getPaymentMethods(Long companyId) {
        return paymentMethodRepository.findByCompanyId(companyId)
                .stream().map(this::toPaymentMethodDto)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // §1.1 Notifications
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long companyId, boolean unreadOnly,
                                                    BillingNotification.NotificationType type,
                                                    int limit) {
        List<NotificationDto> results;

        if (unreadOnly) {
            results = notificationService.getUnreadNotifications(companyId);
        } else {
            results = notificationService
                    .getNotifications(companyId, 0, limit)
                    .getContent();
        }

        // Apply type filter if provided
        if (type != null) {
            results = results.stream()
                    .filter(n -> n.getType() == type)
                    .collect(Collectors.toList());
        }

        // Apply limit
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }

        return results;
    }

    @Override
    @Transactional
    public void markNotificationAsRead(Long notificationId) {
        notificationService.markAsRead(notificationId);
    }

    @Override
    @Transactional
    public int markAllNotificationsAsRead(Long companyId) {
        return notificationService.markAllAsRead(companyId);
    }

    @Override
    @Transactional
    public void dismissNotification(Long notificationId) {
        notificationService.deleteNotification(notificationId);
    }

    // -------------------------------------------------------------------------
    // §4 Usage History
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageHistoryDto getUsageHistory(Long companyId, int days) {
        Map<String, Integer> dailyAnswers =
                usageAnalyticsService.getDailyAnswerUsage(companyId, days);

        int totalAnswers = dailyAnswers.values().stream()
                .mapToInt(Integer::intValue).sum();

        return UsageHistoryDto.builder()
                .dailyAnswers(dailyAnswers)
                .days(days)
                .totalAnswers(totalAnswers)
                .build();
    }

    // -------------------------------------------------------------------------
    // Entitlement History
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<EntitlementHistoryDto> getEntitlementHistory(Long companyId, int page, int size) {
        return entitlementHistoryRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size))
                .map(this::toHistoryDto);
    }

    // -------------------------------------------------------------------------
    // Converters
    // -------------------------------------------------------------------------

    private PaymentMethodDto toPaymentMethodDto(BillingPaymentMethod pm) {
        boolean expiringSoon = false;
        if (pm.getCardExpYear() != null && pm.getCardExpMonth() != null
                && !Boolean.TRUE.equals(pm.getIsExpired())) {
            LocalDateTime expiry = LocalDateTime.of(
                    pm.getCardExpYear(), pm.getCardExpMonth(), 1, 0, 0)
                    .plusMonths(1).minusDays(1);
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDateTime.now(), expiry);
            expiringSoon = daysLeft <= 30 && daysLeft > 0;
        }

        return PaymentMethodDto.builder()
                .id(pm.getId())
                .stripePaymentMethodId(pm.getStripePaymentMethodId())
                .type(pm.getType())
                .isDefault(pm.getIsDefault())
                .cardBrand(pm.getCardBrand())
                .cardLast4(pm.getCardLast4())
                .cardExpMonth(pm.getCardExpMonth())
                .cardExpYear(pm.getCardExpYear())
                .isExpired(pm.getIsExpired())
                .isExpiringSoon(expiringSoon)
                .build();
    }

    private EntitlementHistoryDto toHistoryDto(BillingEntitlementHistory h) {
        return EntitlementHistoryDto.builder()
                .id(h.getId())
                .companyId(h.getCompanyId())
                .changeType(h.getChangeType())
                .oldPlanCode(h.getOldPlanCode())
                .newPlanCode(h.getNewPlanCode())
                .oldAddonCodes(h.getOldAddonCodes())
                .newAddonCodes(h.getNewAddonCodes())
                .oldAnswersLimit(h.getOldAnswersLimit())
                .newAnswersLimit(h.getNewAnswersLimit())
                .oldKbPagesLimit(h.getOldKbPagesLimit())
                .newKbPagesLimit(h.getNewKbPagesLimit())
                .oldAgentsLimit(h.getOldAgentsLimit())
                .newAgentsLimit(h.getNewAgentsLimit())
                .oldUsersLimit(h.getOldUsersLimit())
                .newUsersLimit(h.getNewUsersLimit())
                .triggeredBy(h.getTriggeredBy())
                .stripeEventId(h.getStripeEventId())
                .effectiveDate(h.getEffectiveDate())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private UsageMetricsDto.MetricDto buildMetric(Integer used, Integer limit,
                                                    Boolean blocked, LocalDateTime resetDate) {
        int u = used != null ? used : 0;
        int l = limit != null ? limit : 0;
        int remaining = Math.max(0, l - u);
        double pct = l > 0 ? (u * 100.0 / l) : 0.0;
        return UsageMetricsDto.MetricDto.builder()
                .used(u).limit(l).remaining(remaining)
                .percentage(pct)
                .isBlocked(Boolean.TRUE.equals(blocked))
                .resetDate(resetDate)
                .build();
    }

    private CompanyBilling getBilling(Long companyId) {
        return companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "companyId", companyId));
    }

    private LocalDateTime epochToLdt(Long epoch) {
        if (epoch == null) return null;
        return Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private String formatCents(Integer cents) {
        if (cents == null) return "$0.00";
        return String.format("$%.2f", cents / 100.0);
    }
}
