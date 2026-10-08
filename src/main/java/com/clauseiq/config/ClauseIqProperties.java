package com.clauseiq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "clauseiq")
public record ClauseIqProperties(
        Jwt jwt,
        Storage storage,
        Processing processing,
        Ai ai,
        Rag rag,
        Risk risk) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Storage(String uploadDir, long maxFileSizeBytes) {
    }

    public record Processing(boolean async) {
    }

    public record Ai(String provider, OpenAi openai) {
    }

    public record OpenAi(String apiKey, String baseUrl, String chatModel, String embeddingModel, int timeoutSeconds) {
    }

    public record Rag(int topK, double minSimilarity) {
    }

    public record Risk(int maxTerminationNoticeDays,
                       int highTerminationNoticeDays,
                       int maxAutoRenewalMonths,
                       List<String> preferredGoverningLaws) {
    }
}
