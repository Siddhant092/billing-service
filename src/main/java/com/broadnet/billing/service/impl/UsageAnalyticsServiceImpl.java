package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.CompanyUsageSummaryDto;
import com.broadnet.billing.dto.UsageStatsDto;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.UsageAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageAnalyticsServiceImpl implements UsageAnalyticsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final BillingUsageLogRepository usageLogRepository;
    private final BillingUsageAnalyticsRepository analyticsRepository;
    private final CompanyBillingRepository companyBillingRepository;

    // -------------------------------------------------------------------------
    // Raw log analytics
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageStatsDto getUsageStats(Long companyId, LocalDateTime startDate, LocalDateTime endDate) {
        List<BillingUsageLog> logs = usageLogRepository
                .findByCompanyIdAndCreatedAtBetween(companyId, startDate, endDate);

        int totalAnswers = 0, totalKbAdded = 0, totalKbUpdated = 0,
            totalAgents = 0, totalUsers = 0, totalBlocked = 0, blockedAnswers = 0;

        for (BillingUsageLog log : logs) {
            int count = log.getUsageCount() != null ? log.getUsageCount() : 1;
            switch (log.getUsageType()) {
                case answer          -> { totalAnswers += count;
                    if (Boolean.TRUE.equals(log.getWasBlocked())) blockedAnswers++; }
                case kb_page_added   -> totalKbAdded += count;
                case kb_page_updated -> totalKbUpdated += count;
                case agent_created   -> totalAgents += count;
                case user_created    -> totalUsers += count;
            }
            if (Boolean.TRUE.equals(log.getWasBlocked())) totalBlocked++;
        }

        return UsageStatsDto.builder()
                .companyId(companyId)
                .startDate(startDate)
                .endDate(endDate)
                .totalAnswers(totalAnswers)
                .totalKbPagesAdded(totalKbAdded)
                .totalKbPagesUpdated(totalKbUpdated)
                .totalAgentsCreated(totalAgents)
                .totalUsersCreated(totalUsers)
                .totalBlockedAttempts(totalBlocked)
                .blockedAnswerAttempts(blockedAnswers)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getDailyAnswerUsage(Long companyId, int days) {
        LocalDateTime end   = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);

        List<BillingUsageLog> logs = usageLogRepository
                .findByCompanyIdAndCreatedAtBetween(companyId, start, end)
                .stream()
                .filter(l -> l.getUsageType() == BillingUsageLog.UsageType.answer
                        && !Boolean.TRUE.equals(l.getWasBlocked()))
                .collect(Collectors.toList());

        // Build ordered map with 0 values for every day in range
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            result.put(end.minusDays(i).format(DATE_FMT), 0);
        }

        // Aggregate by day
        for (BillingUsageLog log : logs) {
            String day = log.getCreatedAt().format(DATE_FMT);
            result.merge(day, log.getUsageCount() != null ? log.getUsageCount() : 1, Integer::sum);
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Admin: company summaries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyUsageSummaryDto> getCompanyUsageSummary(int page, int size) {
        // Paginate over all active company billings
        org.springframework.data.domain.Page<CompanyBilling> billings =
                companyBillingRepository.findAll(PageRequest.of(page, size));

        List<CompanyUsageSummaryDto> summaries = billings.getContent()
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());

        return new PageImpl<>(summaries, billings.getPageable(), billings.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyUsageSummaryDto> getCompaniesApproachingLimits(Double threshold) {
        // threshold e.g. 0.8 = 80%
        return companyBillingRepository.findApproachingAnswerLimit(threshold)
                .stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Blocked attempts
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<BillingUsageLog> getBlockedAttempts(Long companyId, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        return usageLogRepository.findByCompanyIdAndCreatedAtBetween(
                        companyId, since, LocalDateTime.now())
                .stream()
                .filter(l -> Boolean.TRUE.equals(l.getWasBlocked()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Usage logs with pagination
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<BillingUsageLog> getUsageLogs(Long companyId,
                                               BillingUsageLog.UsageType usageType,
                                               int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (usageType != null) {
            return usageLogRepository.findByCompanyIdAndUsageTypeOrderByCreatedAtDesc(
                    companyId, usageType, pageRequest);
        }
        return usageLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId, pageRequest);
    }

    // -------------------------------------------------------------------------
    // Usage breakdown
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUsageBreakdown(Long companyId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate) {
        List<Object[]> rows = usageLogRepository.countUsageByTypeInDateRange(
                companyId, startDate, endDate);

        Map<String, Object> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String type = row[0] != null ? row[0].toString() : "unknown";
            Long   sum  = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
            result.put(type, sum);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Pre-aggregated analytics (billing_usage_analytics)
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public int aggregateUsageAnalytics(Long companyId,
                                        BillingUsageAnalytics.PeriodType periodType) {
        LocalDateTime now = LocalDateTime.now();
        int written = 0;

        // Determine date range to aggregate based on period type
        LocalDateTime periodStart = switch (periodType) {
            case hour  -> now.minusHours(1).withMinute(0).withSecond(0).withNano(0);
            case day   -> now.minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case week  -> now.minusWeeks(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
            case month -> now.minusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        };
        LocalDateTime periodEnd = now;

        // Determine which companies to aggregate
        List<CompanyBilling> targets;
        if (companyId != null) {
            targets = companyBillingRepository.findByCompanyId(companyId)
                    .map(List::of).orElse(List.of());
        } else {
            targets = companyBillingRepository.findAll();
        }

        for (CompanyBilling billing : targets) {
            for (BillingUsageAnalytics.MetricType metricType
                    : BillingUsageAnalytics.MetricType.values()) {

                int usageCount = computeUsageCount(billing.getCompanyId(),
                        metricType, periodStart, periodEnd);
                Integer limitValue = getLimit(billing, metricType);

                // Upsert the analytics bucket
                Optional<BillingUsageAnalytics> existing =
                        analyticsRepository.findByCompanyIdAndMetricTypeAndPeriodTypeAndPeriodStart(
                                billing.getCompanyId(), metricType, periodType, periodStart);

                BillingUsageAnalytics record = existing.orElseGet(() ->
                        BillingUsageAnalytics.builder()
                                .companyId(billing.getCompanyId())
                                .metricType(metricType)
                                .periodType(periodType)
                                .periodStart(periodStart)
                                .periodEnd(periodEnd)
                                .build());

                record.setUsageCount(usageCount);
                record.setLimitValue(limitValue);
                analyticsRepository.save(record);
                written++;
            }
        }

        log.info("Aggregated {} analytics buckets for periodType={}", written, periodType);
        return written;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> getAnalyticsSeries(Long companyId,
                                                    BillingUsageAnalytics.MetricType metricType,
                                                    BillingUsageAnalytics.PeriodType periodType,
                                                    LocalDateTime startDate,
                                                    LocalDateTime endDate) {
        List<BillingUsageAnalytics> records = analyticsRepository
                .findByCompanyAndMetricInRange(companyId, metricType, periodType, startDate, endDate);

        Map<String, Integer> result = new LinkedHashMap<>();
        for (BillingUsageAnalytics record : records) {
            result.put(record.getPeriodStart().format(ISO_FMT), record.getUsageCount());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int computeUsageCount(Long companyId, BillingUsageAnalytics.MetricType metricType,
                                   LocalDateTime start, LocalDateTime end) {
        BillingUsageLog.UsageType usageType = switch (metricType) {
            case answers  -> BillingUsageLog.UsageType.answer;
            case kb_pages -> BillingUsageLog.UsageType.kb_page_added;
            case agents   -> BillingUsageLog.UsageType.agent_created;
            case users    -> BillingUsageLog.UsageType.user_created;
        };

        return usageLogRepository
                .findByCompanyIdAndCreatedAtBetween(companyId, start, end)
                .stream()
                .filter(l -> l.getUsageType() == usageType
                        && !Boolean.TRUE.equals(l.getWasBlocked()))
                .mapToInt(l -> l.getUsageCount() != null ? l.getUsageCount() : 1)
                .sum();
    }

    private Integer getLimit(CompanyBilling billing, BillingUsageAnalytics.MetricType type) {
        return switch (type) {
            case answers  -> billing.getEffectiveAnswersLimit();
            case kb_pages -> billing.getEffectiveKbPagesLimit();
            case agents   -> billing.getEffectiveAgentsLimit();
            case users    -> billing.getEffectiveUsersLimit();
        };
    }

    private CompanyUsageSummaryDto toSummaryDto(CompanyBilling b) {
        int answersLimit = b.getEffectiveAnswersLimit();
        int kbLimit      = b.getEffectiveKbPagesLimit();
        int agentsLimit  = b.getEffectiveAgentsLimit();
        int usersLimit   = b.getEffectiveUsersLimit();

        double answersPct  = answersLimit > 0 ? (b.getAnswersUsedInPeriod() * 100.0 / answersLimit) : 0;
        double kbPct       = kbLimit      > 0 ? (b.getKbPagesTotal()        * 100.0 / kbLimit)      : 0;
        double agentsPct   = agentsLimit  > 0 ? (b.getAgentsTotal()         * 100.0 / agentsLimit)   : 0;
        double usersPct    = usersLimit   > 0 ? (b.getUsersTotal()          * 100.0 / usersLimit)    : 0;

        String status = deriveStatus(b, answersPct, kbPct);

        return CompanyUsageSummaryDto.builder()
                .companyId(b.getCompanyId())
                .subscriptionStatus(b.getSubscriptionStatus())
                .activePlanCode(b.getActivePlanCode())
                .billingMode(b.getBillingMode())
                .answersUsed(b.getAnswersUsedInPeriod())
                .answersLimit(answersLimit)
                .answersPercentage(answersPct)
                .answersBlocked(b.getAnswersBlocked())
                .kbPagesTotal(b.getKbPagesTotal())
                .kbPagesLimit(kbLimit)
                .kbPagesPercentage(kbPct)
                .agentsTotal(b.getAgentsTotal())
                .agentsLimit(agentsLimit)
                .agentsPercentage(agentsPct)
                .usersTotal(b.getUsersTotal())
                .usersLimit(usersLimit)
                .usersPercentage(usersPct)
                .status(status)
                .build();
    }

    private String deriveStatus(CompanyBilling b, double answersPct, double kbPct) {
        if (Boolean.TRUE.equals(b.getAnswersBlocked())
                || b.getServiceRestrictedAt() != null) return "blocked";
        if (answersPct >= 90 || kbPct >= 90)  return "warning";
        if (answersPct >= 80 || kbPct >= 80)  return "warning";
        return "ok";
    }
}
