package com.clauseiq.support;

import com.clauseiq.ai.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests against a real PostgreSQL + pgvector container. Processing runs synchronously and
 * the deterministic offline AI provider is used, so tests need Docker but no API key.
 */
@SpringBootTest(properties = {
        "clauseiq.processing.async=false",
        "clauseiq.ai.provider=offline",
        "clauseiq.storage.upload-dir=target/test-uploads"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    // One container for the whole test run (shared across test classes and the cached Spring context).
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    /**
     * Spy (real offline provider underneath) so tests can inspect exactly what reaches the LLM and
     * simulate provider outages. Declared here so every test class shares one cached Spring context.
     */
    @MockitoSpyBean
    protected AiService aiService;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Registers a fresh organization and returns its admin's JWT. */
    protected String registerTenant(String organization) throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@" + organization.toLowerCase().replaceAll("\\W", "") + ".test";
        return json(mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "organizationName", organization, "email", email, "password", "Sup3rSecret!"))))
                .andExpect(status().isCreated()))
                .get("token").asText();
    }

    protected ResultActions upload(String token, String filename, byte[] content) throws Exception {
        return mockMvc.perform(multipart("/api/contracts/upload")
                .file(new MockMultipartFile("file", filename, MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
                .header("Authorization", bearer(token)));
    }

    protected long uploadContract(String token, String filename, byte[] content) throws Exception {
        return json(upload(token, filename, content).andExpect(status().isAccepted())).get("id").asLong();
    }

    protected ResultActions postJson(String path, String token, Object body) throws Exception {
        var request = post(path).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", bearer(token));
        }
        return mockMvc.perform(request);
    }

    protected JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
