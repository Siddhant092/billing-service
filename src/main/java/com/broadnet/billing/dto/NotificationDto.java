package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for billing notifications
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    
    private Long id;
    private String notificationType; // payment_failed, limit_warning, subscription_active, etc.
    private String title;
    private String message;
    private String severity; // info, warning, error, success
    
    private String actionUrl;
    private String actionText;
    
    private Boolean isRead;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
