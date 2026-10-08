package com.clauseiq.ai.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Parses raw LLM output into {@link ContractExtractionResult} and rejects anything malformed. */
@Component
public class ExtractionValidator {

    public static class InvalidExtractionException extends RuntimeException {
        public InvalidExtractionException(String message) {
            super(message);
        }
    }

    private final ObjectMapper objectMapper;

    public ExtractionValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ContractExtractionResult parseAndValidate(String rawJson) {
        ContractExtractionResult result;
        try {
            result = objectMapper.readValue(stripCodeFences(rawJson), ContractExtractionResult.class);
        } catch (JsonProcessingException e) {
            throw new InvalidExtractionException("Response is not valid JSON matching the schema: "
                    + e.getOriginalMessage());
        }
        if (result == null) {
            throw new InvalidExtractionException("Response was empty");
        }
        validate(result);
        return result;
    }

    public void validate(ContractExtractionResult r) {
        List<String> errors = new ArrayList<>();
        checkDate("effectiveDate", r.effectiveDate(), errors);
        checkDate("expirationDate", r.expirationDate(), errors);
        if (r.terminationNoticeDays() != null && (r.terminationNoticeDays() < 0 || r.terminationNoticeDays() > 3650)) {
            errors.add("terminationNoticeDays must be between 0 and 3650");
        }
        if (r.renewalTermMonths() != null && (r.renewalTermMonths() < 0 || r.renewalTermMonths() > 600)) {
            errors.add("renewalTermMonths must be between 0 and 600");
        }
        if (r.parties() != null && r.parties().stream().anyMatch(p -> p == null || p.isBlank())) {
            errors.add("parties must not contain blank entries");
        }
        LocalDate effective = parseDate(r.effectiveDate());
        LocalDate expiration = parseDate(r.expirationDate());
        if (effective != null && expiration != null && expiration.isBefore(effective)) {
            errors.add("expirationDate must not be before effectiveDate");
        }
        if (!errors.isEmpty()) {
            throw new InvalidExtractionException(String.join("; ", errors));
        }
    }

    /** Returns null for blank/absent values; assumes the value already passed validation. */
    public static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void checkDate(String field, String value, List<String> errors) {
        if (value != null && !value.isBlank()) {
            try {
                LocalDate.parse(value.trim());
            } catch (DateTimeParseException e) {
                errors.add(field + " must be an ISO date (yyyy-MM-dd) or null, got '" + value + "'");
            }
        }
    }

    private static String stripCodeFences(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return s;
    }
}
