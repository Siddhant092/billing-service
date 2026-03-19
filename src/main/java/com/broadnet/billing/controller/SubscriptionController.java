package com.broadnet.billing.controller;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.BillingPlanLimit;
import com.broadnet.billing.service.BillingDashboardService;
import com.broadnet.billing.service.CheckoutService;
import com.broadnet.billing.service.SubscriptionManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Subscription management and checkout APIs.
 * Architecture Plan §1 (Checkout) and §2 (Subscription Management).
 *
 * §1    POST /api/billing/checkout/create-session
 *        GET  /api/billing/checkout/success
 * §2.1  GET  /api/billing/subscription/plans
 * §2.2  POST /api/billing/subscription/change-plan
 *        POST /api/billing/subscription/preview-change
 * §2.3  POST /api/billing/subscription/cancel
 * §2.4  POST /api/billing/subscription/reactivate
 *        GET  /api/billing/subscription
 *        POST /api/billing/subscription/addons/add
 *        POST /api/billing/subscription/addons/remove
 *        POST /api/billing/subscription/addons/upgrade
 *        GET  /api/billing/entitlement-history
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionManagementService subscriptionService;
    private final CheckoutService               checkoutService;
    private final BillingDashboardService       dashboardService;

    // ------------------------------------------------------------------
    // §1 Checkout API
    // POST /api/billing/checkout/create-session
    // Architecture Plan body: { plan_code, billing_interval, success_url, cancel_url }
    // Architecture Plan response: { checkout_session_id, url }
    // ------------------------------------------------------------------
    @PostMapping("/checkout/create-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody CheckoutSessionRequest request) {

        return ResponseEntity.ok(checkoutService.createCheckoutSession(companyId, request));
    }

    // GET /api/billing/checkout/success?sessionId=cs_xxx
    // Architecture Plan: "Most subscription updates come via webhooks.
    // This is just for immediate UI feedback."
    @GetMapping("/checkout/success")
    public ResponseEntity<Map<String, Object>> checkoutSuccess(
            @RequestParam String sessionId) {

        boolean success = checkoutService.handleCheckoutSuccess(sessionId);
        return ResponseEntity.ok(Map.of(
                "success",   success,
                "sessionId", sessionId,
                "message",   success
                        ? "Checkout completed. Your subscription is being activated."
                        : "Checkout session not yet complete."));
    }

    // ------------------------------------------------------------------
    // GET /api/billing/subscription
    // ------------------------------------------------------------------
    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionDto> getCurrentSubscription(
            @RequestAttribute("companyId") Long companyId) {

        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(companyId));
    }

    // ------------------------------------------------------------------
    // §2.1 GET /api/billing/subscription/plans
    // Query param: billingInterval (month|year, default month)
    // ------------------------------------------------------------------
    @GetMapping("/subscription/plans")
    public ResponseEntity<AvailablePlansDto> getAvailablePlans(
            @RequestAttribute("companyId") Long companyId,
            @RequestParam(name = "billingInterval", defaultValue = "month") String billingInterval) {

        BillingPlanLimit.BillingInterval interval =
                BillingPlanLimit.BillingInterval.valueOf(billingInterval);

        return ResponseEntity.ok(subscriptionService.getAvailablePlans(companyId, interval));
    }

    // ------------------------------------------------------------------
    // §2.2 POST /api/billing/subscription/change-plan
    // Body: { planCode, billingInterval, prorationBehavior }
    // ------------------------------------------------------------------
    @PostMapping("/subscription/change-plan")
    public ResponseEntity<SubscriptionDto> changePlan(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody PlanChangeRequest request) {

        return ResponseEntity.ok(subscriptionService.changePlan(companyId, request));
    }

    // POST /api/billing/subscription/preview-change
    // Body: { planCode, billingInterval }
    @PostMapping("/subscription/preview-change")
    public ResponseEntity<SubscriptionPreviewDto> previewPlanChange(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, String> body) {

        BillingPlanLimit.BillingInterval interval =
                BillingPlanLimit.BillingInterval.valueOf(
                        body.getOrDefault("billingInterval", "month"));

        return ResponseEntity.ok(subscriptionService.previewPlanChange(
                companyId, body.get("planCode"), interval));
    }

    // ------------------------------------------------------------------
    // §2.3 POST /api/billing/subscription/cancel
    // Body: { cancelAtPeriodEnd: true|false }
    // Architecture Plan response: { success, canceledAt, cancelAtPeriodEnd, periodEnd, message }
    // ------------------------------------------------------------------
    @PostMapping("/subscription/cancel")
    public ResponseEntity<Map<String, Object>> cancelSubscription(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, Object> body) {

        boolean cancelAtPeriodEnd =
                Boolean.TRUE.equals(body.getOrDefault("cancelAtPeriodEnd", true));

        SubscriptionDto result =
                subscriptionService.cancelSubscription(companyId, cancelAtPeriodEnd);

        String periodEnd = result.getPeriodEnd() != null
                ? result.getPeriodEnd().toLocalDate().toString() : "";

        return ResponseEntity.ok(Map.of(
                "success",           true,
                "cancelAtPeriodEnd", cancelAtPeriodEnd,
                "periodEnd",         periodEnd,
                "message",           cancelAtPeriodEnd
                        ? "Subscription will cancel at period end. You'll retain access until " + periodEnd + "."
                        : "Subscription canceled immediately."));
    }

    // ------------------------------------------------------------------
    // §2.4 POST /api/billing/subscription/reactivate
    // Architecture Plan response: { success, message, renewalDate }
    // ------------------------------------------------------------------
    @PostMapping("/subscription/reactivate")
    public ResponseEntity<Map<String, Object>> reactivateSubscription(
            @RequestAttribute("companyId") Long companyId) {

        SubscriptionDto result = subscriptionService.reactivateSubscription(companyId);
        return ResponseEntity.ok(Map.of(
                "success",     true,
                "message",     "Subscription reactivated successfully.",
                "renewalDate", result.getPeriodEnd() != null
                        ? result.getPeriodEnd().toString() : ""));
    }

    // ------------------------------------------------------------------
    // Addon management
    // POST /api/billing/subscription/addons/add  — body: { addonCode }
    // POST /api/billing/subscription/addons/remove — body: { addonCode }
    // POST /api/billing/subscription/addons/upgrade — body: { currentAddonCode, newAddonCode }
    // ------------------------------------------------------------------
    @PostMapping("/subscription/addons/add")
    public ResponseEntity<SubscriptionDto> addAddon(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(
                subscriptionService.addAddon(companyId, body.get("addonCode")));
    }

    @PostMapping("/subscription/addons/remove")
    public ResponseEntity<SubscriptionDto> removeAddon(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(
                subscriptionService.removeAddon(companyId, body.get("addonCode")));
    }

    @PostMapping("/subscription/addons/upgrade")
    public ResponseEntity<SubscriptionDto> upgradeAddon(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(subscriptionService.upgradeAddon(
                companyId,
                body.get("currentAddonCode"),
                body.get("newAddonCode")));
    }

    // ------------------------------------------------------------------
    // GET /api/billing/entitlement-history
    // Audit log of plan/limit/addon changes.
    // ------------------------------------------------------------------
    @GetMapping("/entitlement-history")
    public ResponseEntity<Object> getEntitlementHistory(
            @RequestAttribute("companyId") Long companyId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(dashboardService.getEntitlementHistory(companyId, page, size));
    }
}