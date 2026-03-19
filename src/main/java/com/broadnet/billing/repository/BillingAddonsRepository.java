package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for billing_addons table.
 *
 * CHANGES FROM ORIGINAL:
 * - findByCategoryAndIsActiveTrue: param type changed from String to BillingAddon.AddonCategory
 * - findByCategoryAndTier: both param types changed to their respective enums
 * - findByAddonCodesAndActive JPQL query: unchanged logic, confirmed correct
 * - findAllActiveSorted JPQL: unchanged logic, confirmed correct
 */
@Repository
public interface BillingAddonsRepository extends JpaRepository<BillingAddon, Long> {

    /** Find addon by its unique code (e.g. "answers_boost_m"). */
    Optional<BillingAddon> findByAddonCode(String addonCode);

    /** Find all active addons. */
    List<BillingAddon> findByIsActiveTrue();

    /**
     * Find active addons by category.
     * FIXED: category param type changed to BillingAddon.AddonCategory (entity uses @Enumerated).
     */
    List<BillingAddon> findByCategoryAndIsActiveTrue(BillingAddon.AddonCategory category);

    /**
     * Find active addons by category and tier.
     * FIXED: both param types changed to their respective enums.
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.category = :category " +
            "AND ba.tier = :tier AND ba.isActive = true")
    List<BillingAddon> findByCategoryAndTier(
            @Param("category") BillingAddon.AddonCategory category,
            @Param("tier") BillingAddon.AddonTier tier
    );

    /** Check if addon code already exists (for uniqueness validation). */
    boolean existsByAddonCode(String addonCode);

    /** Find addon by code only if active. */
    Optional<BillingAddon> findByAddonCodeAndIsActiveTrue(String addonCode);

    /**
     * Bulk lookup of addons by codes.
     * Used by EntitlementService to resolve active addon codes from company_billing.active_addon_codes.
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.addonCode IN :addonCodes AND ba.isActive = true")
    List<BillingAddon> findByAddonCodesAndActive(@Param("addonCodes") List<String> addonCodes);

    /**
     * Find all active addons sorted by category then tier.
     * Used by the public plan/addon listing API.
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.isActive = true ORDER BY ba.category, ba.tier")
    List<BillingAddon> findAllActiveSorted();
}