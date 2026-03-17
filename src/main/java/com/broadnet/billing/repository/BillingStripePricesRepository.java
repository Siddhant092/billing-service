package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingStripePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingStripePricesRepository extends JpaRepository<BillingStripePrice, Long> {

    /**
     * Find price by Stripe price ID
     */
    Optional<BillingStripePrice> findByStripePriceId(String stripePriceId);

    /**
     * Find price by lookup key
     */
    Optional<BillingStripePrice> findByLookupKey(String lookupKey);

    /**
     * Find all active prices
     */
    List<BillingStripePrice> findByIsActiveTrue();

    /**
     * Find prices for a specific plan
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.planId = :planId AND bsp.isActive = true")
    List<BillingStripePrice> findActivePricesByPlanId(@Param("planId") Long planId);

    /**
     * Find price for plan and interval
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.planId = :planId " +
            "AND bsp.billingInterval = :interval AND bsp.isActive = true")
    Optional<BillingStripePrice> findByPlanIdAndInterval(
            @Param("planId") Long planId,
            @Param("interval") String interval
    );

    /**
     * Find prices for a specific addon
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.addonId = :addonId AND bsp.isActive = true")
    List<BillingStripePrice> findActivePricesByAddonId(@Param("addonId") Long addonId);

    /**
     * Find price for addon and interval
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp WHERE bsp.addonId = :addonId " +
            "AND bsp.billingInterval = :interval AND bsp.isActive = true")
    Optional<BillingStripePrice> findByAddonIdAndInterval(
            @Param("addonId") Long addonId,
            @Param("interval") String interval
    );

    /**
     * Check if Stripe price ID exists
     */
    boolean existsByStripePriceId(String stripePriceId);

    /**
     * Check if lookup key exists
     */
    boolean existsByLookupKey(String lookupKey);

    /**
     * Find prices by currency
     */
    List<BillingStripePrice> findByCurrencyAndIsActiveTrue(String currency);

    // ⚠️ MISSING METHODS - Add these to fix CheckoutServiceImpl

    /**
     * Find price by plan code and interval
     * Required by CheckoutServiceImpl.createCheckoutSession()
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp " +
            "JOIN BillingPlan bp ON bsp.planId = bp.id " +
            "WHERE bp.planCode = :planCode " +
            "AND bsp.billingInterval = :interval " +
            "AND bsp.isActive = true")
    Optional<BillingStripePrice> findByPlanCodeAndInterval(
            @Param("planCode") String planCode,
            @Param("interval") String interval
    );

    /**
     * Find price by addon code and interval
     * Required by CheckoutServiceImpl.createAddonCheckoutSession()
     */
    @Query("SELECT bsp FROM BillingStripePrice bsp " +
            "JOIN BillingAddon ba ON bsp.addonId = ba.id " +
            "WHERE ba.addonCode = :addonCode " +
            "AND bsp.billingInterval = :interval " +
            "AND bsp.isActive = true")
    Optional<BillingStripePrice> findByAddonCodeAndInterval(
            @Param("addonCode") String addonCode,
            @Param("interval") String interval
    );
}