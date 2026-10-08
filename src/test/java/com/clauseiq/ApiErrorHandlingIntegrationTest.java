package com.clauseiq;

import com.clauseiq.ai.AiException;
import com.clauseiq.support.IntegrationTestBase;
import com.clauseiq.support.TestDocuments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Client mistakes must be 4xx with a safe message; dependency failures 503; never a raw 500. */
class ApiErrorHandlingIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        token = registerTenant("Errors Co");
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body"));
    }

    @Test
    void invalidEnumValueIsBadRequest() throws Exception {
        postJson("/api/users", token, Map.of("email", "x@errors.test", "password", "Sup3rSecret!", "role", "SUPERUSER"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericPathVariableIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/contracts/abc").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongMethodAndMediaTypeKeepTheirStatus() throws Exception {
        mockMvc.perform(put("/api/contracts").header("Authorization", bearer(token)))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/contracts/upload").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void aiProviderOutageIsServiceUnavailableWithoutLeakingDetails() throws Exception {
        uploadContract(token, "acme.pdf", TestDocuments.pdf(TestDocuments.ACME_PAGE_2));
        doThrow(new AiException("OpenAI request to /chat/completions failed: 429 rate limit (internal detail)"))
                .when(aiService).generateAnswer(org.mockito.ArgumentMatchers.anyString(), anyList());

        postJson("/api/chat", token, Map.of("question", "What is the termination notice period?"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(not(containsString("internal detail"))));
    }
}
