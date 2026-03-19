package com.broadnet.billing.controller;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.BillingNotification;
import com.broadnet.billing.entity.BillingPlanLimit;
import com.broadnet.billing.service.BillingDashboardService;
import com.broadnet.billing.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Overview dashboard APIs — Architecture Plan UI API Design §1.
 *
 * §1.1  GET  /api/billing/notifications
 * §1.2  GET  /api/billing/current-plan
 * §1.3  GET  /api/billing/billing-snapshot
 * §1.4  GET  /api/billing/usage-metrics
 * §1.5  GET  /api/billing/available-boosts
 * §1.6  GET  /api/billing/overview
 * §1.7  PATCH /api/billing/notifications/{id}/read
 *        PATCH /api/billing/notifications/read-all
 * §1.8  DELETE /api/billing/notifications/{id}
 * §1.9  POST /api/billing/boosts/purchase
 */
@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingOverviewController {

    private final BillingDashboardService dashboardService;
    private final CheckoutService checkoutService;

    // ------------------------------------------------------------------
    // §1.1 GET /api/billing/notifications
    // Architecture Plan response: { notifications[], unreadCount, totalCount }
    // Query params: unread (bool, default true), limit (int, default 20),
    //               type (NotificationType, optional)
    // ------------------------------------------------------------------
    @GetMapping("/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestAttribute("companyId") Long companyId,
            @RequestParam(name = "unread", defaultValue = "true") boolean unread,
            @RequestParam(name = "limit",  defaultValue = "20")   int limit,
            @RequestParam(name = "type",   required = false)
            BillingNotification.NotificationType type) {

        List<NotificationDto> notifications =
                dashboardService.getNotifications(companyId, unread, type, limit);

        long unreadCount = notifications.stream()
                .filter(n -> Boolean.FALSE.equals(n.getIsRead())).count();

        return ResponseEntity.ok(Map.of(
                "notifications", notifications,
                "unreadCount",   unreadCount,
                "totalCount",    notifications.size()));
    }

    // ------------------------------------------------------------------
    // §1.7 PATCH /api/billing/notifications/{notificationId}/read
    // ------------------------------------------------------------------
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Map<String, Object>> markNotificationRead(
            @PathVariable Long notificationId) {

        dashboardService.markNotificationAsRead(notificationId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // PATCH /api/billing/notifications/read-all  (bulk)
    @PatchMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllNotificationsRead(
            @RequestAttribute("companyId") Long companyId) {

        int count = dashboardService.markAllNotificationsAsRead(companyId);
        return ResponseEntity.ok(Map.of("success", true, "markedRead", count));
    }

    // ------------------------------------------------------------------
    // §1.8 DELETE /api/billing/notifications/{notificationId}
    // ------------------------------------------------------------------
    @DeleteMapping("/notifications/{notificationId}")
    public ResponseEntity<Map<String, Object>> dismissNotification(
            @PathVariable Long notificationId) {

        dashboardService.dismissNotification(notificationId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ------------------------------------------------------------------
    // §1.2 GET /api/billing/current-plan
    // ------------------------------------------------------------------
    @GetMapping("/current-plan")
    public ResponseEntity<CurrentPlanDto> getCurrentPlan(
            @RequestAttribute("companyId") Long companyId) {

        return ResponseEntity.ok(dashboardService.getCurrentPlan(companyId));
    }

    // ------------------------------------------------------------------
    // §1.3 GET /api/billing/billing-snapshot
    // ------------------------------------------------------------------
    @GetMapping("/billing-snapshot")
    public ResponseEntity<BillingSnapshotDto> getBillingSnapshot(
            @RequestAttribute("companyId") Long companyId) {

        return ResponseEntity.ok(dashboardService.getBillingSnapshot(companyId));
    }

    // ------------------------------------------------------------------
    // §1.4 GET /api/billing/usage-metrics
    // ------------------------------------------------------------------
    @GetMapping("/usage-metrics")
    public ResponseEntity<UsageMetricsDto> getUsageMetrics(
            @RequestAttribute("companyId") Long companyId) {

        return ResponseEntity.ok(dashboardService.getUsageMetrics(companyId));
    }

    // ------------------------------------------------------------------
    // §1.5 GET /api/billing/available-boosts
    // ------------------------------------------------------------------
    @GetMapping("/available-boosts")
    public ResponseEntity<Map<String, Object>> getAvailableBoosts(
            @RequestAttribute("companyId") Long companyId,
            @RequestParam(name = "billingInterval", defaultValue = "month") String billingInterval) {

        BillingPlanLimit.BillingInterval interval =
                BillingPlanLimit.BillingInterval.valueOf(billingInterval);

        List<AddonDto> boosts = dashboardService.getAvailableBoosts(companyId, interval);
        return ResponseEntity.ok(Map.of("boosts", boosts));
    }

    // ------------------------------------------------------------------
    // §1.6 GET /api/billing/overview
    // Aggregator — calls current-plan, billing-snapshot, usage-metrics,
    // notifications, available-boosts in one response.
    // ------------------------------------------------------------------
    @GetMapping("/overview")
    public ResponseEntity<BillingOverviewDto> getOverview(
            @RequestAttribute("companyId") Long companyId,
            @RequestParam(name = "billingInterval", defaultValue = "month") String billingInterval) {

        BillingPlanLimit.BillingInterval interval =
                BillingPlanLimit.BillingInterval.valueOf(billingInterval);

        return ResponseEntity.ok(dashboardService.getOverview(companyId, interval));
    }

    // ------------------------------------------------------------------
    // §1.9 POST /api/billing/boosts/purchase
    // Purchase an addon (boost) via Stripe Checkout Session.
    // Body: { addonCode, billingInterval }
    // ------------------------------------------------------------------
    @PostMapping("/boosts/purchase")
    public ResponseEntity<CheckoutSessionResponse> purchaseBoost(
            @RequestAttribute("companyId") Long companyId,
            @RequestBody Map<String, String> body) {

        String addonCode      = body.get("addonCode");
        String intervalStr    = body.getOrDefault("billingInterval", "month");
        BillingPlanLimit.BillingInterval interval =
                BillingPlanLimit.BillingInterval.valueOf(intervalStr);

        return ResponseEntity.ok(
                checkoutService.createAddonCheckoutSession(companyId, addonCode, interval));
    }
}