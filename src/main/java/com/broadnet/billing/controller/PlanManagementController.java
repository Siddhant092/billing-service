package com.broadnet.billing.controller;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.service.PlanManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Plan and Addon Management
 *
 * Two sets of endpoints:
 * 1. PUBLIC  → /api/billing/plans  (anyone can view available plans and addons)
 * 2. ADMIN   → /api/admin/billing/plans  (create/update/deactivate plans and addons)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PlanManagementController {

    private final PlanManagementService planManagementService;

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC — plan and addon catalogue (pricing page, upgrade modal)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/billing/plans?billingInterval=month
     * Returns all active plans with their limits for the given billing interval.
     * Used by the pricing page and upgrade modal.
     */
    @GetMapping("/api/billing/plans")
    public ResponseEntity<List<PlanDto>> getAllActivePlans(
            @RequestParam(defaultValue = "month") String billingInterval) {
        return ResponseEntity.ok(planManagementService.getAllActivePlans(billingInterval));
    }

    /**
     * GET /api/billing/plans/{planCode}
     * Returns a single plan with all its limits (all intervals).
     */
    @GetMapping("/api/billing/plans/{planCode}")
    public ResponseEntity<PlanDto> getPlanByCode(@PathVariable String planCode) {
        return ResponseEntity.ok(planManagementService.getPlanByCode(planCode));
    }

    /**
     * GET /api/billing/addons?category=answers
     * Returns all active addons, optionally filtered by category.
     */
    @GetMapping("/api/billing/addons")
    public ResponseEntity<List<AddonDto>> getAllActiveAddons(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(planManagementService.getAllActiveAddons(category));
    }

    /**
     * GET /api/billing/addons/{addonCode}
     * Returns a single addon with all its deltas.
     */
    @GetMapping("/api/billing/addons/{addonCode}")
    public ResponseEntity<AddonDto> getAddonByCode(@PathVariable String addonCode) {
        return ResponseEntity.ok(planManagementService.getAddonByCode(addonCode));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN — plan and addon CRUD + Stripe sync
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/admin/billing/plans
     * Creates a new plan definition with its limits.
     * Admin only.
     */
    @PostMapping("/api/admin/billing/plans")
    public ResponseEntity<PlanDto> createPlan(@RequestBody PlanDto planDto) {
        log.info("Admin: creating plan {}", planDto.getPlanCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planManagementService.createPlan(planDto));
    }

    /**
     * PATCH /api/admin/billing/plans/{planCode}/limits
     * Updates a specific limit for a plan (e.g. raise answer limit from 1000 to 2000).
     * Automatically recomputes entitlements for all companies on this plan.
     */
    @PatchMapping("/api/admin/billing/plans/{planCode}/limits")
    public ResponseEntity<PlanDto> updatePlanLimit(
            @PathVariable String planCode,
            @RequestBody PlanLimitDto limitDto) {
        log.info("Admin: updating limits for plan {}", planCode);
        return ResponseEntity.ok(planManagementService.updatePlanLimit(planCode, limitDto));
    }

    /**
     * DELETE /api/admin/billing/plans/{planCode}
     * Soft-deactivates a plan (is_active = false). Does not affect existing subscribers.
     */
    @DeleteMapping("/api/admin/billing/plans/{planCode}")
    public ResponseEntity<Void> deactivatePlan(@PathVariable String planCode) {
        log.info("Admin: deactivating plan {}", planCode);
        planManagementService.deactivatePlan(planCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/billing/addons
     * Creates a new addon definition with its deltas.
     * Admin only.
     */
    @PostMapping("/api/admin/billing/addons")
    public ResponseEntity<AddonDto> createAddon(@RequestBody AddonDto addonDto) {
        log.info("Admin: creating addon {}", addonDto.getAddonCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planManagementService.createAddon(addonDto));
    }

    /**
     * PATCH /api/admin/billing/addons/{addonCode}/delta
     * Updates the delta (limit increase) value of an addon.
     */
    @PatchMapping("/api/admin/billing/addons/{addonCode}/delta")
    public ResponseEntity<AddonDto> updateAddonDelta(
            @PathVariable String addonCode,
            @RequestBody AddonDeltaDto deltaDto) {
        log.info("Admin: updating delta for addon {}", addonCode);
        return ResponseEntity.ok(planManagementService.updateAddonDelta(addonCode, deltaDto));
    }

    /**
     * DELETE /api/admin/billing/addons/{addonCode}
     * Soft-deactivates an addon.
     */
    @DeleteMapping("/api/admin/billing/addons/{addonCode}")
    public ResponseEntity<Void> deactivateAddon(@PathVariable String addonCode) {
        log.info("Admin: deactivating addon {}", addonCode);
        planManagementService.deactivateAddon(addonCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/admin/billing/sync-stripe-prices
     * Pulls all active prices from Stripe and upserts them into billing_stripe_prices.
     * Returns count of newly synced prices.
     */
    @PostMapping("/api/admin/billing/sync-stripe-prices")
    public ResponseEntity<Integer> syncStripePrices() {
        log.info("Admin: syncing Stripe prices");
        return ResponseEntity.ok(planManagementService.syncStripePrices());
    }
}