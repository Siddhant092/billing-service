package com.broadnet.billing.controller;

import com.broadnet.billing.dto.CompanyUsageSummaryDto;
import com.broadnet.billing.dto.EntitlementsDto;
import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.service.CompanyBillingService;
import com.broadnet.billing.service.EntitlementService;
import com.broadnet.billing.service.UsageAnalyticsService;
import com.broadnet.billing.service.UsageEnforcementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Admin Billing Operations
 * Base path: /api/admin/billing
 *
 * Admin-only endpoints for:
 * - Viewing and syncing any company's billing state
 * - Manually unblocking answers
 * - Viewing usage summary across all companies
 * - Identifying companies approaching limits (for proactive support/upsell)
 * - Recomputing entitlements for a plan
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {

    private final CompanyBillingService companyBillingService;
    private final EntitlementService entitlementService;
    private final UsageAnalyticsService usageAnalyticsService;
    private final UsageEnforcementService usageEnforcementService;

    /**
     * GET /api/admin/billing/companies/{companyId}
     * Returns the full billing record for a company.
     */
    @GetMapping("/companies/{companyId}")
    public ResponseEntity<CompanyBilling> getCompanyBilling(@PathVariable Long companyId) {
        log.info("Admin: GET billing for company {}", companyId);
        return ResponseEntity.ok(companyBillingService.getCompanyBilling(companyId));
    }

    /**
     * POST /api/admin/billing/companies/{companyId}/sync
     * Pulls the latest subscription state from Stripe and updates the local DB.
     * Use this to fix drift between Stripe and the local database.
     */
    @PostMapping("/companies/{companyId}/sync")
    public ResponseEntity<CompanyBilling> syncFromStripe(@PathVariable Long companyId) {
        log.info("Admin: syncing company {} from Stripe", companyId);
        return ResponseEntity.ok(companyBillingService.syncFromStripe(companyId));
    }

    /**
     * POST /api/admin/billing/companies/{companyId}/unblock-answers?adminUserId=1
     * Manually unblocks answer generation for a company.
     * Logs the admin user who performed the action.
     */
    @PostMapping("/companies/{companyId}/unblock-answers")
    public ResponseEntity<Void> unblockAnswers(
            @PathVariable Long companyId,
            @RequestParam Long adminUserId) {
        log.info("Admin {}: unblocking answers for company {}", adminUserId, companyId);
        usageEnforcementService.unblockAnswers(companyId, adminUserId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/billing/companies/{companyId}/reset-usage
     * Manually resets the period usage counters for a company.
     * Use carefully — this simulates a period rollover.
     */
    @PostMapping("/companies/{companyId}/reset-usage")
    public ResponseEntity<Void> resetPeriodUsage(@PathVariable Long companyId) {
        log.info("Admin: resetting period usage for company {}", companyId);
        usageEnforcementService.resetPeriodUsage(companyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/admin/billing/companies/{companyId}/entitlements
     * Returns the current computed entitlements (limits + usage) for a company.
     */
    @GetMapping("/companies/{companyId}/entitlements")
    public ResponseEntity<EntitlementsDto> getEntitlements(@PathVariable Long companyId) {
        return ResponseEntity.ok(entitlementService.getCurrentEntitlements(companyId));
    }

    /**
     * POST /api/admin/billing/plans/{planCode}/recompute-entitlements
     * Recomputes and updates entitlements for all companies currently on a plan.
     * Call this after changing a plan's limits to push the new limits to all subscribers.
     * Returns the count of companies updated.
     */
    @PostMapping("/plans/{planCode}/recompute-entitlements")
    public ResponseEntity<Integer> recomputeEntitlements(@PathVariable String planCode) {
        log.info("Admin: recomputing entitlements for plan {}", planCode);
        return ResponseEntity.ok(entitlementService.recomputeEntitlementsForPlan(planCode));
    }

    /**
     * GET /api/admin/billing/usage-summary?page=0&size=20
     * Paginated usage summary across all companies:
     * company, plan, usage vs limits, percentage, status (ok/warning/blocked).
     */
    @GetMapping("/usage-summary")
    public ResponseEntity<Page<CompanyUsageSummaryDto>> getCompanyUsageSummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(usageAnalyticsService.getCompanyUsageSummary(page, size));
    }

    /**
     * GET /api/admin/billing/approaching-limits?threshold=0.8
     * Returns companies that have used ≥ threshold% of any limit.
     * Default threshold is 80%. Used for proactive support and upsell.
     */
    @GetMapping("/approaching-limits")
    public ResponseEntity<List<CompanyUsageSummaryDto>> getCompaniesApproachingLimits(
            @RequestParam(defaultValue = "0.8") Double threshold) {
        return ResponseEntity.ok(usageAnalyticsService.getCompaniesApproachingLimits(threshold));
    }
}