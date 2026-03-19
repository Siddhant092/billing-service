package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.*;
import com.broadnet.billing.entity.*;
import com.broadnet.billing.exception.DuplicateResourceException;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.repository.*;
import com.broadnet.billing.service.PlanManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanManagementServiceImpl implements PlanManagementService {

    private final BillingPlansRepository plansRepository;
    private final BillingPlanLimitsRepository planLimitsRepository;
    private final BillingAddonsRepository addonsRepository;
    private final BillingAddonDeltasRepository addonDeltasRepository;
    private final BillingStripePricesRepository stripePricesRepository;
    private final CompanyBillingRepository companyBillingRepository;

    // -------------------------------------------------------------------------
    // Plans
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PlanDto> getAllActivePlans(BillingPlanLimit.BillingInterval billingInterval) {
        List<BillingPlan> plans = plansRepository.findByIsActiveTrueAndIsEnterpriseFalse();
        // Also include enterprise plans
        List<BillingPlan> enterprise = plansRepository.findByIsEnterpriseTrue();
        List<BillingPlan> all = new ArrayList<>(plans);
        all.addAll(enterprise);
        return all.stream()
                .map(p -> convertToPlanDto(p, billingInterval))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanDto getPlanByCode(String planCode) {
        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "planCode", planCode));
        return convertToPlanDto(plan, BillingPlanLimit.BillingInterval.month);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanDto getPlanById(Long planId) {
        BillingPlan plan = plansRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "id", planId));
        return convertToPlanDto(plan, BillingPlanLimit.BillingInterval.month);
    }

    @Override
    @Transactional
    public PlanDto createPlan(PlanDto planDto) {
        if (plansRepository.existsByPlanCode(planDto.getPlanCode())) {
            throw new DuplicateResourceException("BillingPlan", "planCode", planDto.getPlanCode());
        }
        BillingPlan plan = BillingPlan.builder()
                .planCode(planDto.getPlanCode())
                .planName(planDto.getPlanName())
                .description(planDto.getDescription())
                .isActive(true)
                .isEnterprise(Boolean.TRUE.equals(planDto.getIsEnterprise()))
                .supportTier(planDto.getSupportTier() != null
                        ? BillingPlan.SupportTier.valueOf(planDto.getSupportTier()) : null)
                .build();
        BillingPlan saved = plansRepository.save(plan);
        log.info("Created plan: {}", saved.getPlanCode());
        return convertToPlanDto(saved, BillingPlanLimit.BillingInterval.month);
    }

    @Override
    @Transactional
    public PlanDto updatePlanLimit(String planCode, PlanLimitDto limitDto) {
        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "planCode", planCode));

        // Soft-expire the current active limit of this type
        List<BillingPlanLimit> existing = planLimitsRepository.findByPlan_IdAndIsActiveTrue(plan.getId());
        existing.stream()
                .filter(l -> l.getLimitType() == limitDto.getLimitType()
                        && l.getBillingInterval() == limitDto.getBillingInterval())
                .forEach(l -> {
                    l.setIsActive(false);
                    l.setEffectiveTo(limitDto.getEffectiveFrom().minusSeconds(1));
                    planLimitsRepository.save(l);
                });

        // Create new limit row
        BillingPlanLimit newLimit = BillingPlanLimit.builder()
                .plan(plan)
                .limitType(limitDto.getLimitType())
                .limitValue(limitDto.getLimitValue())
                .billingInterval(limitDto.getBillingInterval())
                .isActive(true)
                .effectiveFrom(limitDto.getEffectiveFrom() != null
                        ? limitDto.getEffectiveFrom() : LocalDateTime.now())
                .build();
        planLimitsRepository.save(newLimit);

        log.info("Updated limit for plan={} type={} interval={} value={}",
                planCode, limitDto.getLimitType(), limitDto.getBillingInterval(), limitDto.getLimitValue());
        return convertToPlanDto(plan, limitDto.getBillingInterval());
    }

    @Override
    @Transactional
    public void deactivatePlan(String planCode) {
        BillingPlan plan = plansRepository.findByPlanCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingPlan", "planCode", planCode));
        plan.setIsActive(false);
        plansRepository.save(plan);
        log.info("Deactivated plan: {}", planCode);
    }

    // -------------------------------------------------------------------------
    // Addons
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<AddonDto> getAllActiveAddons(BillingAddon.AddonCategory category) {
        List<BillingAddon> addons = category != null
                ? addonsRepository.findByCategoryAndIsActiveTrue(category)
                : addonsRepository.findByIsActiveTrue();
        return addons.stream().map(this::convertToAddonDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddonDto> getAvailableBoosts(Long companyId,
                                             BillingPlanLimit.BillingInterval billingInterval) {
        List<BillingAddon> addons = addonsRepository.findAllActiveSorted();
        List<String> activeAddonCodes = companyBillingRepository.findByCompanyId(companyId)
                .map(b -> b.getActiveAddonCodes() != null ? b.getActiveAddonCodes() : List.<String>of())
                .orElse(List.of());

        return addons.stream().map(addon -> {
            AddonDto dto = convertToAddonDto(addon);
            dto.setIsPurchased(activeAddonCodes.contains(addon.getAddonCode()));

            // Add pricing
            stripePricesRepository.findByAddonCodeAndInterval(
                            addon.getAddonCode(),
                            BillingStripePrice.BillingInterval.valueOf(billingInterval.name()))
                    .ifPresent(price -> {
                        if (billingInterval == BillingPlanLimit.BillingInterval.month) {
                            dto.setPriceMonthly(price.getAmountCents());
                            dto.setPriceMonthlyFormatted(formatCents(price.getAmountCents()) + "/month");
                        } else {
                            dto.setPriceAnnual(price.getAmountCents());
                            dto.setPriceAnnualFormatted(formatCents(price.getAmountCents()) + "/year");
                        }
                    });
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AddonDto getAddonByCode(String addonCode) {
        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingAddon", "addonCode", addonCode));
        return convertToAddonDto(addon);
    }

    @Override
    @Transactional(readOnly = true)
    public AddonDto getAddonById(Long addonId) {
        BillingAddon addon = addonsRepository.findById(addonId)
                .orElseThrow(() -> new ResourceNotFoundException("BillingAddon", "id", addonId));
        return convertToAddonDto(addon);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddonDto> getAddonsByCodes(List<String> addonCodes) {
        return addonsRepository.findByAddonCodesAndActive(addonCodes)
                .stream().map(this::convertToAddonDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AddonDto createAddon(AddonDto addonDto) {
        if (addonsRepository.existsByAddonCode(addonDto.getAddonCode())) {
            throw new DuplicateResourceException("BillingAddon", "addonCode", addonDto.getAddonCode());
        }
        BillingAddon addon = BillingAddon.builder()
                .addonCode(addonDto.getAddonCode())
                .addonName(addonDto.getAddonName())
                .category(addonDto.getCategory())
                .tier(addonDto.getTier())
                .description(addonDto.getDescription())
                .isActive(true)
                .build();
        BillingAddon saved = addonsRepository.save(addon);
        log.info("Created addon: {}", saved.getAddonCode());
        return convertToAddonDto(saved);
    }

    @Override
    @Transactional
    public AddonDto updateAddonDelta(String addonCode, AddonDeltaDto deltaDto) {
        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingAddon", "addonCode", addonCode));

        // Soft-expire current active delta of this type/interval
        addonDeltasRepository.findByAddon_Id(addon.getId()).stream()
                .filter(d -> d.getDeltaType() == deltaDto.getDeltaType()
                        && d.getBillingInterval() == deltaDto.getBillingInterval()
                        && Boolean.TRUE.equals(d.getIsActive()))
                .forEach(d -> {
                    d.setIsActive(false);
                    d.setEffectiveTo(deltaDto.getEffectiveFrom().minusSeconds(1));
                    addonDeltasRepository.save(d);
                });

        // Create new delta row
        BillingAddonDelta newDelta = BillingAddonDelta.builder()
                .addon(addon)
                .deltaType(deltaDto.getDeltaType())
                .deltaValue(deltaDto.getDeltaValue())
                .billingInterval(deltaDto.getBillingInterval())
                .isActive(true)
                .effectiveFrom(deltaDto.getEffectiveFrom() != null
                        ? deltaDto.getEffectiveFrom() : LocalDateTime.now())
                .build();
        addonDeltasRepository.save(newDelta);

        log.info("Updated delta for addon={} type={} interval={} value={}",
                addonCode, deltaDto.getDeltaType(), deltaDto.getBillingInterval(), deltaDto.getDeltaValue());
        return convertToAddonDto(addon);
    }

    @Override
    @Transactional
    public void deactivateAddon(String addonCode) {
        BillingAddon addon = addonsRepository.findByAddonCode(addonCode)
                .orElseThrow(() -> new ResourceNotFoundException("BillingAddon", "addonCode", addonCode));
        addon.setIsActive(false);
        addonsRepository.save(addon);
        log.info("Deactivated addon: {}", addonCode);
    }

    @Override
    @Transactional
    public int syncStripePrices() {
        // Placeholder — actual implementation calls Stripe API to sync prices
        // and upserts billing_stripe_prices records
        log.info("Stripe price sync triggered (implementation connects to Stripe API)");
        return 0;
    }

    // -------------------------------------------------------------------------
    // Conversion helpers
    // -------------------------------------------------------------------------

    private PlanDto convertToPlanDto(BillingPlan plan,
                                     BillingPlanLimit.BillingInterval billingInterval) {
        LocalDateTime now = LocalDateTime.now();

        PlanDto dto = PlanDto.builder()
                .id(plan.getId())
                .planCode(plan.getPlanCode())
                .planName(plan.getPlanName())
                .description(plan.getDescription())
                .isActive(plan.getIsActive())
                .isEnterprise(plan.getIsEnterprise())
                .supportTier(plan.getSupportTier() != null ? plan.getSupportTier().name() : null)
                .build();

        if (Boolean.TRUE.equals(plan.getIsEnterprise())) {
            dto.setBillingMode("postpaid");
            dto.setPricing(PlanDto.PricingDto.builder()
                    .type("custom")
                    .message("Custom pricing based on usage")
                    .startingFrom("Contact us for pricing")
                    .build());
            dto.setUpgradeAction("contact_us");
        } else {
            // Load active limits
            List<BillingPlanLimit> limits = planLimitsRepository
                    .findActiveLimitsByPlanId(plan.getId(), now);

            limits.stream().filter(l -> l.getBillingInterval() == billingInterval)
                    .forEach(l -> {
                        switch (l.getLimitType()) {
                            case answers_per_period -> dto.setAnswersPerPeriod(l.getLimitValue());
                            case kb_pages           -> dto.setKbPages(l.getLimitValue());
                            case agents             -> dto.setAgents(l.getLimitValue());
                            case users              -> dto.setUsers(l.getLimitValue());
                        }
                    });

            // Load pricing
            Optional<BillingStripePrice> monthlyPrice = stripePricesRepository
                    .findByPlanCodeAndInterval(plan.getPlanCode(),
                            BillingStripePrice.BillingInterval.month);
            Optional<BillingStripePrice> annualPrice = stripePricesRepository
                    .findByPlanCodeAndInterval(plan.getPlanCode(),
                            BillingStripePrice.BillingInterval.year);

            PlanDto.PricingDto pricing = PlanDto.PricingDto.builder()
                    .type("standard")
                    .monthly(monthlyPrice.map(p -> PlanDto.PriceIntervalDto.builder()
                            .amount(p.getAmountCents())
                            .amountFormatted(formatCents(p.getAmountCents()))
                            .stripePriceId(p.getStripePriceId())
                            .build()).orElse(null))
                    .annual(annualPrice.map(p -> {
                        int monthlyAmt = monthlyPrice.map(BillingStripePrice::getAmountCents).orElse(0);
                        String savings = monthlyAmt > 0
                                ? Math.round((1.0 - (p.getAmountCents() / 12.0) / monthlyAmt) * 100) + "%"
                                : null;
                        return PlanDto.PriceIntervalDto.builder()
                                .amount(p.getAmountCents())
                                .amountFormatted(formatCents(p.getAmountCents()))
                                .stripePriceId(p.getStripePriceId())
                                .savings(savings)
                                .build();
                    }).orElse(null))
                    .build();
            dto.setPricing(pricing);
        }

        return dto;
    }

    private AddonDto convertToAddonDto(BillingAddon addon) {
        LocalDateTime now = LocalDateTime.now();

        AddonDto dto = AddonDto.builder()
                .id(addon.getId())
                .addonCode(addon.getAddonCode())
                .addonName(addon.getAddonName())
                .category(addon.getCategory())
                .tier(addon.getTier())
                .description(addon.getDescription())
                .isActive(addon.getIsActive())
                .isPurchased(false)
                .build();

        // Find primary delta (first active month delta)
        addonDeltasRepository.findActiveDeltasByAddonId(addon.getId(), now)
                .stream().findFirst().ifPresent(delta -> {
                    dto.setDeltaType(delta.getDeltaType());
                    dto.setDeltaValue(delta.getDeltaValue());
                });

        return dto;
    }

    private String formatCents(Integer cents) {
        if (cents == null) return "$0.00";
        return String.format("$%.2f", cents / 100.0);
    }
}