package com.broadnet.billing.controller;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.service.BillingDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Billing Dashboard
 * Base path: /api/billing/dashboard
 *
 * Exposes read-only billing state to the frontend:
 * - Snapshot (plan + usage + payment method + upcoming invoice + alerts)
 * - Usage metrics (live counters vs limits)
 * - Invoice list + PDF download
 * - Payment methods
 * - Notifications
 * - Usage history (for charts)
 * - Entitlement audit log
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/dashboard")
@RequiredArgsConstructor
public class BillingDashboardController {

    private final BillingDashboardService billingDashboardService;

    /**
     * GET /api/billing/dashboard/snapshot
     * Returns the full billing snapshot: plan, usage, payment method,
     * upcoming invoice, alerts and pending changes in one call.
     */
    @GetMapping("/snapshot")
    public ResponseEntity<BillingSnapshotDto> getBillingSnapshot(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/dashboard/snapshot for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getBillingSnapshot(companyId));
    }

    /**
     * GET /api/billing/dashboard/usage
     * Live usage counters: answers used/limit/remaining/%, KB pages, agents, users.
     */
    @GetMapping("/usage")
    public ResponseEntity<UsageMetricsDto> getUsageMetrics(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/dashboard/usage for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getUsageMetrics(companyId));
    }

    /**
     * GET /api/billing/dashboard/plan
     * Current plan details: plan code, interval, renewal date, cancel flag.
     */
    @GetMapping("/plan")
    public ResponseEntity<CurrentPlanDto> getCurrentPlan(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/dashboard/plan for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getCurrentPlan(companyId));
    }

    /**
     * GET /api/billing/dashboard/invoices?page=0&size=10
     * Paginated invoice history fetched from Stripe.
     */
    @GetMapping("/invoices")
    public ResponseEntity<Page<InvoiceDto>> getInvoices(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("GET /api/billing/dashboard/invoices for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getInvoices(companyId, page, size));
    }

    /**
     * GET /api/billing/dashboard/invoices/upcoming
     * Preview of the next invoice (amount, period, proration).
     */
    @GetMapping("/invoices/upcoming")
    public ResponseEntity<UpcomingInvoiceDto> getUpcomingInvoice(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/dashboard/invoices/upcoming for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getUpcomingInvoice(companyId));
    }

    /**
     * GET /api/billing/dashboard/invoices/{invoiceId}/pdf
     * Downloads the invoice PDF as binary.
     */
    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @RequestParam Long companyId,
            @PathVariable String invoiceId) {
        log.info("GET /api/billing/dashboard/invoices/{}/pdf for company {}", invoiceId, companyId);
        byte[] pdf = billingDashboardService.downloadInvoicePdf(companyId, invoiceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-" + invoiceId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * GET /api/billing/dashboard/payment-methods
     * List of saved payment methods from Stripe.
     */
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodDto>> getPaymentMethods(
            @RequestParam Long companyId) {
        log.info("GET /api/billing/dashboard/payment-methods for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getPaymentMethods(companyId));
    }

    /**
     * GET /api/billing/dashboard/notifications?unreadOnly=false
     * In-app billing alerts and warnings (limit reached, payment failed, etc.)
     */
    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        log.info("GET /api/billing/dashboard/notifications for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getNotifications(companyId, unreadOnly));
    }

    /**
     * PATCH /api/billing/dashboard/notifications/{notificationId}/read
     * Marks a notification as read.
     */
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<Void> markNotificationAsRead(
            @PathVariable Long notificationId) {
        log.info("PATCH /api/billing/dashboard/notifications/{}/read", notificationId);
        billingDashboardService.markNotificationAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/billing/dashboard/usage-history?days=30
     * Daily usage breakdown for charts (answers per day over last N days).
     */
    @GetMapping("/usage-history")
    public ResponseEntity<UsageHistoryDto> getUsageHistory(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "30") int days) {
        log.info("GET /api/billing/dashboard/usage-history for company {}", companyId);
        return ResponseEntity.ok(billingDashboardService.getUsageHistory(companyId, days));
    }

    /**
     * GET /api/billing/dashboard/entitlement-history?page=0&size=20
     * Audit log of all entitlement changes (plan changes, addon add/remove).
     */
    @GetMapping("/entitlement-history")
    public ResponseEntity<Page<EntitlementHistoryDto>> getEntitlementHistory(
            @RequestParam Long companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/billing/dashboard/entitlement-history for company {}", companyId);
        return ResponseEntity.ok(
                billingDashboardService.getEntitlementHistory(companyId, page, size));
    }
}