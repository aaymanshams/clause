package com.clauseiq.config;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.extraction.ExtractionValidator;
import com.clauseiq.ai.provider.OfflineAiService;
import com.clauseiq.ai.provider.OpenAiClient;
import com.clauseiq.ai.provider.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Chooses the AI provider at startup. "auto" (default) uses OpenAI when OPENAI_API_KEY is present
 * and otherwise falls back to the offline provider, so the app starts cleanly without a key.
 */
@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiService aiService(ClauseIqProperties properties, ExtractionValidator validator) {
        ClauseIqProperties.Ai ai = properties.ai();
        String provider = ai.provider() == null ? "auto" : ai.provider().toLowerCase(Locale.ROOT);
        boolean hasKey = ai.openai().apiKey() != null && !ai.openai().apiKey().isBlank();

        switch (provider) {
            case "openai" -> {
                if (!hasKey) {
                    throw new IllegalStateException(
                            "AI_PROVIDER=openai but OPENAI_API_KEY is not set. Set the key or use AI_PROVIDER=auto.");
                }
                return openAi(ai, properties.rag(), validator);
            }
            case "offline" -> {
                return offline();
            }
            case "auto" -> {
                if (hasKey) {
                    return openAi(ai, properties.rag(), validator);
                }
                log.warn("OPENAI_API_KEY is not set: using the OFFLINE AI provider (lexical embeddings, "
                        + "regex extraction, extractive answers). Set OPENAI_API_KEY for real LLM features.");
                return offline();
            }
            default -> throw new IllegalStateException("Unknown clauseiq.ai.provider: " + provider);
        }
    }

    private static AiService openAi(ClauseIqProperties.Ai ai, ClauseIqProperties.Rag rag,
                                    ExtractionValidator validator) {
        log.info("AI provider: OpenAI (chat={}, embeddings={})", ai.openai().chatModel(), ai.openai().embeddingModel());
        return new OpenAiService(new OpenAiClient(ai.openai()), validator, rag.minSimilarity());
    }

    private static AiService offline() {
        return new OfflineAiService();
    }
}
