package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiException;
import com.clauseiq.config.ClauseIqProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the wire format against a local fake of the OpenAI REST API (no real key or network needed). */
class OpenAiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> requests = new ConcurrentHashMap<>();
    private final Map<String, String> authHeaders = new ConcurrentHashMap<>();
    private HttpServer server;
    private OpenAiClient client;
    private volatile int status = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> respond(ex, """
                {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Notice is 90 days [S1]."}}]}
                """));
        server.createContext("/v1/embeddings", ex -> {
            JsonNode body = record(ex);
            StringBuilder data = new StringBuilder();
            // Return items out of order to prove the client re-sorts them by index.
            for (int i = body.get("input").size() - 1; i >= 0; i--) {
                data.append(data.length() > 0 ? "," : "")
                        .append("{\"index\":").append(i).append(",\"embedding\":[").append(i).append(".5,1.0]}");
            }
            send(ex, "{\"data\":[" + data + "]}");
        });
        server.start();
        client = new OpenAiClient(new ClauseIqProperties.OpenAi("sk-test-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "gpt-test", "embed-test", 5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void chatSendsModelMessagesJsonModeAndBearerKey() {
        String answer = client.chat("system prompt", "user prompt", true);

        assertThat(answer).isEqualTo("Notice is 90 days [S1].");
        JsonNode req = requests.get("/v1/chat/completions");
        assertThat(req.get("model").asText()).isEqualTo("gpt-test");
        assertThat(req.get("temperature").asDouble()).isZero();
        assertThat(req.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(req.at("/messages/1/content").asText()).isEqualTo("user prompt");
        assertThat(req.at("/response_format/type").asText()).isEqualTo("json_object");
        assertThat(authHeaders.get("/v1/chat/completions")).isEqualTo("Bearer sk-test-key");
    }

    @Test
    void chatWithoutJsonModeOmitsResponseFormat() {
        client.chat("s", "u", false);
        assertThat(requests.get("/v1/chat/completions").has("response_format")).isFalse();
    }

    @Test
    void embeddingsAreReturnedInInputOrder() {
        List<float[]> vectors = client.embed(List.of("a", "b", "c"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)[0]).isEqualTo(0.5f);
        assertThat(vectors.get(2)[0]).isEqualTo(2.5f);
        assertThat(requests.get("/v1/embeddings").get("model").asText()).isEqualTo("embed-test");
    }

    @Test
    void httpErrorsBecomeAiExceptionsWithoutLeakingTheKey() {
        status = 429;
        assertThatThrownBy(() -> client.chat("s", "u", false))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("429")
                .hasMessageNotContaining("sk-test-key");
    }

    private void respond(HttpExchange ex, String body) throws IOException {
        record(ex);
        send(ex, body);
    }

    private JsonNode record(HttpExchange ex) throws IOException {
        JsonNode body = mapper.readTree(ex.getRequestBody());
        requests.put(ex.getRequestURI().getPath(), body);
        authHeaders.put(ex.getRequestURI().getPath(), ex.getRequestHeaders().getFirst("Authorization"));
        return body;
    }

    private void send(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
