package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for plan limit details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanLimitDto {
    
    private Long id;
    private String limitType; // answers_per_period, kb_pages, agents, users
    private Integer limitValue;
    private String billingInterval; // month, year
    private Boolean isActive;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
