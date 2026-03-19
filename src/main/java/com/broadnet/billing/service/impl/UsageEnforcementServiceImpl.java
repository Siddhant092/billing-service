package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.UsageCheckResult;
import com.broadnet.billing.dto.UsageIncrementResult;
import com.broadnet.billing.entity.BillingUsageLog;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.repository.BillingUsageLogRepository;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.UsageEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Implements prepaid usage enforcement with row-level locking.
 *
 * Architecture Plan locking strategy:
 * - Answers: atomic UPDATE with version+limit check (optimistic-style in one SQL)
 * - KB/Agents/Users: SELECT FOR UPDATE then UPDATE (pessimistic lock)
 * Enterprise customers route through BillingEnterpriseUsageService — this class
 * only handles prepaid (blocking) enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageEnforcementServiceImpl implements UsageEnforcementService {

    private final CompanyBillingRepository companyBillingRepository;
    private final BillingUsageLogRepository usageLogRepository;

    // -------------------------------------------------------------------------
    // Answers — atomic increment via versioned UPDATE
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public UsageIncrementResult incrementAnswerUsage(Long companyId) {
        // Lock row first
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        // Check if already blocked
        if (Boolean.TRUE.equals(billing.getAnswersBlocked())) {
            logUsage(companyId, BillingUsageLog.UsageType.answer, 1,
                    billing.getAnswersUsedInPeriod(), billing.getAnswersUsedInPeriod(),
                    true, "ANSWER_LIMIT_EXCEEDED");
            return UsageIncrementResult.builder()
                    .success(false)
                    .used(billing.getAnswersUsedInPeriod())
                    .limit(billing.getEffectiveAnswersLimit())
                    .remaining(0)
                    .blocked(true)
                    .errorCode("ANSWER_LIMIT_EXCEEDED")
                    .message("You've reached your answer limit. Upgrade plan or add a boost.")
                    .build();
        }

        // Check if at limit
        if (billing.getAnswersUsedInPeriod() >= billing.getEffectiveAnswersLimit()) {
            // Set blocked flag
            billing.setAnswersBlocked(true);
            companyBillingRepository.save(billing);

            logUsage(companyId, BillingUsageLog.UsageType.answer, 1,
                    billing.getAnswersUsedInPeriod(), billing.getAnswersUsedInPeriod(),
                    true, "ANSWER_LIMIT_EXCEEDED");

            return UsageIncrementResult.builder()
                    .success(false)
                    .used(billing.getAnswersUsedInPeriod())
                    .limit(billing.getEffectiveAnswersLimit())
                    .remaining(0)
                    .blocked(true)
                    .errorCode("ANSWER_LIMIT_EXCEEDED")
                    .message("You've reached your answer limit. Upgrade plan or add a boost.")
                    .build();
        }

        // Attempt atomic increment via versioned UPDATE
        int updated = companyBillingRepository.incrementAnswerUsage(
                companyId, 1, billing.getVersion());

        if (updated == 0) {
            // Version conflict — caller should retry
            log.warn("Version conflict on answer increment for companyId={}", companyId);
            return UsageIncrementResult.builder()
                    .success(false)
                    .errorCode("CONCURRENT_UPDATE")
                    .message("Concurrent update, please retry.")
                    .build();
        }

        // Refresh to get updated counts
        CompanyBilling updated_ = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow();

        int newCount  = updated_.getAnswersUsedInPeriod();
        int limit     = updated_.getEffectiveAnswersLimit();
        int remaining = Math.max(0, limit - newCount);
        boolean nowBlocked = newCount >= limit;

        logUsage(companyId, BillingUsageLog.UsageType.answer, 1,
                newCount - 1, newCount, false, null);

        log.debug("Answer incremented for companyId={}: {}/{}", companyId, newCount, limit);

        return UsageIncrementResult.builder()
                .success(true)
                .used(newCount)
                .limit(limit)
                .remaining(remaining)
                .blocked(nowBlocked)
                .build();
    }

    // -------------------------------------------------------------------------
    // KB Pages — pessimistic lock
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageCheckResult checkKbPageLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used      = billing.getKbPagesTotal();
        int limit     = billing.getEffectiveKbPagesLimit();
        int remaining = Math.max(0, limit - used);
        double pct    = limit > 0 ? (used * 100.0 / limit) : 0.0;

        return UsageCheckResult.builder()
                .allowed(used < limit)
                .used(used)
                .limit(limit)
                .remaining(remaining)
                .percentageUsed(pct)
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementKbPageUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used  = billing.getKbPagesTotal();
        int limit = billing.getEffectiveKbPagesLimit();

        if (used >= limit) {
            logUsage(companyId, BillingUsageLog.UsageType.kb_page_added, 1,
                    used, used, true, "KB_PAGE_LIMIT_EXCEEDED");
            return UsageIncrementResult.builder()
                    .success(false)
                    .used(used).limit(limit).remaining(0).blocked(true)
                    .errorCode("KB_PAGE_LIMIT_EXCEEDED")
                    .message("You've reached your KB pages limit.")
                    .build();
        }

        billing.setKbPagesTotal(used + 1);
        companyBillingRepository.save(billing);

        logUsage(companyId, BillingUsageLog.UsageType.kb_page_added, 1, used, used + 1, false, null);

        return UsageIncrementResult.builder()
                .success(true)
                .used(used + 1).limit(limit).remaining(limit - used - 1).blocked(false)
                .build();
    }

    @Override
    @Transactional
    public void decrementKbPageUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int newCount = Math.max(0, billing.getKbPagesTotal() - 1);
        billing.setKbPagesTotal(newCount);
        companyBillingRepository.save(billing);
    }

    // -------------------------------------------------------------------------
    // Agents — pessimistic lock
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageCheckResult checkAgentLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used = billing.getAgentsTotal(), limit = billing.getEffectiveAgentsLimit();
        return UsageCheckResult.builder()
                .allowed(used < limit).used(used).limit(limit)
                .remaining(Math.max(0, limit - used))
                .percentageUsed(limit > 0 ? (used * 100.0 / limit) : 0.0)
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementAgentUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used = billing.getAgentsTotal(), limit = billing.getEffectiveAgentsLimit();
        if (used >= limit) {
            logUsage(companyId, BillingUsageLog.UsageType.agent_created, 1,
                    used, used, true, "AGENT_LIMIT_EXCEEDED");
            return UsageIncrementResult.builder()
                    .success(false).used(used).limit(limit).remaining(0).blocked(true)
                    .errorCode("AGENT_LIMIT_EXCEEDED")
                    .message("You've reached your agent limit.")
                    .build();
        }

        billing.setAgentsTotal(used + 1);
        companyBillingRepository.save(billing);
        logUsage(companyId, BillingUsageLog.UsageType.agent_created, 1, used, used + 1, false, null);

        return UsageIncrementResult.builder()
                .success(true).used(used + 1).limit(limit).remaining(limit - used - 1).blocked(false)
                .build();
    }

    @Override
    @Transactional
    public void decrementAgentUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));
        billing.setAgentsTotal(Math.max(0, billing.getAgentsTotal() - 1));
        companyBillingRepository.save(billing);
    }

    // -------------------------------------------------------------------------
    // Users — pessimistic lock
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public UsageCheckResult checkUserLimit(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used = billing.getUsersTotal(), limit = billing.getEffectiveUsersLimit();
        return UsageCheckResult.builder()
                .allowed(used < limit).used(used).limit(limit)
                .remaining(Math.max(0, limit - used))
                .percentageUsed(limit > 0 ? (used * 100.0 / limit) : 0.0)
                .build();
    }

    @Override
    @Transactional
    public UsageIncrementResult incrementUserUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        int used = billing.getUsersTotal(), limit = billing.getEffectiveUsersLimit();
        if (used >= limit) {
            logUsage(companyId, BillingUsageLog.UsageType.user_created, 1,
                    used, used, true, "USER_LIMIT_EXCEEDED");
            return UsageIncrementResult.builder()
                    .success(false).used(used).limit(limit).remaining(0).blocked(true)
                    .errorCode("USER_LIMIT_EXCEEDED")
                    .message("You've reached your user limit.")
                    .build();
        }

        billing.setUsersTotal(used + 1);
        companyBillingRepository.save(billing);
        logUsage(companyId, BillingUsageLog.UsageType.user_created, 1, used, used + 1, false, null);

        return UsageIncrementResult.builder()
                .success(true).used(used + 1).limit(limit).remaining(limit - used - 1).blocked(false)
                .build();
    }

    @Override
    @Transactional
    public void decrementUserUsage(Long companyId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));
        billing.setUsersTotal(Math.max(0, billing.getUsersTotal() - 1));
        companyBillingRepository.save(billing);
    }

    // -------------------------------------------------------------------------
    // Admin operations
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void unblockAnswers(Long companyId, Long adminUserId) {
        CompanyBilling billing = companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));

        billing.setAnswersBlocked(false);
        companyBillingRepository.save(billing);

        // Log the manual unblock
        BillingUsageLog log_ = BillingUsageLog.builder()
                .companyId(companyId)
                .usageType(BillingUsageLog.UsageType.answer)
                .usageCount(0)
                .wasBlocked(false)
                .metadata(Map.of("action", "manual_unblock", "adminUserId", adminUserId))
                .build();
        usageLogRepository.save(log_);
        log.info("Answers unblocked for companyId={} by adminUserId={}", companyId, adminUserId);
    }

    @Override
    @Transactional
    public void resetPeriodUsage(Long companyId) {
        companyBillingRepository.resetPeriodUsage(companyId, LocalDateTime.now());
        log.info("Period usage reset for companyId={}", companyId);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void logUsage(Long companyId, BillingUsageLog.UsageType type, int count,
                          Integer before, Integer after, boolean wasBlocked, String blockReason) {
        try {
            BillingUsageLog entry = BillingUsageLog.builder()
                    .companyId(companyId)
                    .usageType(type)
                    .usageCount(count)
                    .beforeCount(before)
                    .afterCount(after)
                    .wasBlocked(wasBlocked)
                    .blockReason(blockReason)
                    .build();
            usageLogRepository.save(entry);
        } catch (Exception e) {
            // Usage logging must never break the main flow
            log.error("Failed to log usage for companyId={}, type={}: {}", companyId, type, e.getMessage());
        }
    }
}