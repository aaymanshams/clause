package com.clauseiq.ai.extraction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strongly typed shape the LLM must return. Dates are ISO-8601 strings (yyyy-MM-dd) and are
 * validated by {@link ExtractionValidator} before anything is persisted.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContractExtractionResult(
        List<String> parties,
        String effectiveDate,
        String expirationDate,
        String renewalPeriod,
        Boolean autoRenewal,
        Integer renewalTermMonths,
        Integer terminationNoticeDays,
        String governingLaw,
        String liabilityCap,
        String paymentTerms) {
}
