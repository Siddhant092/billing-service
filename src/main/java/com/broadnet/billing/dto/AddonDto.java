package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for addon details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddonDto {
    
    private Long id;
    private String addonCode;
    private String addonName;
    private String category; // answers, kb
    private String tier; // small, medium, large
    private String description;
    private Boolean isActive;
    
    // Deltas (what this addon adds)
    private List<AddonDeltaDto> deltas;
    
    // Pricing
    private Integer monthlyPriceCents;
    private Integer yearlyPriceCents;
    private String currency;
}
