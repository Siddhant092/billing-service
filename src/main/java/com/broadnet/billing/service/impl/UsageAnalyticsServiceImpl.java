package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.CompanyUsageSummaryDto;
import com.broadnet.billing.dto.UsageStatsDto;
import com.broadnet.billing.entity.BillingUsageLog;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.repository.BillingUsageLogRepository;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.UsageAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of UsageAnalyticsService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageAnalyticsServiceImpl implements UsageAnalyticsService {

    private final BillingUsageLogRepository usageLogRepository;
    private final CompanyBillingRepository companyBillingRepository;

    @Override
    public UsageStatsDto getUsageStats(Long companyId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Fetching usage stats for company {} from {} to {}",
                companyId, startDate, endDate);

        List<BillingUsageLog> logs = usageLogRepository.findByCompanyIdAndCreatedAtBetween(
                companyId, startDate, endDate);

        long totalAnswers = logs.stream()
                .filter(log -> "answer".equals(log.getUsageType()))
                .filter(log -> !log.getWasBlocked())
                .count();

        long blockedAnswers = logs.stream()
                .filter(log -> "answer".equals(log.getUsageType()))
                .filter(BillingUsageLog::getWasBlocked)
                .count();

        long kbPagesAdded = logs.stream()
                .filter(log -> "kb_page_added".equals(log.getUsageType()))
                .count();

        long kbPagesDeleted = logs.stream()
                .filter(log -> "kb_page_deleted".equals(log.getUsageType()))
                .count();

        long agentsCreated = logs.stream()
                .filter(log -> "agent_created".equals(log.getUsageType()))
                .count();

        long usersAdded = logs.stream()
                .filter(log -> "user_created".equals(log.getUsageType()))
                .count();

        return UsageStatsDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalAnswersGenerated(totalAnswers)
                .blockedAnswerAttempts(blockedAnswers)
                .kbPagesAdded(kbPagesAdded)
                .kbPagesDeleted(kbPagesDeleted)
                .agentsCreated(agentsCreated)
                .usersAdded(usersAdded)
                .totalUsageEvents((long) logs.size())
                .totalBlockedAttempts(blockedAnswers)
                .build();
    }

    @Override
    public Map<String, Integer> getDailyAnswerUsage(Long companyId, int days) {
        log.info("Fetching daily answer usage for company {} ({} days)", companyId, days);

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        List<BillingUsageLog> logs = usageLogRepository.findByCompanyIdAndCreatedAtBetween(
                companyId, startDate, LocalDateTime.now());

        Map<String, Integer> dailyUsage = new HashMap<>();

        logs.stream()
                .filter(log -> "answer".equals(log.getUsageType()))
                .filter(log -> !log.getWasBlocked())
                .forEach(log -> {
                    String dateKey = log.getCreatedAt().toLocalDate().toString();
                    dailyUsage.merge(dateKey, 1, Integer::sum);
                });

        return dailyUsage;
    }

    @Override
    public Page<CompanyUsageSummaryDto> getCompanyUsageSummary(int page, int size) {
        log.info("Fetching company usage summary (page: {}, size: {})", page, size);

        Page<CompanyBilling> billingPage = companyBillingRepository
                .findAll(PageRequest.of(page, size));

        List<CompanyUsageSummaryDto> summaries = billingPage.getContent().stream()
                .map(this::convertToUsageSummary)
                .collect(Collectors.toList());

        return new PageImpl<>(summaries, PageRequest.of(page, size),
                billingPage.getTotalElements());
    }

    @Override
    public List<CompanyUsageSummaryDto> getCompaniesApproachingLimits(Double threshold) {
        log.info("Fetching companies approaching limits (threshold: {})", threshold);

        List<CompanyBilling> allCompanies = companyBillingRepository.findAll();

        return allCompanies.stream()
                .map(this::convertToUsageSummary)
                .filter(summary -> summary.getApproachingLimit())
                .collect(Collectors.toList());
    }

    @Override
    public List<BillingUsageLog> getBlockedAttempts(Long companyId, int days) {
        log.info("Fetching blocked attempts for company {} ({} days)", companyId, days);

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);

        return usageLogRepository.findByCompanyIdAndCreatedAtBetween(
                        companyId, startDate, LocalDateTime.now())
                .stream()
                .filter(BillingUsageLog::getWasBlocked)
                .collect(Collectors.toList());
    }

    @Override
    public Page<BillingUsageLog> getUsageLogs(Long companyId, String usageType, int page, int size) {
        log.info("Fetching usage logs for company {} (type: {}, page: {}, size: {})",
                companyId, usageType, page, size);

        if (usageType != null) {
            return usageLogRepository.findByCompanyIdAndUsageTypeOrderByCreatedAtDesc(
                    companyId, usageType, PageRequest.of(page, size));
        } else {
            return usageLogRepository.findByCompanyIdOrderByCreatedAtDesc(
                    companyId, PageRequest.of(page, size));
        }
    }

    @Override
    public Map<String, Object> getUsageBreakdown(Long companyId, LocalDateTime startDate,
                                                 LocalDateTime endDate) {
        log.info("Fetching usage breakdown for company {} from {} to {}",
                companyId, startDate, endDate);

        List<BillingUsageLog> logs = usageLogRepository.findByCompanyIdAndCreatedAtBetween(
                companyId, startDate, endDate);

        Map<String, Long> breakdown = logs.stream()
                .collect(Collectors.groupingBy(
                        BillingUsageLog::getUsageType,
                        Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("breakdown", breakdown);
        result.put("total", logs.size());
        result.put("startDate", startDate);
        result.put("endDate", endDate);

        return result;
    }

    private CompanyUsageSummaryDto convertToUsageSummary(CompanyBilling billing) {
        Double answersPercentage = calculatePercentage(
                billing.getAnswersUsedInPeriod(), billing.getEffectiveAnswersLimit());

        Double kbPagesPercentage = calculatePercentage(
                billing.getKbPagesTotal(), billing.getEffectiveKbPagesLimit());

        boolean approaching = answersPercentage > 80 || kbPagesPercentage > 80;

        String status = "ok";
        if (billing.getAnswersBlocked()) {
            status = "blocked";
        } else if (approaching) {
            status = "warning";
        }

        String alertMessage = null;
        if (billing.getAnswersBlocked()) {
            alertMessage = "Answer generation blocked";
        } else if (answersPercentage > 80) {
            alertMessage = "Approaching answer limit (" + answersPercentage + "%)";
        } else if (kbPagesPercentage > 80) {
            alertMessage = "Approaching KB page limit (" + kbPagesPercentage + "%)";
        }

        return CompanyUsageSummaryDto.builder()
                .companyId(billing.getCompanyId())
                .planCode(billing.getActivePlanCode())
                .subscriptionStatus(billing.getSubscriptionStatus())
                .answersUsed(billing.getAnswersUsedInPeriod())
                .answersLimit(billing.getEffectiveAnswersLimit())
                .answersPercentage(answersPercentage)
                .kbPagesUsed(billing.getKbPagesTotal())
                .kbPagesLimit(billing.getEffectiveKbPagesLimit())
                .kbPagesPercentage(kbPagesPercentage)
                .agentsUsed(billing.getAgentsTotal())
                .agentsLimit(billing.getEffectiveAgentsLimit())
                .usersUsed(billing.getUsersTotal())
                .usersLimit(billing.getEffectiveUsersLimit())
                .status(status)
                .answersBlocked(billing.getAnswersBlocked())
                .approachingLimit(approaching)
                .alertMessage(alertMessage)
                .build();
    }

    private Double calculatePercentage(Integer used, Integer limit) {
        if (limit == null || limit == 0) return 0.0;
        return (used.doubleValue() / limit.doubleValue()) * 100;
    }
}