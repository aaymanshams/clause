package com.clauseiq;

import com.clauseiq.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    private static final String PASSWORD = "Sup3rSecret!";

    @Test
    void registerCreatesTenantAndAdminAndReturnsToken() throws Exception {
        JsonNode body = json(postJson("/api/auth/register", null, Map.of(
                "organizationName", "Acme Corporation", "email", "owner@acme-register.test", "password", PASSWORD))
                .andExpect(status().isCreated()));

        assertThat(body.get("token").asText()).isNotBlank();
        assertThat(body.at("/user/role").asText()).isEqualTo("ADMIN");
        assertThat(body.at("/user/tenantName").asText()).isEqualTo("Acme Corporation");
        assertThat(body.toString()).doesNotContain("password").doesNotContain(PASSWORD);
    }

    @Test
    void loginWithValidCredentialsReturnsTokenThatAuthenticates() throws Exception {
        register("Login Co", "user@login.test");

        String token = json(postJson("/api/auth/login", null, Map.of("email", "USER@login.test", "password", PASSWORD))
                .andExpect(status().isOk())).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@login.test"))
                .andExpect(jsonPath("$.tenantName").value("Login Co"));
    }

    @Test
    void loginWithWrongPasswordOrUnknownEmailIsRejectedWithSameMessage() throws Exception {
        register("Wrong Pass Co", "user@wrongpass.test");

        postJson("/api/auth/login", null, Map.of("email", "user@wrongpass.test", "password", "not-the-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        postJson("/api/auth/login", null, Map.of("email", "nobody@wrongpass.test", "password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        register("First Co", "dup@dup.test");
        postJson("/api/auth/register", null, Map.of(
                "organizationName", "Second Co", "email", "dup@dup.test", "password", PASSWORD))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationInputIsRejected() throws Exception {
        postJson("/api/auth/register", null, Map.of(
                "organizationName", "", "email", "not-an-email", "password", "short"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mockMvc.perform(get("/api/contracts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/contracts").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());

        String token = register("Tamper Co", "user@tamper.test");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mockMvc.perform(get("/api/contracts").header("Authorization", bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsCanAddUsersAndNewUsersJoinTheAdminsTenant() throws Exception {
        String adminToken = register("Roles Co", "admin@roles.test");

        JsonNode created = json(postJson("/api/users", adminToken, Map.of(
                "email", "analyst@roles.test", "password", PASSWORD, "role", "USER"))
                .andExpect(status().isCreated()));
        JsonNode admin = json(mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(adminToken))));
        assertThat(created.get("tenantId").asLong()).isEqualTo(admin.get("tenantId").asLong());

        String userToken = json(postJson("/api/auth/login", null,
                Map.of("email", "analyst@roles.test", "password", PASSWORD))).get("token").asText();
        postJson("/api/users", userToken, Map.of("email", "x@roles.test", "password", PASSWORD, "role", "ADMIN"))
                .andExpect(status().isForbidden());
    }

    private String register(String org, String email) throws Exception {
        return json(postJson("/api/auth/register", null, Map.of(
                "organizationName", org, "email", email, "password", PASSWORD))
                .andExpect(status().isCreated())).get("token").asText();
    }
}
