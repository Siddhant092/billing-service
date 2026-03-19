package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Notification response DTO.
 * Architecture Plan §1.1 GET /api/billing/notifications response item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private Long id;
    private Long companyId;
    private BillingNotification.NotificationType type;
    private String title;
    private String message;
    private BillingNotification.Severity severity;
    private Boolean isRead;
    private String actionUrl;
    private String actionText;
    private String stripeEventId;
    private Map<String, Object> metadata;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}