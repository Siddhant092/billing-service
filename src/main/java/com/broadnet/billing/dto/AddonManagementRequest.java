package com.broadnet.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for addon management operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddonManagementRequest {
    
    @NotBlank(message = "Addon code is required")
    private String addonCode;
    
    // For upgrade operations
    private String newAddonCode;
}
