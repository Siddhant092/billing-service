package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.UsageCheckResult;
import com.broadnet.billing.dto.UsageIncrementResult;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.entity.BillingUsageLog;
import com.broadnet.billing.exception.UsageLimitExceededException;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.repository.BillingUsageLogRepository;
import com.broadnet.billing.service.UsageEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of UsageEnforcementService
 * Handles atomic usage increments with row-level locking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageEnforcementServiceImpl implements UsageEnforcementService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingUsageLogRepository billingUsageLogRepository;

    @Override
    @Transactional
    public UsageIncrementResult incrementAnswerUsage(Long companyId) {
        log.debug("Incrementing answer usage for company: {}", companyId);

        // SELECT FOR UPDATE - row-level lock
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer beforeCount = billing.getAnswersUsedInPeriod();
        Integer limit = billing.getEffectiveAnswersLimit();

        // Check if already blocked
        if (billing.getAnswersBlocked()) {
            logUsage(companyId, "answer", 1, beforeCount, beforeCount, true, "Already blocked");

            return UsageIncrementResult.builder()
                    .success(false)
                    .usageCount(beforeCount)
                    .limit(limit)
                    .remaining(0)
                    .blocked(true)
                    .error("ANSWER_LIMIT_EXCEEDED")
                    .message("Answer limit already exceeded. Please upgrade your plan.")
                    .build();
        }

        // Check if at limit
        if (beforeCount >= limit) {
            // Set blocked flag
            billing.setAnswersBlocked(true);
            companyBillingRepository.save(billing);

            logUsage(companyId, "answer", 1, beforeCount, beforeCount, true, "Limit exceeded");

            return UsageIncrementResult.builder()
                    .success(false)
                    .usageCount(beforeCount)
                    .limit(limit)
                    .remaining(0)
                    .blocked(true)
                    .error("ANSWER_LIMIT_EXCEEDED")
                    .message("You've reached your answer limit. Upgrade your plan or add a boost.")
                    .build();
        }

        // Increment usage
        Integer newCount = beforeCount + 1;
        billing.setAnswersUsedInPeriod(newCount);

        // Set blocked if now at limit
        if (newCount >= limit) {
            billing.setAnswersBlocked(true);
        }

        companyBillingRepository.save(billing);

        // Log usage
        logUsage(companyId, "answer", 1, beforeCount, newCount, false, null);

        return UsageIncrementResult.builder()
                .success(true)
                .usageCount(newCount)
                .limit(limit)
                .remaining(limit - newCount)
                .blocked(newCount >= limit)
                .message("Answer usage incremented successfully")
                .build();
    }

    @Override
    public UsageCheckResult checkKbPageLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer current = billing.getKbPagesTotal();
        Integer limit = billing.getEffectiveKbPagesLimit();
        boolean allowed = current < limit;

        return UsageCheckResult.builder()
                .allowed(allowed)
                .currentUsage(current)
                .limit(limit)
                .remaining(allowed ? limit - current : 0)
                .usageType("kb_pages")
                .message(allowed ? "KB page creation allowed" : "KB page limit reached")
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementKbPageUsage(Long companyId) {
        log.debug("Incrementing KB page usage for company: {}", companyId);

        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer beforeCount = billing.getKbPagesTotal();
        Integer limit = billing.getEffectiveKbPagesLimit();

        if (beforeCount >= limit) {
            logUsage(companyId, "kb_page_added", 1, beforeCount, beforeCount, true, "Limit exceeded");

            throw new UsageLimitExceededException(
                    "KB page limit exceeded", "kb_pages", beforeCount, limit);
        }

        Integer newCount = beforeCount + 1;
        billing.setKbPagesTotal(newCount);
        companyBillingRepository.save(billing);

        logUsage(companyId, "kb_page_added", 1, beforeCount, newCount, false, null);

        return UsageIncrementResult.builder()
                .success(true)
                .usageCount(newCount)
                .limit(limit)
                .remaining(limit - newCount)
                .blocked(false)
                .message("KB page usage incremented successfully")
                .build();
    }

    @Override
    @Transactional
    public void decrementKbPageUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        if (billing.getKbPagesTotal() > 0) {
            billing.setKbPagesTotal(billing.getKbPagesTotal() - 1);
            companyBillingRepository.save(billing);

            logUsage(companyId, "kb_page_deleted", -1,
                    billing.getKbPagesTotal() + 1, billing.getKbPagesTotal(), false, null);
        }
    }

    @Override
    public UsageCheckResult checkAgentLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer current = billing.getAgentsTotal();
        Integer limit = billing.getEffectiveAgentsLimit();
        boolean allowed = current < limit;

        return UsageCheckResult.builder()
                .allowed(allowed)
                .currentUsage(current)
                .limit(limit)
                .remaining(allowed ? limit - current : 0)
                .usageType("agents")
                .message(allowed ? "Agent creation allowed" : "Agent limit reached")
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementAgentUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer beforeCount = billing.getAgentsTotal();
        Integer limit = billing.getEffectiveAgentsLimit();

        if (beforeCount >= limit) {
            throw new UsageLimitExceededException(
                    "Agent limit exceeded", "agents", beforeCount, limit);
        }

        Integer newCount = beforeCount + 1;
        billing.setAgentsTotal(newCount);
        companyBillingRepository.save(billing);

        logUsage(companyId, "agent_created", 1, beforeCount, newCount, false, null);

        return UsageIncrementResult.builder()
                .success(true)
                .usageCount(newCount)
                .limit(limit)
                .remaining(limit - newCount)
                .build();
    }

    @Override
    @Transactional
    public void decrementAgentUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        if (billing.getAgentsTotal() > 0) {
            billing.setAgentsTotal(billing.getAgentsTotal() - 1);
            companyBillingRepository.save(billing);
        }
    }

    @Override
    public UsageCheckResult checkUserLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer current = billing.getUsersTotal();
        Integer limit = billing.getEffectiveUsersLimit();
        boolean allowed = current < limit;

        return UsageCheckResult.builder()
                .allowed(allowed)
                .currentUsage(current)
                .limit(limit)
                .remaining(allowed ? limit - current : 0)
                .usageType("users")
                .message(allowed ? "User creation allowed" : "User limit reached")
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementUserUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        Integer beforeCount = billing.getUsersTotal();
        Integer limit = billing.getEffectiveUsersLimit();

        if (beforeCount >= limit) {
            throw new UsageLimitExceededException(
                    "User limit exceeded", "users", beforeCount, limit);
        }

        Integer newCount = beforeCount + 1;
        billing.setUsersTotal(newCount);
        companyBillingRepository.save(billing);

        logUsage(companyId, "user_created", 1, beforeCount, newCount, false, null);

        return UsageIncrementResult.builder()
                .success(true)
                .usageCount(newCount)
                .limit(limit)
                .remaining(limit - newCount)
                .build();
    }

    @Override
    @Transactional
    public void decrementUserUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        if (billing.getUsersTotal() > 0) {
            billing.setUsersTotal(billing.getUsersTotal() - 1);
            companyBillingRepository.save(billing);
        }
    }

    @Override
    @Transactional
    public void unblockAnswers(Long companyId, Long adminUserId) {
        log.info("Unblocking answers for company {} by admin {}", companyId, adminUserId);

        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new RuntimeException("Company billing not found"));

        billing.setAnswersBlocked(false);
        companyBillingRepository.save(billing);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("admin_user_id", adminUserId);
        metadata.put("action", "manual_unblock");

        logUsageWithMetadata(companyId, "answer", 0,
                billing.getAnswersUsedInPeriod(), billing.getAnswersUsedInPeriod(),
                false, "Manually unblocked by admin", metadata);
    }

    @Override
    @Transactional
    public void resetPeriodUsage(Long companyId) {
        log.info("Resetting period usage for company: {}", companyId);

        companyBillingRepository.resetPeriodUsage(companyId, LocalDateTime.now());

        logUsage(companyId, "answer", 0, null, 0, false, "Period reset");
    }

    private void logUsage(Long companyId, String usageType, Integer count,
                          Integer beforeCount, Integer afterCount,
                          Boolean wasBlocked, String blockReason) {
        logUsageWithMetadata(companyId, usageType, count, beforeCount, afterCount,
                wasBlocked, blockReason, null);
    }

    private void logUsageWithMetadata(Long companyId, String usageType, Integer count,
                                      Integer beforeCount, Integer afterCount,
                                      Boolean wasBlocked, String blockReason,
                                      Map<String, Object> metadata) {
        BillingUsageLog log = BillingUsageLog.builder()
                .companyId(companyId)
                .usageType(usageType)
                .usageCount(count)
                .beforeCount(beforeCount)
                .afterCount(afterCount)
                .wasBlocked(wasBlocked)
                .blockReason(blockReason)
                .metadata(metadata)
                .build();

        billingUsageLogRepository.save(log);
    }
}