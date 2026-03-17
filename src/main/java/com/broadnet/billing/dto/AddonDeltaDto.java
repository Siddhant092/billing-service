package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for addon delta (limit increase)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddonDeltaDto {
    
    private Long id;
    private String deltaType; // answers_per_period, kb_pages
    private Integer deltaValue;
    private String billingInterval; // month, year
    private Boolean isActive;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
