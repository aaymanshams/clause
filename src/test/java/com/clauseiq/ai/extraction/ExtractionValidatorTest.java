package com.clauseiq.ai.extraction;

import com.clauseiq.ai.extraction.ExtractionValidator.InvalidExtractionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator(new ObjectMapper());

    @Test
    void acceptsValidJsonIncludingCodeFencesAndUnknownFields() {
        var result = validator.parseAndValidate("""
                ```json
                {"parties":["A","B"],"effectiveDate":"2025-01-01","terminationNoticeDays":30,"extra":"ignored"}
                ```""");
        assertThat(result.parties()).containsExactly("A", "B");
        assertThat(result.terminationNoticeDays()).isEqualTo(30);
    }

    @Test
    void acceptsNullsForUnknownFields() {
        var result = validator.parseAndValidate("{\"parties\":[],\"effectiveDate\":null,\"liabilityCap\":null}");
        assertThat(result.effectiveDate()).isNull();
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> validator.parseAndValidate("Sure! Here are the terms..."))
                .isInstanceOf(InvalidExtractionException.class);
    }

    @Test
    void rejectsWrongTypes() {
        assertThatThrownBy(() -> validator.parseAndValidate("{\"terminationNoticeDays\":\"ninety\"}"))
                .isInstanceOf(InvalidExtractionException.class);
    }

    @Test
    void rejectsNonIsoDatesAndImpossibleValues() {
        assertThatThrownBy(() -> validator.parseAndValidate("{\"effectiveDate\":\"Jan 1 2025\"}"))
                .hasMessageContaining("effectiveDate");
        assertThatThrownBy(() -> validator.parseAndValidate("{\"terminationNoticeDays\":-5}"))
                .hasMessageContaining("terminationNoticeDays");
        assertThatThrownBy(() -> validator.parseAndValidate(
                "{\"effectiveDate\":\"2025-01-01\",\"expirationDate\":\"2024-01-01\"}"))
                .hasMessageContaining("expirationDate must not be before");
        assertThatThrownBy(() -> validator.parseAndValidate("{\"parties\":[\"A\",\" \"]}"))
                .hasMessageContaining("parties");
    }
}
