package com.broadnet.billing.service.impl;

import com.broadnet.billing.entity.CompanyBilling;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.repository.CompanyBillingRepository;
import com.broadnet.billing.service.CompanyBillingService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * Implementation of CompanyBillingService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyBillingServiceImpl implements CompanyBillingService {

    private final CompanyBillingRepository companyBillingRepository;

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @Override
    @Transactional
    public CompanyBilling initializeCompanyBilling(Long companyId, String companyName, String email) {
        log.info("Initializing billing for company: {} ({})", companyId, companyName);

        // Check if already exists
        if (companyBillingRepository.findByCompanyId(companyId).isPresent()) {
            log.warn("Billing already initialized for company: {}", companyId);
            return companyBillingRepository.findByCompanyId(companyId).get();
        }

        try {
            // Create Stripe customer
            CustomerCreateParams params = CustomerCreateParams.builder()
                    .setName(companyName)
                    .setEmail(email)
                    .putMetadata("company_id", String.valueOf(companyId))
                    .build();

            Customer customer = Customer.create(params);

            // Create company_billing record
            CompanyBilling billing = CompanyBilling.builder()
                    .companyId(companyId)
                    .stripeCustomerId(customer.getId())
                    .billingMode("prepaid")
                    .effectiveAnswersLimit(0)
                    .effectiveKbPagesLimit(0)
                    .effectiveAgentsLimit(0)
                    .effectiveUsersLimit(0)
                    .answersUsedInPeriod(0)
                    .kbPagesTotal(0)
                    .agentsTotal(0)
                    .usersTotal(0)
                    .answersBlocked(false)
                    .build();

            CompanyBilling saved = companyBillingRepository.save(billing);

            log.info("Billing initialized for company {} with Stripe customer {}",
                    companyId, customer.getId());

            return saved;

        } catch (StripeException e) {
            log.error("Failed to create Stripe customer for company: {}", companyId, e);
            throw new RuntimeException("Failed to initialize billing: " + e.getMessage(), e);
        }
    }

    @Override
    public CompanyBilling getCompanyBilling(Long companyId) {
        return companyBillingRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", companyId));
    }

    @Override
    public CompanyBilling getByStripeCustomerId(String stripeCustomerId) {
        return companyBillingRepository.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CompanyBilling", "stripeCustomerId: " + stripeCustomerId));
    }

    @Override
    @Transactional
    public CompanyBilling getCompanyBillingWithLock(Long companyId) {
        return companyBillingRepository.findByCompanyIdWithLock(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanyBilling", companyId));
    }

    @Override
    @Transactional
    public CompanyBilling updateCompanyBilling(CompanyBilling companyBilling) {
        return companyBillingRepository.save(companyBilling);
    }

    @Override
    @Transactional
    public CompanyBilling syncFromStripe(Long companyId) {
        log.info("Syncing billing state from Stripe for company: {}", companyId);

        CompanyBilling billing = getCompanyBilling(companyId);

        if (billing.getStripeSubscriptionId() == null) {
            log.warn("No subscription found for company: {}", companyId);
            return billing;
        }

        try {
            // Fetch subscription from Stripe
            Subscription subscription = Subscription.retrieve(billing.getStripeSubscriptionId());

            // Update local state
            billing.setSubscriptionStatus(subscription.getStatus());
            billing.setPeriodStart(LocalDateTime.ofEpochSecond(subscription.getCurrentPeriodStart(), 0,
                    java.time.ZoneOffset.UTC));
            billing.setPeriodEnd(LocalDateTime.ofEpochSecond(subscription.getCurrentPeriodEnd(), 0,
                    java.time.ZoneOffset.UTC));
            billing.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
            billing.setLastSyncAt(LocalDateTime.now());

            return companyBillingRepository.save(billing);

        } catch (StripeException e) {
            log.error("Failed to sync from Stripe for company: {}", companyId, e);
            throw new RuntimeException("Failed to sync from Stripe: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean hasActiveSubscription(Long companyId) {
        return companyBillingRepository.hasActiveSubscription(companyId);
    }

    @Override
    @Transactional
    public void applyPaymentFailureRestrictions(Long companyId) {
        log.info("Applying payment failure restrictions for company: {}", companyId);

        CompanyBilling billing = getCompanyBilling(companyId);

        billing.setServiceRestrictedAt(LocalDateTime.now());
        billing.setRestrictionReason("payment_failed");
        billing.setAnswersBlocked(true);

        companyBillingRepository.save(billing);

        log.info("Payment failure restrictions applied for company: {}", companyId);
    }

    @Override
    @Transactional
    public void removePaymentFailureRestrictions(Long companyId) {
        log.info("Removing payment failure restrictions for company: {}", companyId);

        CompanyBilling billing = getCompanyBilling(companyId);

        billing.setServiceRestrictedAt(null);
        billing.setPaymentFailureDate(null);
        billing.setRestrictionReason(null);
        billing.setAnswersBlocked(false);

        companyBillingRepository.save(billing);

        log.info("Payment failure restrictions removed for company: {}", companyId);
    }
}