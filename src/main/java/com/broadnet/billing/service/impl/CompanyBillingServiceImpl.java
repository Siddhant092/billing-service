package com.broadnet.billing.service.impl;

import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.exception.DuplicateResourceException;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.exception.StripeIntegrationException;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.CompanyBillingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyBillingServiceImpl implements CompanyBillingService {

    private final CompanyBillingRepository companyBillingRepository;

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public CompanyBilling initializeCompanyBilling(Long companyId) {
        // Guard: prevent duplicate initialization
        if (companyBillingRepository.findByCompanyId(companyId).isPresent()) {
            throw new DuplicateResourceException("CompanyBilling", "companyId", companyId);
        }

        // Create Stripe customer
        String stripeCustomerId = createStripeCustomer(companyId);

        return buildAndSave(companyId, stripeCustomerId);
    }

    @Override
    @Transactional
    public CompanyBilling initializeCompanyBilling(Long companyId, String stripeCustomerId) {
        if (companyBillingRepository.findByCompanyId(companyId).isPresent()) {
            throw new DuplicateResourceException("CompanyBilling", "companyId", companyId);
        }
        return buildAndSave(companyId, stripeCustomerId);
    }

    private CompanyBilling buildAndSave(Long companyId, String stripeCustomerId) {
        CompanyBilling billing = CompanyBilling.builder()
                .companyId(companyId)
                .stripeCustomerId(stripeCustomerId)
                .billingMode(CompanyBilling.BillingMode.prepaid)
                .effectiveAnswersLimit(0)
                .effectiveKbPagesLimit(0)
                .effectiveAgentsLimit(0)
                .effectiveUsersLimit(0)
                .answersUsedInPeriod(0)
                .kbPagesTotal(0)
                .agentsTotal(0)
                .usersTotal(0)
                .answersBlocked(false)
                .version(1)
                .build();

        CompanyBilling saved = companyBillingRepository.save(billing);
        log.info("Initialized company billing for companyId={}, stripeCustomerId={}",
                companyId, stripeCustomerId);
        return saved;
    }

    private String createStripeCustomer(Long companyId) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setMetadata(java.util.Map.of("company_id", String.valueOf(companyId)))
                    .build();
            Customer customer = Customer.create(params);
            log.info("Created Stripe customer {} for companyId={}", customer.getId(), companyId);
            return customer.getId();
        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to create Stripe customer for companyId: " + companyId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CompanyBilling getCompanyBilling(Long companyId) {
        return companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyBilling getByStripeCustomerId(String stripeCustomerId) {
        return companyBillingRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "stripeCustomerId", stripeCustomerId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyBilling getCompanyBillingBySubscriptionId(String subscriptionId) {
        return companyBillingRepository.findByStripeSubscriptionId(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "stripeSubscriptionId", subscriptionId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyBilling getCompanyBillingWithLock(Long companyId) {
        return companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", "companyId", companyId));
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public CompanyBilling updateCompanyBilling(CompanyBilling companyBilling) {
        return companyBillingRepository.save(companyBilling);
    }

    // -------------------------------------------------------------------------
    // Stripe sync
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public CompanyBilling syncFromStripe(Long companyId) {
        CompanyBilling billing = getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            log.debug("No Stripe subscription for companyId={}, skipping sync", companyId);
            return billing;
        }

        try {
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Sync subscription state
            billing.setSubscriptionStatus(
                    CompanyBilling.SubscriptionStatus.valueOf(subscription.getStatus().replace("-", "_")));
            billing.setPeriodStart(epochToLocalDateTime(subscription.getCurrentPeriodStart()));
            billing.setPeriodEnd(epochToLocalDateTime(subscription.getCurrentPeriodEnd()));
            billing.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
            if (subscription.getCanceledAt() != null) {
                billing.setCanceledAt(epochToLocalDateTime(subscription.getCanceledAt()));
            }
            billing.setLastSyncAt(LocalDateTime.now());

            CompanyBilling saved = companyBillingRepository.save(billing);
            log.info("Synced billing state from Stripe for companyId={}", companyId);
            return saved;

        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to sync subscription from Stripe for companyId: " + companyId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Subscription checks
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(Long companyId) {
        return companyBillingRepository.hasActiveSubscription(companyId);
    }

    // -------------------------------------------------------------------------
    // Payment failure restrictions
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void applyPaymentFailureRestrictions(Long companyId) {
        CompanyBilling billing = getCompanyBilling(companyId);
        billing.setServiceRestrictedAt(LocalDateTime.now());
        billing.setRestrictionReason(CompanyBilling.RestrictionReason.payment_failed);
        billing.setAnswersBlocked(true);
        companyBillingRepository.save(billing);
        log.info("Applied payment failure restrictions for companyId={}", companyId);
    }

    @Override
    @Transactional
    public void removePaymentFailureRestrictions(Long companyId) {
        CompanyBilling billing = getCompanyBilling(companyId);
        billing.setPaymentFailureDate(null);
        billing.setServiceRestrictedAt(null);
        billing.setRestrictionReason(null);
        billing.setAnswersBlocked(false);
        companyBillingRepository.save(billing);
        log.info("Removed payment failure restrictions for companyId={}", companyId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LocalDateTime epochToLocalDateTime(Long epochSeconds) {
        if (epochSeconds == null) return null;
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDateTime();
    }
}