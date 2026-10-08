package com.clauseiq;

import com.clauseiq.support.IntegrationTestBase;
import com.clauseiq.support.TestDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload -> Tika -> chunks -> pgvector -> extraction -> risks -> search/chat, end to end. */
class ContractPipelineIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerTenant("Pipeline Co");
    }

    @Test
    void pdfUploadIsProcessedExtractedAndRiskAssessed() throws Exception {
        long id = uploadContract(token, "acme-msa.pdf",
                TestDocuments.pdf(TestDocuments.ACME_PAGE_1, TestDocuments.ACME_PAGE_2));

        JsonNode detail = json(mockMvc.perform(get("/api/contracts/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()));
        assertThat(detail.at("/contract/status").asText()).isEqualTo("READY");
        assertThat(detail.at("/contract/extractionStatus").asText()).isEqualTo("SUCCESS");
        assertThat(detail.at("/contract/pageCount").asInt()).isEqualTo(2);
        assertThat(detail.at("/contract/chunkCount").asInt()).isGreaterThan(0);

        JsonNode data = detail.get("extractedData");
        assertThat(data.get("terminationNoticeDays").asInt()).isEqualTo(90);
        assertThat(data.get("effectiveDate").asText()).isEqualTo("2025-01-01");
        assertThat(data.get("expirationDate").asText()).isEqualTo("2027-12-31");
        assertThat(data.get("autoRenewal").asBoolean()).isTrue();
        assertThat(data.get("renewalTermMonths").asInt()).isEqualTo(24);
        assertThat(data.get("governingLaw").asText()).isEqualTo("New York");
        assertThat(data.get("parties").toString()).contains("Acme Corporation").contains("Northwind Traders Ltd");

        List<String> riskTypes = new ArrayList<>();
        detail.get("risks").forEach(r -> riskTypes.add(r.get("type").asText() + ":" + r.get("severity").asText()));
        assertThat(riskTypes).contains("TERMINATION:MEDIUM", "AUTO_RENEWAL:MEDIUM");
        assertThat(riskTypes).noneMatch(r -> r.startsWith("MISSING_LIABILITY_CAP") || r.startsWith("GOVERNING_LAW"));
    }

    @Test
    void chatAnswersWithCitationIncludingRealPageNumber() throws Exception {
        uploadContract(token, "acme-msa.pdf", TestDocuments.pdf(TestDocuments.ACME_PAGE_1, TestDocuments.ACME_PAGE_2));

        JsonNode answer = json(postJson("/api/chat", token,
                Map.of("question", "What is the termination notice period?")).andExpect(status().isOk()));

        assertThat(answer.get("answered").asBoolean()).isTrue();
        assertThat(answer.get("answer").asText()).contains("ninety (90) days");
        JsonNode source = answer.get("sources").get(0);
        assertThat(source.get("contractName").asText()).isEqualTo("acme-msa.pdf");
        assertThat(source.get("pageNumber").asInt()).isEqualTo(2);
        assertThat(source.get("chunkId").asLong()).isPositive();
    }

    @Test
    void chatRefusesWhenContractsDoNotContainTheAnswer() throws Exception {
        uploadContract(token, "acme-msa.pdf", TestDocuments.pdf(TestDocuments.ACME_PAGE_1, TestDocuments.ACME_PAGE_2));

        JsonNode answer = json(postJson("/api/chat", token,
                Map.of("question", "What colour is the office carpet in Reykjavik?")).andExpect(status().isOk()));

        assertThat(answer.get("answered").asBoolean()).isFalse();
        assertThat(answer.get("answer").asText())
                .isEqualTo("I could not find enough information in the uploaded contracts to answer this question.");
        assertThat(answer.get("sources")).isEmpty();
    }

    @Test
    void docxUploadIsProcessedWithoutInventedPageNumbers() throws Exception {
        long id = uploadContract(token, "globex.docx", TestDocuments.docx(TestDocuments.GLOBEX_TEXT.split("\n\n")));

        JsonNode detail = json(mockMvc.perform(get("/api/contracts/" + id).header("Authorization", bearer(token))));
        assertThat(detail.at("/contract/status").asText()).isEqualTo("READY");
        assertThat(detail.at("/extractedData/terminationNoticeDays").asInt()).isEqualTo(15);
        List<String> riskTypes = new ArrayList<>();
        detail.get("risks").forEach(r -> riskTypes.add(r.get("type").asText()));
        assertThat(riskTypes).contains("UNLIMITED_LIABILITY", "GOVERNING_LAW");

        JsonNode search = json(postJson("/api/search", token, Map.of("query", "termination notice")));
        assertThat(search.get("results")).isNotEmpty();
        assertThat(search.get("results").get(0).get("pageNumber").isNull()).isTrue();
    }

    @Test
    void unsupportedOrSpoofedFilesAreRejected() throws Exception {
        upload(token, "notes.txt", "hello".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        upload(token, "fake.pdf", "this is not really a pdf".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest());
        upload(token, "empty.pdf", new byte[0]).andExpect(status().isBadRequest());
        upload(token, "renamed.docx", TestDocuments.pdf("a pdf pretending to be docx"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownContractReturns404() throws Exception {
        mockMvc.perform(get("/api/contracts/999999").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }
}
