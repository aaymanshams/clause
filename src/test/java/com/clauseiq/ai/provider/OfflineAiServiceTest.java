package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OfflineAiServiceTest {

    private final OfflineAiService ai = new OfflineAiService();

    @Test
    void embeddingsAreNormalisedWithTheConfiguredDimensions() {
        float[] v = ai.generateEmbedding("termination notice period");
        assertThat(v).hasSize(AiService.EMBEDDING_DIMENSIONS);
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(ai.generateEmbedding("")).hasSize(AiService.EMBEDDING_DIMENSIONS);
    }

    @Test
    void similarTextScoresHigherThanUnrelatedText() {
        float[] q = ai.generateEmbedding("termination notice days");
        double related = dot(q, ai.generateEmbedding("Either party may terminate with 90 days notice of termination"));
        double unrelated = dot(q, ai.generateEmbedding("Invoices are payable in euros to the supplier bank account"));
        assertThat(related).isGreaterThan(unrelated);
    }

    @Test
    void extractsCommonClauseWording() {
        var r = ai.extractContractData("""
                This Agreement is effective as of March 5, 2024 between Initech LLC (the "Customer") and Hooli Inc.
                It expires on 2026-03-04. It will automatically renew for successive one (1) year terms.
                Either party may terminate on sixty (60) days' prior written notice.
                The total aggregate liability of either party shall not exceed USD 1,000,000.
                Payment is due within thirty (30) days of receipt of invoice.
                This Agreement is governed by the laws of the State of Delaware.
                """);
        assertThat(r.parties()).containsExactly("Initech LLC", "Hooli Inc");
        assertThat(r.effectiveDate()).isEqualTo("2024-03-05");
        assertThat(r.expirationDate()).isEqualTo("2026-03-04");
        assertThat(r.autoRenewal()).isTrue();
        assertThat(r.renewalTermMonths()).isEqualTo(12);
        assertThat(r.terminationNoticeDays()).isEqualTo(60);
        assertThat(r.liabilityCap()).contains("USD 1,000,000");
        assertThat(r.paymentTerms()).contains("thirty (30) days");
        assertThat(r.governingLaw()).isEqualTo("Delaware");
    }

    @Test
    void recognisesEndsOnAsExpiryAndStripsPartyDescriptors() {
        var r = ai.extractContractData("""
                This Agreement is effective as of January 1, 2025 between Acme Corporation, a Delaware corporation, and Hooli Inc.
                The subscription term ends on November 30, 2026.
                """);
        assertThat(r.parties()).containsExactly("Acme Corporation", "Hooli Inc");
        assertThat(r.expirationDate()).isEqualTo("2026-11-30");
    }

    @Test
    void answersAreExtractiveAndCitedOrRefused() {
        var ctx = List.of(new RetrievedChunk(1L, 1L, "a.pdf", 0, 1,
                "Either party may terminate this agreement with 45 days written notice.", 0.7));
        assertThat(ai.generateAnswer("What is the termination notice?", ctx))
                .contains("45 days written notice").endsWith("[S1]");
        assertThat(ai.generateAnswer("Who won the football match?", ctx)).isEqualTo(Prompts.NO_ANSWER);
    }

    private static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
