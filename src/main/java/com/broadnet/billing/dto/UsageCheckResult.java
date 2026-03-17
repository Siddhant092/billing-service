package com.broadnet.billing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result DTO for usage limit checks
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageCheckResult {
    
    private boolean allowed;
    private Integer currentUsage;
    private Integer limit;
    private Integer remaining;
    private String message;
    private String usageType; // answers, kb_pages, agents, users
}
