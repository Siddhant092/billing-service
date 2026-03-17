package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.EntitlementService;
import com.broadnet.billing.service.PlanManagementService;
import com.stripe.exception.StripeException;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.param.PriceListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of PlanManagementService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanManagementServiceImpl implements PlanManagementService {

    private final BillingPlansRepository plansRepository;
    private final BillingPlanLimitsRepository planLimitsRepository;
    private final BillingAddonsRepository addonsRepository;
    private final BillingAddonDeltasRepository addonDeltasRepository;
    private final BillingStripePricesRepository stripePricesRepository;
    private final EntitlementService entitlementService;

    @Override
    public List<PlanDto> getAllActivePlans(String billingInterval) {
        log.info("Fetching all active plans for interval: {}", billingInterval);

        List<BillingPlan> plans = plansRepository.findByIsActiveTrue();

        return plans.stream()
                .map(plan -> convertToPlanDto(plan, billingInterval))
                .collect(Collectors.toList());
    }

    @Override
    public PlanDto getPlanByCode(String planCode) {
        log.info("Fetching plan: {}", planCode);

        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planCode));

        return convertToPlanDto(plan, null);
    }

    @Override
    @Transactional
    public PlanDto createPlan(PlanDto planDto) {
        log.info("Creating plan: {}", planDto.getPlanCode());

        // Create plan entity
        BillingPlan plan = BillingPlan.builder()
                .planCode(planDto.getPlanCode())
                .planName(planDto.getPlanName())
                .description(planDto.getDescription())
                .isActive(true)
                .isEnterprise(planDto.getIsEnterprise())
                .supportTier(planDto.getSupportTier())
                .build();

        plan = plansRepository.save(plan);

        // Create plan limits
        if (planDto.getLimits() != null) {
            for (PlanLimitDto limitDto : planDto.getLimits()) {
                BillingPlanLimit limit = BillingPlanLimit.builder()
                        .planId(plan.getId())
                        .limitType(limitDto.getLimitType())
                        .limitValue(limitDto.getLimitValue())
                        .billingInterval(limitDto.getBillingInterval())
                        .isActive(true)
                        .effectiveFrom(LocalDateTime.now())
                        .build();

                planLimitsRepository.save(limit);
            }
        }

        log.info("Plan created: {}", plan.getPlanCode());

        return convertToPlanDto(plan, null);
    }

    @Override
    @Transactional
    public PlanDto updatePlanLimit(String planCode, PlanLimitDto limitDto) {
        log.info("Updating limits for plan: {}", planCode);

        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        // Find existing limit
        List<BillingPlanLimit> existingLimits = planLimitsRepository
                .findActiveLimitsByPlanIdAndInterval(
                        plan.getId(), limitDto.getBillingInterval(), LocalDateTime.now());

        BillingPlanLimit existingLimit = existingLimits.stream()
                .filter(l -> l.getLimitType().equals(limitDto.getLimitType()))
                .findFirst()
                .orElse(null);

        if (existingLimit != null) {
            // Update existing limit
            existingLimit.setLimitValue(limitDto.getLimitValue());
            planLimitsRepository.save(existingLimit);
        } else {
            // Create new limit
            BillingPlanLimit newLimit = BillingPlanLimit.builder()
                    .planId(plan.getId())
                    .limitType(limitDto.getLimitType())
                    .limitValue(limitDto.getLimitValue())
                    .billingInterval(limitDto.getBillingInterval())
                    .isActive(true)
                    .effectiveFrom(LocalDateTime.now())
                    .build();

            planLimitsRepository.save(newLimit);
        }

        // Recompute entitlements for all companies on this plan
        entitlementService.recomputeEntitlementsForPlan(planCode);

        return convertToPlanDto(plan, limitDto.getBillingInterval());
    }

    @Override
    @Transactional
    public void deactivatePlan(String planCode) {
        log.info("Deactivating plan: {}", planCode);

        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        plan.setIsActive(false);
        plansRepository.save(plan);

        log.info("Plan deactivated: {}", planCode);
    }

    @Override
    public List<AddonDto> getAllActiveAddons(String category) {
        log.info("Fetching all active addons for category: {}", category);

        List<BillingAddon> addons;
        if (category != null) {
            addons = addonsRepository.findByCategoryAndIsActiveTrue(category);
        } else {
            addons = addonsRepository.findByIsActiveTrue();
        }

        return addons.stream()
                .map(this::convertToAddonDto)
                .collect(Collectors.toList());
    }

    @Override
    public AddonDto getAddonByCode(String addonCode) {
        log.info("Fetching addon: {}", addonCode);

        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new RuntimeException("Addon not found: " + addonCode));

        return convertToAddonDto(addon);
    }

    @Override
    @Transactional
    public AddonDto createAddon(AddonDto addonDto) {
        log.info("Creating addon: {}", addonDto.getAddonCode());

        // Create addon entity
        BillingAddon addon = BillingAddon.builder()
                .addonCode(addonDto.getAddonCode())
                .addonName(addonDto.getAddonName())
                .category(addonDto.getCategory())
                .tier(addonDto.getTier())
                .description(addonDto.getDescription())
                .isActive(true)
                .build();

        addon = addonsRepository.save(addon);

        // Create addon deltas
        if (addonDto.getDeltas() != null) {
            for (AddonDeltaDto deltaDto : addonDto.getDeltas()) {
                BillingAddonDelta delta = BillingAddonDelta.builder()
                        .addonId(addon.getId())
                        .deltaType(deltaDto.getDeltaType())
                        .deltaValue(deltaDto.getDeltaValue())
                        .billingInterval(deltaDto.getBillingInterval())
                        .isActive(true)
                        .effectiveFrom(LocalDateTime.now())
                        .build();

                addonDeltasRepository.save(delta);
            }
        }

        log.info("Addon created: {}", addon.getAddonCode());

        return convertToAddonDto(addon);
    }

    @Override
    @Transactional
    public AddonDto updateAddonDelta(String addonCode, AddonDeltaDto deltaDto) {
        log.info("Updating delta for addon: {}", addonCode);

        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new RuntimeException("Addon not found"));

        // Find existing delta
        List<BillingAddonDelta> existingDeltas = addonDeltasRepository
                .findActiveDeltasByAddonIds(List.of(addon.getId()), LocalDateTime.now());

        BillingAddonDelta existingDelta = existingDeltas.stream()
                .filter(d -> d.getDeltaType().equals(deltaDto.getDeltaType()) &&
                        d.getBillingInterval().equals(deltaDto.getBillingInterval()))
                .findFirst()
                .orElse(null);

        if (existingDelta != null) {
            // Update existing delta
            existingDelta.setDeltaValue(deltaDto.getDeltaValue());
            addonDeltasRepository.save(existingDelta);
        } else {
            // Create new delta
            BillingAddonDelta newDelta = BillingAddonDelta.builder()
                    .addonId(addon.getId())
                    .deltaType(deltaDto.getDeltaType())
                    .deltaValue(deltaDto.getDeltaValue())
                    .billingInterval(deltaDto.getBillingInterval())
                    .isActive(true)
                    .effectiveFrom(LocalDateTime.now())
                    .build();

            addonDeltasRepository.save(newDelta);
        }

        return convertToAddonDto(addon);
    }

    @Override
    @Transactional
    public void deactivateAddon(String addonCode) {
        log.info("Deactivating addon: {}", addonCode);

        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new RuntimeException("Addon not found"));

        addon.setIsActive(false);
        addonsRepository.save(addon);

        log.info("Addon deactivated: {}", addonCode);
    }

    @Override
    @Transactional
    public int syncStripePrices() {
        log.info("Syncing prices from Stripe");

        try {
            PriceListParams params = PriceListParams.builder()
                    .setActive(true)
                    .setLimit(100L)
                    .build();

            List<Price> prices = Price.list(params).getData();
            int synced = 0;

            for (Price price : prices) {
                Product product = Product.retrieve(price.getProduct());

                if (product.getMetadata() == null) continue;

                String type = product.getMetadata().get("type");
                String planCode = product.getMetadata().get("plan_code");
                String addonCode = product.getMetadata().get("addon_code");

                if (type == null) continue;

                // Check if price already exists
                if (stripePricesRepository.existsByStripePriceId(price.getId())) {
                    continue;
                }

                BillingStripePrice stripePrice = BillingStripePrice.builder()
                        .stripePriceId(price.getId())
                        .lookupKey(price.getLookupKey() != null ? price.getLookupKey() : price.getId())
                        .planId("plan".equals(type) ? resolvePlanId(planCode) : null)
                        .addonId("addon".equals(type) ? resolveAddonId(addonCode) : null)
                        .amountCents(price.getUnitAmount() != null ? price.getUnitAmount().intValue() : 0)
                        .currency(price.getCurrency())
                        .billingInterval(price.getRecurring() != null ?
                                price.getRecurring().getInterval() : "month")
                        .isActive(true)
                        .build();

                stripePricesRepository.save(stripePrice);
                synced++;
            }

            log.info("Synced {} prices from Stripe", synced);
            return synced;

        } catch (StripeException e) {
            log.error("Failed to sync prices from Stripe", e);
            throw new RuntimeException("Failed to sync prices: " + e.getMessage());
        }
    }

    private PlanDto convertToPlanDto(BillingPlan plan, String billingInterval) {
        // Get limits
        List<BillingPlanLimit> limits;
        if (billingInterval != null) {
            limits = planLimitsRepository.findActiveLimitsByPlanIdAndInterval(
                    plan.getId(), billingInterval, LocalDateTime.now());
        } else {
            limits = planLimitsRepository.findByPlanIdAndIsActiveTrue(plan.getId());
        }

        List<PlanLimitDto> limitDtos = limits.stream()
                .map(limit -> PlanLimitDto.builder()
                        .id(limit.getId())
                        .limitType(limit.getLimitType())
                        .limitValue(limit.getLimitValue())
                        .billingInterval(limit.getBillingInterval())
                        .isActive(limit.getIsActive())
                        .effectiveFrom(limit.getEffectiveFrom())
                        .effectiveTo(limit.getEffectiveTo())
                        .build())
                .collect(Collectors.toList());

        return PlanDto.builder()
                .id(plan.getId())
                .planCode(plan.getPlanCode())
                .planName(plan.getPlanName())
                .description(plan.getDescription())
                .isActive(plan.getIsActive())
                .isEnterprise(plan.getIsEnterprise())
                .supportTier(plan.getSupportTier())
                .limits(limitDtos)
                .build();
    }

    private AddonDto convertToAddonDto(BillingAddon addon) {
        // Get deltas
        List<BillingAddonDelta> deltas = addonDeltasRepository
                .findActiveDeltasByAddonIds(List.of(addon.getId()), LocalDateTime.now());

        List<AddonDeltaDto> deltaDtos = deltas.stream()
                .map(delta -> AddonDeltaDto.builder()
                        .id(delta.getId())
                        .deltaType(delta.getDeltaType())
                        .deltaValue(delta.getDeltaValue())
                        .billingInterval(delta.getBillingInterval())
                        .isActive(delta.getIsActive())
                        .effectiveFrom(delta.getEffectiveFrom())
                        .effectiveTo(delta.getEffectiveTo())
                        .build())
                .collect(Collectors.toList());

        return AddonDto.builder()
                .id(addon.getId())
                .addonCode(addon.getAddonCode())
                .addonName(addon.getAddonName())
                .category(addon.getCategory())
                .tier(addon.getTier())
                .description(addon.getDescription())
                .isActive(addon.getIsActive())
                .deltas(deltaDtos)
                .build();
    }

    @Override
    public PlanDto getPlanById(Long planId) {
        log.debug("Fetching plan by ID: {}", planId);

        BillingPlan plan = plansRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found with ID: " + planId));

        return convertToPlanDto(plan, null);
    }

    @Override
    public AddonDto getAddonById(Long addonId) {
        log.debug("Fetching addon by ID: {}", addonId);

        BillingAddon addon = addonsRepository.findById(addonId)
                .orElseThrow(() -> new RuntimeException("Addon not found with ID: " + addonId));

        return convertToAddonDto(addon);
    }

    @Override
    public List<AddonDto> getAddonsByCodes(List<String> addonCodes) {
        log.debug("Fetching addons by codes: {}", addonCodes);

        if (addonCodes == null || addonCodes.isEmpty()) {
            return new ArrayList<>();
        }

        List<BillingAddon> addons = addonsRepository.findByAddonCodesAndActive(addonCodes);

        return addons.stream()
                .map(this::convertToAddonDto)
                .collect(Collectors.toList());
    }

    /** Resolve plan DB ID from plan code, returns null if not found */
    private Long resolvePlanId(String planCode) {
        if (planCode == null) return null;
        return plansRepository.findByPlanCode(planCode).map(BillingPlan::getId).orElse(null);
    }

    /** Resolve addon DB ID from addon code, returns null if not found */
    private Long resolveAddonId(String addonCode) {
        if (addonCode == null) return null;
        return addonsRepository.findByAddonCode(addonCode).map(BillingAddon::getId).orElse(null);
    }
}