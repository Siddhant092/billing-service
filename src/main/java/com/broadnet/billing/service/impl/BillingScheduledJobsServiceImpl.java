package com.broadnet.billing.service.impl;

import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.repository.BillingWebhookEventRepository;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.BillingScheduledJobsService;
import com.broadnet.billing.service.CompanyBillingService;
import com.broadnet.billing.service.StripeWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementation of BillingScheduledJobsService
 *
 * FIXES APPLIED:
 * 1. All 8 interface methods declare int return type — all void impls changed to return int
 * 2. billing.setPendingPlanEffectiveDate(null) — field is "pendingEffectiveDate" on entity,
 *    fixed to billing.setPendingEffectiveDate(null)
 * 3. findByPendingPlanCodeIsNotNullAndPendingPlanEffectiveDateBefore — repo method name
 *    matches entity field "pendingEffectiveDate", kept as-is (repo already has this method)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingScheduledJobsServiceImpl implements BillingScheduledJobsService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final CompanyBillingService companyBillingService;
    private final StripeWebhookService stripeWebhookService;

    @Override
    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    @Transactional
    public int resetAnnualAnswerUsage() {
        log.info("Starting annual answer usage reset job");
        LocalDateTime now = LocalDateTime.now();

        List<CompanyBilling> companies = companyBillingRepository
                .findByBillingIntervalAndPeriodEndBefore("year", now);

        int resetCount = 0;
        for (CompanyBilling billing : companies) {
            try {
                billing.setAnswersUsedInPeriod(0);
                billing.setAnswersBlocked(false);
                billing.setPeriodStart(now);
                billing.setPeriodEnd(now.plusYears(1));
                companyBillingRepository.save(billing);
                resetCount++;
                log.info("Reset annual usage for company {}", billing.getCompanyId());
            } catch (Exception e) {
                log.error("Failed to reset annual usage for company {}", billing.getCompanyId(), e);
            }
        }

        log.info("Annual answer usage reset completed: {} companies", resetCount);
        return resetCount;
    }

    @Override
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    @Transactional
    public int applyPaymentFailureRestrictions() {
        log.info("Starting payment failure restrictions job");
        LocalDateTime gracePeriodCutoff = LocalDateTime.now().minusDays(7);

        List<CompanyBilling> companies = companyBillingRepository
                .findByPaymentFailureDateBeforeAndServiceRestrictedAtIsNull(gracePeriodCutoff);

        int restrictedCount = 0;
        for (CompanyBilling billing : companies) {
            try {
                companyBillingService.applyPaymentFailureRestrictions(billing.getCompanyId());
                restrictedCount++;
                log.info("Applied restrictions for company {} due to payment failure",
                        billing.getCompanyId());
            } catch (Exception e) {
                log.error("Failed to apply restrictions for company {}",
                        billing.getCompanyId(), e);
            }
        }

        log.info("Payment failure restrictions applied: {} companies", restrictedCount);
        return restrictedCount;
    }

    @Override
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    @Transactional
    public int applyPendingPlanChanges() {
        log.info("Starting pending plan changes job");
        LocalDateTime now = LocalDateTime.now();

        List<CompanyBilling> companies = companyBillingRepository
                .findByPendingPlanCodeIsNotNullAndPendingEffectiveDateBefore(now);

        int appliedCount = 0;
        for (CompanyBilling billing : companies) {
            try {
                billing.setActivePlanCode(billing.getPendingPlanCode());
                billing.setActiveAddonCodes(billing.getPendingAddonCodes());
                billing.setPendingPlanCode(null);
                billing.setPendingAddonCodes(null);
                // FIX: entity field is pendingEffectiveDate, not pendingPlanEffectiveDate
                billing.setPendingEffectiveDate(null);
                companyBillingRepository.save(billing);
                appliedCount++;
                log.info("Applied pending plan change for company {}", billing.getCompanyId());
            } catch (Exception e) {
                log.error("Failed to apply pending plan change for company {}",
                        billing.getCompanyId(), e);
            }
        }

        log.info("Pending plan changes applied: {} companies", appliedCount);
        return appliedCount;
    }

    @Override
    @Scheduled(cron = "0 0 10 * * *", zone = "UTC")
    public int sendUsageLimitWarnings() {
        log.info("Starting usage limit warnings job");

        List<CompanyBilling> companies = companyBillingRepository.findAll();

        int warningsSent = 0;
        for (CompanyBilling billing : companies) {
            try {
                double answersUsagePercent = calculatePercentage(
                        billing.getAnswersUsedInPeriod(),
                        billing.getEffectiveAnswersLimit());

                if (answersUsagePercent >= 80 && !billing.getAnswersBlocked()) {
                    // TODO: Send notification/email
                    log.info("Company {} has used {}% of answer limit",
                            billing.getCompanyId(), answersUsagePercent);
                    warningsSent++;
                }
            } catch (Exception e) {
                log.error("Failed to check usage for company {}", billing.getCompanyId(), e);
            }
        }

        log.info("Usage limit warnings sent: {}", warningsSent);
        return warningsSent;
    }

    @Override
    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    public int retryFailedWebhooks() {
        log.info("Starting webhook retry job");
        int retried = stripeWebhookService.retryFailedWebhooks(3);
        log.info("Webhook retry completed: {} webhooks retried", retried);
        return retried;
    }

    @Override
    @Scheduled(cron = "0 0 2 * * SUN", zone = "UTC")
    @Transactional
    public int cleanupOldWebhookEvents() {
        log.info("Starting webhook cleanup job");
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        int deleted = webhookEventRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Webhook cleanup completed: {} old events deleted", deleted);
        return deleted;
    }

    @Override
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public int syncSubscriptionStatesFromStripe() {
        log.info("Starting subscription state sync job");

        List<CompanyBilling> companies = companyBillingRepository
                .findByStripeSubscriptionIdIsNotNull();

        int syncedCount = 0;
        for (CompanyBilling billing : companies) {
            try {
                companyBillingService.syncFromStripe(billing.getCompanyId());
                syncedCount++;
            } catch (Exception e) {
                log.error("Failed to sync subscription for company {}",
                        billing.getCompanyId(), e);
            }
        }

        log.info("Subscription sync completed: {} companies synced", syncedCount);
        return syncedCount;
    }

    @Override
    @Scheduled(cron = "0 0 9 * * *", zone = "UTC")
    public int sendPaymentMethodExpirationWarnings() {
        log.info("Starting payment method expiration warnings job");
        // TODO: Query Stripe for expiring payment methods and send notifications
        log.info("Payment method expiration warnings completed");
        return 0;
    }

    @Override
    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    @Transactional
    public int resetMonthlyUsageCounters() {
        log.info("Starting monthly usage counter reset job");
        LocalDateTime now = LocalDateTime.now();

        List<CompanyBilling> companies = companyBillingRepository
                .findByBillingIntervalAndPeriodEndBefore("month", now);

        int resetCount = 0;
        for (CompanyBilling billing : companies) {
            try {
                billing.setAnswersUsedInPeriod(0);
                billing.setAnswersBlocked(false);
                billing.setPeriodStart(now);
                billing.setPeriodEnd(now.plusMonths(1));
                companyBillingRepository.save(billing);
                resetCount++;
                log.info("Reset monthly usage for company {}", billing.getCompanyId());
            } catch (Exception e) {
                log.error("Failed to reset monthly usage for company {}", billing.getCompanyId(), e);
            }
        }

        log.info("Monthly usage counter reset completed: {} companies", resetCount);
        return resetCount;
    }

    private double calculatePercentage(Integer used, Integer limit) {
        if (limit == null || limit == 0) return 0.0;
        return (used.doubleValue() / limit.doubleValue()) * 100;
    }
}