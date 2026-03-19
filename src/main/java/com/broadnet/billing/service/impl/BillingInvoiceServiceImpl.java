package com.broadnet.billing.service.impl;

import com.broadnet.billing.dto.InvoiceDto;
import com.broadnet.billing.entity.BillingInvoice;
import com.broadnet.billing.exception.ResourceNotFoundException;
import com.broadnet.billing.exception.StripeIntegrationException;
import com.broadnet.billing.repository.BillingInvoicesRepository;
import com.broadnet.billing.service.BillingInvoiceService;
import com.stripe.exception.StripeException;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingInvoiceServiceImpl implements BillingInvoiceService {

    private final BillingInvoicesRepository invoicesRepository;

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto getInvoiceByStripeId(String stripeInvoiceId) {
        return invoicesRepository.findByStripeInvoiceId(stripeInvoiceId)
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingInvoice", "stripeInvoiceId", stripeInvoiceId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvoiceDto> getInvoicesByCompanyId(Long companyId, int page, int size) {
        return invoicesRepository
                .findByCompanyId(companyId, PageRequest.of(page, size))
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoicesByStatus(Long companyId, BillingInvoice.InvoiceStatus status) {
        return invoicesRepository.findByCompanyIdAndStatus(companyId, status)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getPaidInvoices(Long companyId) {
        return invoicesRepository.findPaidInvoicesByCompanyId(companyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getUnpaidInvoices(Long companyId) {
        return invoicesRepository.findUnpaidInvoicesByCompanyId(companyId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoicesByDateRange(Long companyId,
                                                   LocalDateTime startDate,
                                                   LocalDateTime endDate) {
        return invoicesRepository.findByCompanyIdAndDateRange(companyId, startDate, endDate)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getOverdueInvoices() {
        return invoicesRepository.findOverdueInvoices(LocalDateTime.now())
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceDto> getInvoicesDueSoon(int days) {
        LocalDateTime now = LocalDateTime.now();
        return invoicesRepository.findInvoicesDueSoon(now, now.plusDays(days))
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalAmountDue(Long companyId) {
        return invoicesRepository.getTotalAmountDueByCompanyId(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer getTotalAmountPaid(Long companyId, LocalDateTime startDate, LocalDateTime endDate) {
        return invoicesRepository.getTotalAmountPaidByCompanyIdAndPeriod(companyId, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceDto getLatestInvoice(Long companyId) {
        return invoicesRepository.findLatestByCompanyId(companyId)
                .map(this::toDto)
                .orElse(null);
    }

    // -------------------------------------------------------------------------
    // Webhook-driven create / update
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public InvoiceDto createOrUpdateFromStripe(Long companyId, String stripeInvoiceId) {
        try {
            Invoice stripeInvoice = Invoice.retrieve(stripeInvoiceId);
            return upsertFromStripeInvoice(companyId, stripeInvoice);
        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to retrieve invoice " + stripeInvoiceId + " from Stripe", e);
        }
    }

    @Override
    @Transactional
    public void updateInvoiceStatus(String stripeInvoiceId, BillingInvoice.InvoiceStatus status) {
        BillingInvoice invoice = invoicesRepository.findByStripeInvoiceId(stripeInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingInvoice", "stripeInvoiceId", stripeInvoiceId));
        invoice.setStatus(status);
        invoicesRepository.save(invoice);
        log.debug("Updated invoice {} status to {}", stripeInvoiceId, status);
    }

    @Override
    @Transactional
    public void markAsPaid(String stripeInvoiceId, LocalDateTime paidAt, Integer amountPaid) {
        BillingInvoice invoice = invoicesRepository.findByStripeInvoiceId(stripeInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingInvoice", "stripeInvoiceId", stripeInvoiceId));
        invoice.setStatus(BillingInvoice.InvoiceStatus.paid);
        invoice.setPaidAt(paidAt);
        invoice.setAmountPaid(amountPaid);
        invoicesRepository.save(invoice);
        log.info("Marked invoice {} as paid, amount={}", stripeInvoiceId, amountPaid);
    }

    // -------------------------------------------------------------------------
    // PDF download
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadInvoicePdf(Long companyId, String stripeInvoiceId) {
        BillingInvoice invoice = invoicesRepository.findByStripeInvoiceId(stripeInvoiceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BillingInvoice", "stripeInvoiceId", stripeInvoiceId));

        if (invoice.getInvoicePdfUrl() == null && invoice.getHostedInvoiceUrl() == null) {
            throw new ResourceNotFoundException("Invoice PDF not available for: " + stripeInvoiceId);
        }

        String pdfUrl = invoice.getInvoicePdfUrl() != null
                ? invoice.getInvoicePdfUrl() : invoice.getHostedInvoiceUrl();

        try (InputStream in = new URL(pdfUrl).openStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new StripeIntegrationException(
                    "Failed to download PDF for invoice: " + stripeInvoiceId, e);
        }
    }

    @Override
    public void resendInvoice(String stripeInvoiceId) {
        try {
            Invoice invoice = Invoice.retrieve(stripeInvoiceId);
            invoice.sendInvoice();
            log.info("Resent invoice: {}", stripeInvoiceId);
        } catch (StripeException e) {
            throw new StripeIntegrationException(
                    "Failed to resend invoice: " + stripeInvoiceId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Sync from Stripe
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public int syncPastInvoicesFromStripe(Long companyId) {
        // Retrieve all invoices from Stripe for this customer would need stripe_customer_id
        // This is a simplified implementation — full impl would page through Stripe API
        log.info("Stripe invoice sync requested for companyId={}", companyId);
        return 0;
    }

    // -------------------------------------------------------------------------
    // Internal upsert
    // -------------------------------------------------------------------------

    /**
     * Called by webhook handlers. Upserts invoice record by stripe_invoice_id.
     */
    public InvoiceDto upsertFromStripeInvoice(Long companyId, Invoice si) {
        BillingInvoice invoice = invoicesRepository.findByStripeInvoiceId(si.getId())
                .orElseGet(() -> BillingInvoice.builder()
                        .companyId(companyId)
                        .stripeInvoiceId(si.getId())
                        .build());

        invoice.setInvoiceNumber(si.getNumber());
        invoice.setStatus(mapStripeStatus(si.getStatus()));
        invoice.setAmountDue(si.getAmountDue() != null ? si.getAmountDue().intValue() : 0);
        invoice.setAmountPaid(si.getAmountPaid() != null ? si.getAmountPaid().intValue() : 0);
        invoice.setSubtotal(si.getSubtotal() != null ? si.getSubtotal().intValue() : 0);
        invoice.setTaxAmount(si.getTax() != null ? si.getTax().intValue() : 0);
        invoice.setTotal(si.getTotal() != null ? si.getTotal().intValue() : 0);
        invoice.setCurrency(si.getCurrency() != null ? si.getCurrency() : "usd");
        invoice.setInvoiceDate(epochToLdt(si.getCreated()));
        invoice.setDueDate(epochToLdt(si.getDueDate()));
        invoice.setPaidAt(si.getStatusTransitions() != null
                ? epochToLdt(si.getStatusTransitions().getPaidAt()) : null);
        invoice.setPeriodStart(epochToLdt(si.getPeriodStart()));
        invoice.setPeriodEnd(epochToLdt(si.getPeriodEnd()));
        invoice.setHostedInvoiceUrl(si.getHostedInvoiceUrl());
        invoice.setInvoicePdfUrl(si.getInvoicePdf());

        // Serialize line items as JSON string
        if (si.getLines() != null && si.getLines().getData() != null) {
            invoice.setLineItems(buildLineItemsJson(si));
        } else {
            invoice.setLineItems("[]");
        }

        BillingInvoice saved = invoicesRepository.save(invoice);
        log.debug("Upserted invoice {} for companyId={}", si.getId(), companyId);
        return toDto(saved);
    }

    // -------------------------------------------------------------------------
    // Converters
    // -------------------------------------------------------------------------

    private InvoiceDto toDto(BillingInvoice i) {
        return InvoiceDto.builder()
                .id(i.getId())
                .companyId(i.getCompanyId())
                .stripeInvoiceId(i.getStripeInvoiceId())
                .invoiceNumber(i.getInvoiceNumber())
                .status(i.getStatus())
                .amountDue(i.getAmountDue())
                .amountDueFormatted(formatCents(i.getAmountDue()))
                .amountPaid(i.getAmountPaid())
                .amountPaidFormatted(formatCents(i.getAmountPaid()))
                .subtotal(i.getSubtotal())
                .taxAmount(i.getTaxAmount())
                .total(i.getTotal())
                .totalFormatted(formatCents(i.getTotal()))
                .currency(i.getCurrency())
                .invoiceDate(i.getInvoiceDate())
                .dueDate(i.getDueDate())
                .paidAt(i.getPaidAt())
                .periodStart(i.getPeriodStart())
                .periodEnd(i.getPeriodEnd())
                .hostedInvoiceUrl(i.getHostedInvoiceUrl())
                .invoicePdfUrl(i.getInvoicePdfUrl())
                .lineItems(i.getLineItems())
                .createdAt(i.getCreatedAt())
                .build();
    }

    private BillingInvoice.InvoiceStatus mapStripeStatus(String status) {
        if (status == null) return BillingInvoice.InvoiceStatus.draft;
        return switch (status) {
            case "draft"          -> BillingInvoice.InvoiceStatus.draft;
            case "open"           -> BillingInvoice.InvoiceStatus.open;
            case "paid"           -> BillingInvoice.InvoiceStatus.paid;
            case "void"           -> BillingInvoice.InvoiceStatus.void_;
            case "uncollectible"  -> BillingInvoice.InvoiceStatus.uncollectible;
            default               -> BillingInvoice.InvoiceStatus.draft;
        };
    }

    private String buildLineItemsJson(Invoice si) {
        // Build simple JSON array of line items for storage
        StringBuilder sb = new StringBuilder("[");
        List<InvoiceLineItem> items = si.getLines().getData();
        for (int i = 0; i < items.size(); i++) {
            InvoiceLineItem item = items.get(i);
            sb.append(String.format("{\"description\":\"%s\",\"amount\":%d,\"currency\":\"%s\"}",
                    item.getDescription() != null ? item.getDescription().replace("\"", "'") : "",
                    item.getAmount() != null ? item.getAmount() : 0,
                    item.getCurrency() != null ? item.getCurrency() : "usd"));
            if (i < items.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private LocalDateTime epochToLdt(Long epoch) {
        if (epoch == null) return null;
        return Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDateTime();
    }

    private String formatCents(Integer cents) {
        if (cents == null) return "$0.00";
        return String.format("$%.2f", cents / 100.0);
    }
}