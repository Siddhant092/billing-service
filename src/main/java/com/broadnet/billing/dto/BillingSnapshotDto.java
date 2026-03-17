package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete billing snapshot for dashboard
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSnapshotDto {
    
    // Current Plan
    private String planCode;
    private String planName;
    private String subscriptionStatus;
    private String billingInterval;
    private LocalDateTime renewalDate;
    private Boolean cancelAtPeriodEnd;
    
    // Usage Metrics
    private UsageMetricsDto usageMetrics;
    
    // Payment Method
    private PaymentMethodDto defaultPaymentMethod;
    
    // Upcoming Invoice
    private UpcomingInvoiceDto upcomingInvoice;
    
    // Active Add-ons
    private List<String> activeAddonCodes;
    
    // Pending Changes
    private String pendingPlanCode;
    private LocalDateTime pendingEffectiveDate;
    
    // Alerts/Warnings
    private List<String> alerts;
    
    // Stripe IDs (for debugging)
    private String stripeCustomerId;
    private String stripeSubscriptionId;
}
