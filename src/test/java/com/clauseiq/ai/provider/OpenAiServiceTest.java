package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiException;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.extraction.ContractExtractionResult;
import com.clauseiq.ai.extraction.ExtractionValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiServiceTest {

    private static final String VALID_JSON = """
            {"parties":["Acme","Globex"],"effectiveDate":"2025-01-01","expirationDate":"2026-12-31",
             "renewalPeriod":null,"autoRenewal":false,"renewalTermMonths":null,"terminationNoticeDays":90,
             "governingLaw":"New York","liabilityCap":"12 months of fees","paymentTerms":"Net 30"}
            """;

    @Mock
    private OpenAiClient client;

    private OpenAiService service;

    @BeforeEach
    void setUp() {
        service = new OpenAiService(client, new ExtractionValidator(new ObjectMapper()), 0.2);
    }

    @Test
    void validExtractionIsReturnedWithoutRetry() {
        when(client.chat(eq(Prompts.EXTRACTION_SYSTEM), anyString(), eq(true))).thenReturn(VALID_JSON);

        ContractExtractionResult result = service.extractContractData("contract text");

        assertThat(result.terminationNoticeDays()).isEqualTo(90);
        assertThat(result.parties()).containsExactly("Acme", "Globex");
        verify(client, times(1)).chat(anyString(), anyString(), anyBoolean());
    }

    @Test
    void invalidOutputIsRetriedOnceWithTheValidationError() {
        when(client.chat(eq(Prompts.EXTRACTION_SYSTEM), anyString(), eq(true)))
                .thenReturn("{\"effectiveDate\":\"1st of January\"}")
                .thenReturn(VALID_JSON);

        ContractExtractionResult result = service.extractContractData("contract text");

        assertThat(result.effectiveDate()).isEqualTo("2025-01-01");
        verify(client).chat(eq(Prompts.EXTRACTION_SYSTEM), contains("effectiveDate must be an ISO date"), eq(true));
    }

    @Test
    void secondInvalidOutputFailsTheExtraction() {
        when(client.chat(anyString(), anyString(), eq(true))).thenReturn("not json at all");

        assertThatThrownBy(() -> service.extractContractData("contract text"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("after retry");
        verify(client, times(2)).chat(anyString(), anyString(), anyBoolean());
    }

    @Test
    void answerWithEmptyContextNeverCallsTheLlm() {
        assertThat(service.generateAnswer("anything?", List.of())).isEqualTo(Prompts.NO_ANSWER);
        verify(client, never()).chat(anyString(), anyString(), anyBoolean());
    }

    @Test
    void answerPromptContainsOnlyTheRetrievedContextWithLabels() {
        var chunk = new RetrievedChunk(7L, 3L, "acme.pdf", 0, 2, "Notice period is 90 days.", 0.9);
        when(client.chat(eq(Prompts.RAG_SYSTEM), anyString(), eq(false))).thenReturn("It is 90 days [S1].");

        assertThat(service.generateAnswer("What is the notice period?", List.of(chunk))).isEqualTo("It is 90 days [S1].");
        verify(client).chat(eq(Prompts.RAG_SYSTEM),
                contains("[S1] (contract: acme.pdf, page 2)\n<excerpt>\nNotice period is 90 days.\n</excerpt>"), eq(false));
    }

    @Test
    void embeddingsWithWrongDimensionsAreRejected() {
        when(client.embed(anyList())).thenReturn(List.of(new float[3]));
        assertThatThrownBy(() -> service.generateEmbeddings(List.of("x"))).isInstanceOf(AiException.class);
    }
}
