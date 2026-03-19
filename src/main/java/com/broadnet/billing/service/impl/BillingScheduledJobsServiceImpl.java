package com.broadnet.billing.service.impl;

import com.broadnet.billing.entity.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.*;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * All cron schedules run in UTC per architecture plan.
 * Each job is also callable programmatically (for testing / manual trigger).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingScheduledJobsServiceImpl implements BillingScheduledJobsService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final BillingPaymentMethodRepository paymentMethodRepository;
    private final BillingNotificationsRepository notificationsRepository;
    private final BillingUsageLogRepository usageLogRepository;
    private final EntitlementService entitlementService;
    private final BillingNotificationService notificationService;
    private final StripeWebhookService stripeWebhookService;
    private final BillingEnterpriseUsageService enterpriseUsageService;
    private final CompanyBillingService companyBillingService;

    // -------------------------------------------------------------------------
    // Annual answer reset — daily 00:05 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    @Transactional
    public int resetAnnualAnswerUsage() {
        LocalDateTime today = LocalDateTime.now();
        int dayOfMonth = today.getDayOfMonth();

        List<CompanyBilling> billings = companyBillingRepository
                .findByBillingIntervalAndAnswersResetDay(
                        CompanyBilling.BillingInterval.year, dayOfMonth);

        int count = 0;
        for (CompanyBilling billing : billings) {
            // Guard: skip if already reset this month
            if (billing.getAnswersPeriodStart() != null
                    && billing.getAnswersPeriodStart().getMonth() == today.getMonth()
                    && billing.getAnswersPeriodStart().getYear() == today.getYear()) {
                continue;
            }

            int before = billing.getAnswersUsedInPeriod();
            companyBillingRepository.resetPeriodUsage(billing.getCompanyId(), today);

            // Log the reset
            usageLogRepository.save(BillingUsageLog.builder()
                    .companyId(billing.getCompanyId())
                    .usageType(BillingUsageLog.UsageType.answer)
                    .usageCount(0)
                    .beforeCount(before)
                    .afterCount(0)
                    .metadata(java.util.Map.of("reset_type", "annual_monthly",
                            "reset_date", today.toLocalDate().toString()))
                    .build());
            count++;
        }

        log.info("[Cron] resetAnnualAnswerUsage: reset {} companies", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Payment failure restrictions — daily 01:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    @Transactional
    public int applyPaymentFailureRestrictions() {
        // Architecture Plan: restrict after 7 days of payment failure
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        List<CompanyBilling> billings = companyBillingRepository
                .findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNullAndSubscriptionStatusIn(
                        cutoff,
                        List.of(CompanyBilling.SubscriptionStatus.past_due,
                                CompanyBilling.SubscriptionStatus.unpaid));

        int count = 0;
        for (CompanyBilling billing : billings) {
            billing.setServiceRestrictedAt(LocalDateTime.now());
            billing.setRestrictionReason(CompanyBilling.RestrictionReason.payment_failed);
            billing.setAnswersBlocked(true);
            companyBillingRepository.save(billing);

            // Notify — subscription_inactive type
            notificationService.createNotification(
                    billing.getCompanyId(),
                    BillingNotification.NotificationType.subscription_inactive,
                    "Service Restricted",
                    "Your service has been restricted due to unpaid invoices. "
                            + "Please update your payment method to restore access.",
                    BillingNotification.Severity.error);
            count++;
        }

        log.info("[Cron] applyPaymentFailureRestrictions: restricted {} companies", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Pending plan changes — hourly
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public int applyPendingPlanChanges() {
        List<CompanyBilling> billings = companyBillingRepository
                .findByPendingPlanCodeIsNotNullAndPendingEffectiveDateBefore(LocalDateTime.now());

        int count = 0;
        for (CompanyBilling billing : billings) {
            try {
                String oldPlanCode = billing.getActivePlanCode();
                String newPlanCode = billing.getPendingPlanCode();

                // Promote pending plan to active
                if (billing.getPendingPlan() != null) {
                    billing.setActivePlan(billing.getPendingPlan());
                }
                billing.setActivePlanCode(newPlanCode);
                if (billing.getPendingAddonCodes() != null) {
                    billing.setActiveAddonCodes(billing.getPendingAddonCodes());
                }

                // Clear pending fields
                billing.setPendingPlan(null);
                billing.setPendingPlanCode(null);
                billing.setPendingAddonCodes(null);
                billing.setPendingEffectiveDate(null);
                billing.setStripeScheduleId(null);
                companyBillingRepository.save(billing);

                // Recompute entitlements
                String interval = billing.getBillingInterval() != null
                        ? billing.getBillingInterval().name() : "month";
                List<String> addons = billing.getActiveAddonCodes() != null
                        ? billing.getActiveAddonCodes() : List.of();
                com.broadnet.billing.dto.EntitlementsDto ent =
                        entitlementService.computeEntitlements(newPlanCode, addons, interval);
                entitlementService.updateCompanyEntitlements(billing.getCompanyId(), ent,
                        BillingEntitlementHistory.TriggeredBy.admin, null);

                notificationService.notifyPlanChanged(billing.getCompanyId(),
                        oldPlanCode, newPlanCode, null);
                count++;
            } catch (Exception e) {
                log.error("[Cron] Failed to apply pending plan for companyId={}: {}",
                        billing.getCompanyId(), e.getMessage());
            }
        }

        log.info("[Cron] applyPendingPlanChanges: applied {} plan changes", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Usage limit warnings — daily 10:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 10 * * *", zone = "UTC")
    @Transactional
    public int sendUsageLimitWarnings() {
        // Find companies at >= 80% answer usage
        List<CompanyBilling> approaching =
                companyBillingRepository.findApproachingAnswerLimit(0.8);

        int count = 0;
        for (CompanyBilling billing : approaching) {
            int used  = billing.getAnswersUsedInPeriod();
            int limit = billing.getEffectiveAnswersLimit();
            if (limit <= 0) continue;
            double pct = (used * 100.0) / limit;

            notificationService.notifyLimitWarning(
                    billing.getCompanyId(),
                    BillingUsageLog.UsageType.answer,
                    pct, used, limit, billing.getPeriodEnd());
            count++;
        }

        log.info("[Cron] sendUsageLimitWarnings: sent {} warnings", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Webhook retry — every 15 minutes
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    @Transactional
    public int retryFailedWebhooks() {
        int retried = stripeWebhookService.retryFailedWebhooks(3);
        if (retried > 0) {
            log.info("[Cron] retryFailedWebhooks: retried {} events", retried);
        }
        return retried;
    }

    // -------------------------------------------------------------------------
    // Webhook cleanup — weekly Sunday 02:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    @Transactional
    public int cleanupOldWebhookEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = webhookEventRepository.deleteByCreatedAtBefore(cutoff);
        log.info("[Cron] cleanupOldWebhookEvents: deleted {} events older than 90 days", deleted);
        return deleted;
    }

    // -------------------------------------------------------------------------
    // Stripe sync — daily 03:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public int syncSubscriptionStatesFromStripe() {
        List<CompanyBilling> billings =
                companyBillingRepository.findByStripeSubscriptionIdIsNotNull();

        int synced = 0;
        for (CompanyBilling billing : billings) {
            try {
                companyBillingService.syncFromStripe(billing.getCompanyId());
                synced++;
            } catch (Exception e) {
                log.warn("[Cron] Failed to sync companyId={}: {}",
                        billing.getCompanyId(), e.getMessage());
            }
        }
        log.info("[Cron] syncSubscriptionStatesFromStripe: synced {} companies", synced);
        return synced;
    }

    // -------------------------------------------------------------------------
    // Payment method expiry warnings — daily 09:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    @Transactional
    public int sendPaymentMethodExpirationWarnings() {
        LocalDateTime now = LocalDateTime.now();
        // Warn for cards expiring within next 30 days
        List<BillingPaymentMethod> expiring =
                paymentMethodRepository.findExpiringInMonth(
                        now.plusDays(30).getYear(),
                        now.plusDays(30).getMonthValue());

        int count = 0;
        for (BillingPaymentMethod pm : expiring) {
            if (Boolean.TRUE.equals(pm.getIsExpired())) continue;
            LocalDateTime expiry = LocalDateTime.of(
                            pm.getCardExpYear(), pm.getCardExpMonth(), 1, 0, 0)
                    .plusMonths(1).minusDays(1);
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(now, expiry);
            if (daysLeft > 0 && daysLeft <= 30) {
                notificationService.notifyPaymentMethodExpiring(
                        pm.getCompanyId(), (int) daysLeft,
                        pm.getCardLast4() != null ? pm.getCardLast4() : "****");
                count++;
            }
        }
        log.info("[Cron] sendPaymentMethodExpirationWarnings: sent {} warnings", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Mark expired payment methods — daily midnight UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @Transactional
    public int checkAndExpirePaymentMethods() {
        LocalDateTime now = LocalDateTime.now();
        List<BillingPaymentMethod> expired = paymentMethodRepository
                .findActuallyExpired(now.getYear(), now.getMonthValue());

        int count = 0;
        for (BillingPaymentMethod pm : expired) {
            paymentMethodRepository.markAsExpired(pm.getStripePaymentMethodId());
            count++;
        }
        log.info("[Cron] checkAndExpirePaymentMethods: marked {} cards expired", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Monthly usage counter reset — daily 00:10 UTC (safety net)
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    @Transactional
    public int resetMonthlyUsageCounters() {
        List<CompanyBilling> billings = companyBillingRepository
                .findByBillingIntervalAndPeriodEndBefore(
                        CompanyBilling.BillingInterval.month, LocalDateTime.now());

        int count = 0;
        for (CompanyBilling billing : billings) {
            if (billing.getSubscriptionStatus() != CompanyBilling.SubscriptionStatus.active) continue;
            companyBillingRepository.resetPeriodUsage(
                    billing.getCompanyId(), LocalDateTime.now());
            count++;
        }
        log.info("[Cron] resetMonthlyUsageCounters: reset {} companies", count);
        return count;
    }

    // -------------------------------------------------------------------------
    // Enterprise billing calculation — 1st of month 00:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 0 1 * *", zone = "UTC")
    @Transactional
    public int calculateEnterpriseBillings() {
        int calculated = enterpriseUsageService.calculateAllDueBillings();
        log.info("[Cron] calculateEnterpriseBillings: calculated {} records", calculated);
        return calculated;
    }

    // -------------------------------------------------------------------------
    // Enterprise invoice generation — 1st of month 00:10 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 10 0 1 * *", zone = "UTC")
    @Transactional
    public int generateEnterpriseInvoices() {
        int generated = enterpriseUsageService.createAllInvoices();
        log.info("[Cron] generateEnterpriseInvoices: generated {} invoices", generated);
        return generated;
    }

    // -------------------------------------------------------------------------
    // Notification cleanup — daily 04:00 UTC
    // -------------------------------------------------------------------------

    @Override
    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    @Transactional
    public int cleanupExpiredNotifications() {
        int deleted = notificationsRepository.deleteExpired(LocalDateTime.now());
        log.info("[Cron] cleanupExpiredNotifications: deleted {} expired notifications", deleted);
        return deleted;
    }
}