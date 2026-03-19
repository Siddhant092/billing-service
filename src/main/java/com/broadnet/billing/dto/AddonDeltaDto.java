package com.broadnet.billing.dto;

import com.broadnet.billing.entity.BillingAddonDelta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Request DTO for updating an addon delta.
 * Used by PlanManagementService.updateAddonDelta().
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddonDeltaDto {

    private BillingAddonDelta.DeltaType deltaType;
    private Integer deltaValue;
    private BillingAddonDelta.BillingInterval billingInterval;
    private LocalDateTime effectiveFrom;
}