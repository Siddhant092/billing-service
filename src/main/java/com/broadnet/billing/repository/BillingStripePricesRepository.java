package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingStripePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_stripe_prices table.
 *
 * CHANGES FROM ORIGINAL:
 * - ALL queries referencing bsp.planId changed to bsp.plan.id
 * - ALL queries referencing bsp.addonId changed to bsp.addon.id
 *   (entity fields are now @ManyToOne BillingPlan plan / BillingAddon addon)
 * - billingInterval param type changed to BillingStripePrice.BillingInterval enum throughout
 * - findActivePricesByPlanId: uses bsp.plan.id
 * - findByPlanIdAndInterval: uses bsp.plan.id + enum
 * - findActivePricesByAddonId: uses bsp.addon.id
 * - findByAddonIdAndInterval: uses bsp.addon.id + enum
 * - findByPlanCodeAndInterval: JOIN rewritten to use bsp.plan.planCode (was bsp.planId JOIN BillingPlan)
 * - findByAddonCodeAndInterval: JOIN rewritten to use bsp.addon.addonCode (was bsp.addonId JOIN BillingAddon)
 * - findByCurrencyAndIsActiveTrue: unchanged
 */
@Repository
public interface BillingStripePricesRepository extends JpaRepository<BillingStripePrice, Long> {

    /** Find price by Stripe price ID (e.g. "price_xxx"). */
    Optional<BillingStripePrice> findByStripePriceId(String stripePriceId);

    /**
     * Find price by lookup key (e.g. "plan_starter_monthly").
     * Used by webhook handler to identify which plan/addon a subscription item maps to.
     */
    Optional<BillingStripePrice> findByLookupKey(String lookupKey);

    /** Find all active prices. */
    List<BillingStripePrice> findByIsActiveTrue();

    /**
     * Find all active prices for a specific plan.
     * Returns both monthly and annual prices.
     * FIXED: bsp.planId → bsp.plan.id
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.plan.id = :planId AND bsp.isActive = true")
    List<BillingStripePrice> findActivePricesByPlanId(@Param("planId") Long planId);

    /**
     * Find price for a plan and billing interval.
     * Used by CheckoutService to get the Stripe price ID for a given plan+interval.
     * FIXED: bsp.planId → bsp.plan.id; interval → enum
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.plan.id = :planId " +
            "AND bsp.billingInterval = :interval AND bsp.isActive = true")
    Optional<BillingStripePrice> findByPlanIdAndInterval(
            @Param("planId") Long planId,
            @Param("interval") BillingStripePrice.BillingInterval interval
    );

    /**
     * Find all active prices for a specific addon.
     * FIXED: bsp.addonId → bsp.addon.id
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.addon.id = :addonId AND bsp.isActive = true")
    List<BillingStripePrice> findActivePricesByAddonId(@Param("addonId") Long addonId);

    /**
     * Find price for an addon and billing interval.
     * FIXED: bsp.addonId → bsp.addon.id; interval → enum
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.addon.id = :addonId " +
            "AND bsp.billingInterval = :interval AND bsp.isActive = true")
    Optional<BillingStripePrice> findByAddonIdAndInterval(
            @Param("addonId") Long addonId,
            @Param("interval") BillingStripePrice.BillingInterval interval
    );

    /** Check if a Stripe price ID is already registered. */
    boolean existsByStripePriceId(String stripePriceId);

    /** Check if a lookup key is already registered. */
    boolean existsByLookupKey(String lookupKey);

    /** Find all active prices for a given currency. */
    List<BillingStripePrice> findByCurrencyAndIsActiveTrue(String currency);

    /**
     * Find price by plan code and billing interval.
     * Used by CheckoutService.createCheckoutSession() — avoids a separate plan lookup.
     * FIXED: navigates through bsp.plan.planCode instead of a JOIN on bare planId.
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp " +
            "WHERE bsp.plan.planCode = :planCode " +
            "AND bsp.billingInterval = :interval " +
            "AND bsp.isActive = true")
    Optional<BillingStripePrice> findByPlanCodeAndInterval(
            @Param("planCode") String planCode,
            @Param("interval") BillingStripePrice.BillingInterval interval
    );

    /**
     * Find price by addon code and billing interval.
     * Used by CheckoutService.createAddonCheckoutSession().
     * FIXED: navigates through bsp.addon.addonCode instead of a JOIN on bare addonId.
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp " +
            "WHERE bsp.addon.addonCode = :addonCode " +
            "AND bsp.billingInterval = :interval " +
            "AND bsp.isActive = true")
    Optional<BillingStripePrice> findByAddonCodeAndInterval(
            @Param("addonCode") String addonCode,
            @Param("interval") BillingStripePrice.BillingInterval interval
    );
}