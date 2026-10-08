package com.clauseiq.ai.extraction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "extracted_contract_data")
public class ExtractedContractData {

    private static final String PARTY_SEPARATOR = " | ";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    private String parties;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "renewal_period")
    private String renewalPeriod;

    @Column(name = "auto_renewal")
    private Boolean autoRenewal;

    @Column(name = "renewal_term_months")
    private Integer renewalTermMonths;

    @Column(name = "termination_notice_days")
    private Integer terminationNoticeDays;

    @Column(name = "governing_law")
    private String governingLaw;

    @Column(name = "liability_cap")
    private String liabilityCap;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ExtractedContractData() {
    }

    public static ExtractedContractData from(Long tenantId, Long contractId, ContractExtractionResult r) {
        ExtractedContractData d = new ExtractedContractData();
        d.tenantId = tenantId;
        d.contractId = contractId;
        d.parties = r.parties() == null ? null : String.join(PARTY_SEPARATOR, r.parties());
        d.effectiveDate = ExtractionValidator.parseDate(r.effectiveDate());
        d.expirationDate = ExtractionValidator.parseDate(r.expirationDate());
        d.renewalPeriod = trim(r.renewalPeriod(), 255);
        d.autoRenewal = r.autoRenewal();
        d.renewalTermMonths = r.renewalTermMonths();
        d.terminationNoticeDays = r.terminationNoticeDays();
        d.governingLaw = trim(r.governingLaw(), 255);
        d.liabilityCap = trim(r.liabilityCap(), 500);
        d.paymentTerms = trim(r.paymentTerms(), 500);
        return d;
    }

    private static String trim(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    public List<String> getPartyList() {
        return parties == null || parties.isBlank() ? List.of() : Arrays.asList(parties.split(" \\| "));
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getContractId() {
        return contractId;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public String getRenewalPeriod() {
        return renewalPeriod;
    }

    public Boolean getAutoRenewal() {
        return autoRenewal;
    }

    public Integer getRenewalTermMonths() {
        return renewalTermMonths;
    }

    public Integer getTerminationNoticeDays() {
        return terminationNoticeDays;
    }

    public String getGoverningLaw() {
        return governingLaw;
    }

    public String getLiabilityCap() {
        return liabilityCap;
    }

    public String getPaymentTerms() {
        return paymentTerms;
    }
}
