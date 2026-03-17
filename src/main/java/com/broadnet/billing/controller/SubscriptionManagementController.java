package com.broadnet.billing.controller;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.service.SubscriptionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Subscription Management
 * Base path: /api/billing/subscription
 *
 * Handles all subscription lifecycle operations:
 * - View current subscription
 * - Upgrade / downgrade plan (immediate for monthly, scheduled for annual downgrades)
 * - Add / remove / upgrade addons
 * - Cancel subscription (at period end)
 * - Reactivate a canceled subscription
 * - Preview cost of a plan change before committing
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/subscription")
@RequiredArgsConstructor
public class SubscriptionManagementController {

    private final SubscriptionManagementService subscriptionManagementService;

    /**
     * GET /api/billing/subscription
     * Returns current subscription: plan, status, billing interval,
     * period dates, cancel flag, active addons.
     */
    @GetMapping
    public ResponseEntity<SubscriptionDto> getCurrentSubscription(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/subscription for company {}", companyId);
        return ResponseEntity.ok(subscriptionManagementService.getCurrentSubscription(companyId));
    }

    /**
     * POST /api/billing/subscription/change-plan
     * Upgrades or downgrades the subscription plan.
     *
     * Logic:
     * - Monthly plan changes → immediate with proration
     * - Annual plan upgrades → immediate with proration
     * - Annual plan downgrades → scheduled at current period end
     *
     * Request body: { "newPlanCode": "professional", "billingInterval": "month",
     *                 "prorationBehavior": true }
     */
    @PostMapping("/change-plan")
    public ResponseEntity<SubscriptionDto> changePlan(
            @RequestParam Long companyId,
            @RequestBody PlanChangeRequest request) {
        log.info("POST /api/billing/subscription/change-plan for company {} to plan {}",
                companyId, request.getNewPlanCode());
        return ResponseEntity.ok(subscriptionManagementService.changePlan(companyId, request));
    }

    /**
     * POST /api/billing/subscription/preview-change
     * Previews the cost impact of a plan change without applying it.
     * Returns proration amount, new amount, effective date.
     *
     * Request params: newPlanCode, billingInterval
     */
    @PostMapping("/preview-change")
    public ResponseEntity<SubscriptionPreviewDto> previewPlanChange(
            @RequestParam Long companyId,
            @RequestParam String newPlanCode,
            @RequestParam(defaultValue = "month") String billingInterval) {
        log.info("POST /api/billing/subscription/preview-change for company {} to {}",
                companyId, newPlanCode);
        return ResponseEntity.ok(
                subscriptionManagementService.previewPlanChange(companyId, newPlanCode, billingInterval));
    }

    /**
     * POST /api/billing/subscription/addons/add?addonCode=answers_boost_s
     * Adds an addon to the current subscription.
     * Proration is applied immediately.
     */
    @PostMapping("/addons/add")
    public ResponseEntity<SubscriptionDto> addAddon(
            @RequestParam Long companyId,
            @RequestParam String addonCode) {
        log.info("POST /api/billing/subscription/addons/add for company {} addon {}",
                companyId, addonCode);
        return ResponseEntity.ok(subscriptionManagementService.addAddon(companyId, addonCode));
    }

    /**
     * POST /api/billing/subscription/addons/remove?addonCode=answers_boost_s
     * Removes an addon from the current subscription.
     */
    @PostMapping("/addons/remove")
    public ResponseEntity<SubscriptionDto> removeAddon(
            @RequestParam Long companyId,
            @RequestParam String addonCode) {
        log.info("POST /api/billing/subscription/addons/remove for company {} addon {}",
                companyId, addonCode);
        return ResponseEntity.ok(subscriptionManagementService.removeAddon(companyId, addonCode));
    }

    /**
     * POST /api/billing/subscription/addons/upgrade
     * Upgrades an addon tier (e.g. answers_boost_s → answers_boost_m).
     * Atomically removes the old and adds the new.
     *
     * Request params: currentAddonCode, newAddonCode
     */
    @PostMapping("/addons/upgrade")
    public ResponseEntity<SubscriptionDto> upgradeAddon(
            @RequestParam Long companyId,
            @RequestParam String currentAddonCode,
            @RequestParam String newAddonCode) {
        log.info("POST /api/billing/subscription/addons/upgrade for company {} {} -> {}",
                companyId, currentAddonCode, newAddonCode);
        return ResponseEntity.ok(
                subscriptionManagementService.upgradeAddon(companyId, currentAddonCode, newAddonCode));
    }

    /**
     * POST /api/billing/subscription/cancel
     * Cancels the subscription at the end of the current billing period.
     * The company retains access until period_end.
     */
    @PostMapping("/cancel")
    public ResponseEntity<SubscriptionDto> cancelSubscription(
            @RequestParam Long companyId) {
        log.info("POST /api/billing/subscription/cancel for company {}", companyId);
        return ResponseEntity.ok(subscriptionManagementService.cancelSubscription(companyId));
    }

    /**
     * POST /api/billing/subscription/reactivate
     * Undoes a pending cancellation (cancel_at_period_end = false).
     * Only works if the subscription has not yet expired.
     */
    @PostMapping("/reactivate")
    public ResponseEntity<SubscriptionDto> reactivateSubscription(
            @RequestParam Long companyId) {
        log.info("POST /api/billing/subscription/reactivate for company {}", companyId);
        return ResponseEntity.ok(subscriptionManagementService.reactivateSubscription(companyId));
    }
}