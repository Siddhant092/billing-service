package com.broadnet.billing.repository;

import com.broadnet.billing.entity.BillingAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAddonsRepository extends JpaRepository<BillingAddon, Long> {

    /**
     * Find addon by code
     */
    Optional<BillingAddon> findByAddonCode(String addonCode);

    /**
     * Find all active addons
     */
    List<BillingAddon> findByIsActiveTrue();

    /**
     * Find active addons by category
     */
    List<BillingAddon> findByCategoryAndIsActiveTrue(String category);

    /**
     * Find addons by category and tier
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.category = :category " +
            "AND ba.tier = :tier AND ba.isActive = true")
    List<BillingAddon> findByCategoryAndTier(
            @Param("category") String category,
            @Param("tier") String tier
    );

    /**
     * Check if addon code exists
     */
    boolean existsByAddonCode(String addonCode);

    /**
     * Find addon by code and active status
     */
    Optional<BillingAddon> findByAddonCodeAndIsActiveTrue(String addonCode);

    /**
     * Find addons by codes (for bulk lookup)
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.addonCode IN :addonCodes AND ba.isActive = true")
    List<BillingAddon> findByAddonCodesAndActive(@Param("addonCodes") List<String> addonCodes);

    /**
     * Find all addons ordered by category and tier
     */
    @Query("SELECT ba FROM BillingAddon ba WHERE ba.isActive = true ORDER BY ba.category, ba.tier")
    List<BillingAddon> findAllActiveSorted();
}