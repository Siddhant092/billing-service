package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result DTO for usage increment operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageIncrementResult {
    
    private boolean success;
    private Integer usageCount;
    private Integer limit;
    private Integer remaining;
    private boolean blocked;
    private String error;
    private String message;
}
