package com.clauseiq.contract;

import com.clauseiq.ai.extraction.ExtractedContractData;
import com.clauseiq.ai.risk.RiskFinding;
import com.clauseiq.ai.risk.RiskResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ContractDtos {

    private ContractDtos() {
    }

    public record ContractSummary(
            Long id,
            String name,
            Contract.Status status,
            Contract.ExtractionStatus extractionStatus,
            long sizeBytes,
            Integer pageCount,
            Integer chunkCount,
            int riskCount,
            RiskFinding.Severity highestRisk,
            Instant uploadedAt,
            Instant processedAt) {
    }

    public record ExtractedDataView(
            List<String> parties,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            String renewalPeriod,
            Boolean autoRenewal,
            Integer renewalTermMonths,
            Integer terminationNoticeDays,
            String governingLaw,
            String liabilityCap,
            String paymentTerms) {

        static ExtractedDataView from(ExtractedContractData d) {
            return new ExtractedDataView(d.getPartyList(), d.getEffectiveDate(), d.getExpirationDate(),
                    d.getRenewalPeriod(), d.getAutoRenewal(), d.getRenewalTermMonths(), d.getTerminationNoticeDays(),
                    d.getGoverningLaw(), d.getLiabilityCap(), d.getPaymentTerms());
        }
    }

    public record RiskView(RiskFinding.Type type, RiskFinding.Severity severity, String description) {
        static RiskView from(RiskResult r) {
            return new RiskView(r.getType(), r.getSeverity(), r.getDescription());
        }
    }

    public record ContractDetail(
            ContractSummary contract,
            String message,
            ExtractedDataView extractedData,
            List<RiskView> risks) {
    }

    public record RiskReport(Long contractId, List<RiskView> risks) {
    }
}
