package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiException;
import com.clauseiq.config.ClauseIqProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Thin HTTP wrapper over the OpenAI REST API (chat completions + embeddings). */
public class OpenAiClient {

    private static final int EMBEDDING_BATCH_SIZE = 100;
    private static final int MAX_EMBEDDING_INPUT_CHARS = 8000;

    private final RestClient restClient;
    private final String chatModel;
    private final String embeddingModel;

    public OpenAiClient(ClauseIqProperties.OpenAi config) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(config.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                .build();
        this.chatModel = config.chatModel();
        this.embeddingModel = config.embeddingModel();
    }

    public String chat(String system, String user, boolean jsonMode) {
        var request = new ChatRequest(chatModel, 0.0,
                List.of(new Message("system", system), new Message("user", user)),
                jsonMode ? Map.of("type", "json_object") : null);
        ChatResponse response = post("/chat/completions", request, ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new AiException("OpenAI returned no choices");
        }
        return response.choices().get(0).message().content();
    }

    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += EMBEDDING_BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(texts.size(), start + EMBEDDING_BATCH_SIZE)).stream()
                    .map(t -> t.length() > MAX_EMBEDDING_INPUT_CHARS ? t.substring(0, MAX_EMBEDDING_INPUT_CHARS) : t)
                    .toList();
            EmbeddingResponse response = post("/embeddings",
                    new EmbeddingRequest(embeddingModel, batch), EmbeddingResponse.class);
            if (response == null || response.data() == null || response.data().size() != batch.size()) {
                throw new AiException("OpenAI returned an unexpected number of embeddings");
            }
            response.data().stream()
                    .sorted(Comparator.comparingInt(EmbeddingData::index))
                    .forEach(d -> result.add(d.embedding()));
        }
        return result;
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientException e) {
            // Never include request headers here: they contain the API key.
            throw new AiException("OpenAI request to " + path + " failed: " + e.getMessage(), e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ChatRequest(String model, double temperature, List<Message> messages,
                       @JsonProperty("response_format") Map<String, String> responseFormat) {
    }

    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    record EmbeddingRequest(String model, List<String> input) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(int index, float[] embedding) {
    }
}
