package com.broadnet.billing.controller;

import com.broadnet.billing.dto.UsageCheckResult;
import com.broadnet.billing.dto.UsageIncrementResult;
import com.broadnet.billing.dto.UsageStatsDto;
import com.broadnet.billing.entity.BillingUsageLog;
import com.broadnet.billing.service.UsageAnalyticsService;
import com.broadnet.billing.service.UsageEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for Usage Enforcement and Analytics
 * Base path: /api/billing/usage
 *
 * Two responsibilities:
 * 1. ENFORCEMENT — atomically increment/decrement counters with row-level locking.
 *    Called internally by other services (KB, Agents, Users, Answer generation).
 * 2. ANALYTICS — read-only usage reports and breakdowns for dashboards and admins.
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageEnforcementService usageEnforcementService;
    private final UsageAnalyticsService usageAnalyticsService;

    // ─────────────────────────────────────────────────────────────────────────
    // ENFORCEMENT endpoints (called by internal services)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/billing/usage/increment-answer
     * Atomically increments answer usage with SELECT FOR UPDATE row-level locking.
     * Returns success=false and blocked=true if the limit is reached.
     * Called by the answer-generation service before generating each answer.
     */
    @PostMapping("/increment-answer")
    public ResponseEntity<UsageIncrementResult> incrementAnswerUsage(
            @RequestParam Long companyId) {
        log.debug("POST /api/billing/usage/increment-answer for company {}", companyId);
        return ResponseEntity.ok(usageEnforcementService.incrementAnswerUsage(companyId));
    }

    /**
     * POST /api/billing/usage/check-kb-page
     * Checks whether KB page creation is allowed (does NOT increment).
     * Called before creating a KB page to give a fast pre-check.
     */
    @PostMapping("/check-kb-page")
    public ResponseEntity<UsageCheckResult> checkKbPageLimit(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.checkKbPageLimit(companyId));
    }

    /**
     * POST /api/billing/usage/increment-kb-page
     * Atomically increments KB pages counter.
     * Called after a KB page is successfully created.
     */
    @PostMapping("/increment-kb-page")
    public ResponseEntity<UsageIncrementResult> incrementKbPageUsage(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.incrementKbPageUsage(companyId));
    }

    /**
     * POST /api/billing/usage/decrement-kb-page
     * Decrements KB pages counter when a page is deleted.
     */
    @PostMapping("/decrement-kb-page")
    public ResponseEntity<Void> decrementKbPageUsage(
            @RequestParam Long companyId) {
        usageEnforcementService.decrementKbPageUsage(companyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/billing/usage/check-agent
     * Checks whether agent creation is allowed.
     */
    @PostMapping("/check-agent")
    public ResponseEntity<UsageCheckResult> checkAgentLimit(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.checkAgentLimit(companyId));
    }

    /**
     * POST /api/billing/usage/increment-agent
     * Atomically increments agent counter.
     */
    @PostMapping("/increment-agent")
    public ResponseEntity<UsageIncrementResult> incrementAgentUsage(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.incrementAgentUsage(companyId));
    }

    /**
     * POST /api/billing/usage/decrement-agent
     * Decrements agent counter when an agent is deleted.
     */
    @PostMapping("/decrement-agent")
    public ResponseEntity<Void> decrementAgentUsage(
            @RequestParam Long companyId) {
        usageEnforcementService.decrementAgentUsage(companyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/billing/usage/check-user
     * Checks whether adding a user is allowed.
     */
    @PostMapping("/check-user")
    public ResponseEntity<UsageCheckResult> checkUserLimit(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.checkUserLimit(companyId));
    }

    /**
     * POST /api/billing/usage/increment-user
     * Atomically increments user counter.
     */
    @PostMapping("/increment-user")
    public ResponseEntity<UsageIncrementResult> incrementUserUsage(
            @RequestParam Long companyId) {
        return ResponseEntity.ok(usageEnforcementService.incrementUserUsage(companyId));
    }

    /**
     * POST /api/billing/usage/decrement-user
     * Decrements user counter when a user is removed.
     */
    @PostMapping("/decrement-user")
    public ResponseEntity<Void> decrementUserUsage(
            @RequestParam Long companyId) {
        usageEnforcementService.decrementUserUsage(companyId);
        return ResponseEntity.noContent().build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANALYTICS endpoints (read-only reporting)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/billing/usage/stats?startDate=...&endDate=...
     * Returns usage statistics for a date range:
     * total answers, blocked attempts, KB pages added/deleted, agents created, users added.
     */
    @GetMapping("/stats")
    public ResponseEntity<UsageStatsDto> getUsageStats(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now().minusDays(30)}")
            LocalDateTime startDate,
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now()}")
            LocalDateTime endDate) {
        return ResponseEntity.ok(usageAnalyticsService.getUsageStats(companyId, startDate, endDate));
    }

    /**
     * GET /api/billing/usage/daily?days=30
     * Returns daily answer usage counts for the last N days (for charts).
     * Response: { "2026-03-01": 42, "2026-03-02": 65, ... }
     */
    @GetMapping("/daily")
    public ResponseEntity<Map<String, Integer>> getDailyAnswerUsage(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(usageAnalyticsService.getDailyAnswerUsage(companyId, days));
    }

    /**
     * GET /api/billing/usage/logs?usageType=answer&page=0&size=20
     * Paginated usage log for a company. Filter by usageType (optional).
     */
    @GetMapping("/logs")
    public ResponseEntity<Page<BillingUsageLog>> getUsageLogs(
            @RequestParam Long companyId,
            @RequestParam(required = false) String usageType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                usageAnalyticsService.getUsageLogs(companyId, usageType, page, size));
    }

    /**
     * GET /api/billing/usage/breakdown?startDate=...&endDate=...
     * Returns usage count grouped by type for a date range.
     */
    @GetMapping("/breakdown")
    public ResponseEntity<Map<String, Object>> getUsageBreakdown(
            @RequestParam Long companyId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(
                usageAnalyticsService.getUsageBreakdown(companyId, startDate, endDate));
    }

    /**
     * GET /api/billing/usage/blocked?days=7
     * Returns all blocked usage attempts in the last N days.
     */
    @GetMapping("/blocked")
    public ResponseEntity<List<BillingUsageLog>> getBlockedAttempts(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(usageAnalyticsService.getBlockedAttempts(companyId, days));
    }
}