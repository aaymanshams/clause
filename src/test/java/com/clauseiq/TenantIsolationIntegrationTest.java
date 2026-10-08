package com.clauseiq;

import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.embedding.VectorRepository;
import com.clauseiq.document.TextChunker.TextChunk;
import com.clauseiq.support.IntegrationTestBase;
import com.clauseiq.support.TestDocuments;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The core guarantee of ClauseIQ: Tenant A can never see Tenant B's contracts, chunks, search
 * results or RAG context — through any endpoint.
 */
class TenantIsolationIntegrationTest extends IntegrationTestBase {

    @Autowired
    private VectorRepository vectorRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String tokenA;
    private String tokenB;
    private long contractA;
    private long contractB;

    @BeforeEach
    void setUp() throws Exception {
        tokenA = registerTenant("Acme Corporation");
        tokenB = registerTenant("Globex Industries");
        contractA = uploadContract(tokenA, "acme-msa.pdf",
                TestDocuments.pdf(TestDocuments.ACME_PAGE_1, TestDocuments.ACME_PAGE_2));
        contractB = uploadContract(tokenB, "globex-supply.pdf", TestDocuments.pdf(TestDocuments.GLOBEX_TEXT));
    }

    @Test
    @DisplayName("Contract list only contains the caller's own contracts")
    void listIsTenantScoped() throws Exception {
        JsonNode listA = json(mockMvc.perform(get("/api/contracts").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()));
        assertThat(ids(listA)).containsExactly(contractA);

        JsonNode listB = json(mockMvc.perform(get("/api/contracts").header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk()));
        assertThat(ids(listB)).containsExactly(contractB);
    }

    @Test
    @DisplayName("Tenant A cannot read, analyse, reprocess or delete Tenant B's contract (404, not 403)")
    void cannotAccessOtherTenantsContractById() throws Exception {
        mockMvc.perform(get("/api/contracts/" + contractB).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/contracts/" + contractB + "/risks").header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/contracts/" + contractB + "/reprocess").header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/contracts/" + contractB).header("Authorization", bearer(tokenA)))
                .andExpect(status().isNotFound());

        // B's contract is untouched and still visible to B.
        mockMvc.perform(get("/api/contracts/" + contractB).header("Authorization", bearer(tokenB)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Semantic search never returns another tenant's chunks, even for a query that matches them exactly")
    void searchIsTenantScoped() throws Exception {
        JsonNode result = json(postJson("/api/search", tokenA,
                Map.of("query", "Zanzibar shipping cargo unlimited liability")).andExpect(status().isOk()));
        for (JsonNode hit : result.get("results")) {
            assertThat(hit.get("contractId").asLong()).isEqualTo(contractA);
            assertThat(hit.get("text").asText()).doesNotContain("Zanzibar");
        }

        JsonNode resultB = json(postJson("/api/search", tokenB,
                Map.of("query", "Zanzibar shipping cargo unlimited liability")).andExpect(status().isOk()));
        assertThat(resultB.get("results")).isNotEmpty();
        resultB.get("results").forEach(hit -> assertThat(hit.get("contractId").asLong()).isEqualTo(contractB));
    }

    @Test
    @DisplayName("Searching with another tenant's contractId is rejected as not found")
    void searchWithForeignContractIdIsRejected() throws Exception {
        postJson("/api/search", tokenA, Map.of("query", "termination", "contractId", contractB))
                .andExpect(status().isNotFound());
        postJson("/api/chat", tokenA, Map.of("question", "What is the termination notice?", "contractId", contractB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RAG context and citations for Tenant A never include Tenant B's documents")
    void ragContextIsTenantScoped() throws Exception {
        JsonNode answer = json(postJson("/api/chat", tokenA,
                Map.of("question", "Who is liable for late delivery of Zanzibar cargo?")).andExpect(status().isOk()));
        assertThat(answer.get("answer").asText()).doesNotContain("Zanzibar");
        answer.get("sources").forEach(s -> assertThat(s.get("contractId").asLong()).isEqualTo(contractA));

        JsonNode answerB = json(postJson("/api/chat", tokenB,
                Map.of("question", "What is the termination notice period in days?")).andExpect(status().isOk()));
        assertThat(answerB.get("answered").asBoolean()).isTrue();
        assertThat(answerB.get("answer").asText()).contains("fifteen (15) days");
        answerB.get("sources").forEach(s -> assertThat(s.get("contractId").asLong()).isEqualTo(contractB));
    }

    @Test
    @DisplayName("The context actually sent to the LLM contains only the caller's chunks")
    @SuppressWarnings("unchecked")
    void llmNeverReceivesAnotherTenantsChunks() throws Exception {
        // Matches both tenants' documents: termination clauses exist in A's and B's contracts.
        postJson("/api/chat", tokenA, Map.of("question", "What is the termination notice in days?"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<RetrievedChunk>> context = ArgumentCaptor.forClass(List.class);
        verify(aiService).generateAnswer(any(), context.capture());
        assertThat(context.getValue()).isNotEmpty()
                .allSatisfy(chunk -> {
                    assertThat(chunk.contractId()).isEqualTo(contractA);
                    assertThat(chunk.text()).doesNotContain("Zanzibar", "Globex");
                });
    }

    @Test
    @DisplayName("Vector repository: identical vectors in two tenants -> each tenant only ever gets its own")
    void vectorSearchFiltersByTenantBeforeRanking() {
        Long tenantA = tenantOf(contractA);
        Long tenantB = tenantOf(contractB);
        String secret = "Confidential pricing schedule for project Nightingale";
        float[] vector = aiService.generateEmbedding(secret);

        // Give tenant B many perfect matches; tenant A has none. A must still see none of B's rows.
        List<TextChunk> chunks = new ArrayList<>();
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            chunks.add(new TextChunk(1000 + i, 1, secret));
            vectors.add(vector);
        }
        vectorRepository.saveChunks(tenantB, contractB, chunks, vectors);

        List<RetrievedChunk> forA = vectorRepository.search(tenantA, vector, 20, null);
        assertThat(forA).isNotEmpty().allMatch(c -> c.contractId().equals(contractA));
        assertThat(forA).noneMatch(c -> c.text().contains("Nightingale"));

        List<RetrievedChunk> forB = vectorRepository.search(tenantB, vector, 5, null);
        assertThat(forB).hasSize(5).allMatch(c -> c.text().equals(secret));
    }

    @Test
    @DisplayName("Dashboard statistics are tenant-scoped")
    void dashboardIsTenantScoped() throws Exception {
        JsonNode stats = json(mockMvc.perform(get("/api/dashboard").header("Authorization", bearer(tokenA)))
                .andExpect(status().isOk()));
        assertThat(stats.get("contracts").asLong()).isEqualTo(1);
        assertThat(stats.get("processed").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("A tenantId smuggled into the request body is ignored")
    void tenantIdInRequestBodyIsIgnored() throws Exception {
        Long tenantB = tenantOf(contractB);
        JsonNode result = json(postJson("/api/search", tokenA,
                Map.of("query", "Zanzibar", "tenantId", tenantB)).andExpect(status().isOk()));
        result.get("results").forEach(hit -> assertThat(hit.get("contractId").asLong()).isEqualTo(contractA));
    }

    private Long tenantOf(long contractId) {
        return jdbc.queryForObject("SELECT tenant_id FROM contracts WHERE id = ?", Long.class, contractId);
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        list.forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }
}
