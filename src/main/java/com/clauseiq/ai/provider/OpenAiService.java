package com.clauseiq.ai.provider;

import com.clauseiq.ai.AiException;
import com.clauseiq.ai.AiService;
import com.clauseiq.ai.Prompts;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.extraction.ContractExtractionResult;
import com.clauseiq.ai.extraction.ExtractionValidator;
import com.clauseiq.ai.extraction.ExtractionValidator.InvalidExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** {@link AiService} backed by OpenAI chat completions + embeddings. */
public class OpenAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiService.class);

    /** Enough for the key commercial terms of most contracts while bounding cost per document. */
    static final int MAX_EXTRACTION_CHARS = 60_000;

    private final OpenAiClient client;
    private final ExtractionValidator validator;
    private final double minSimilarity;

    public OpenAiService(OpenAiClient client, ExtractionValidator validator, double minSimilarity) {
        this.client = client;
        this.validator = validator;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public double minRelevantSimilarity() {
        return minSimilarity;
    }

    @Override
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = client.embed(texts);
        for (float[] v : vectors) {
            if (v.length != EMBEDDING_DIMENSIONS) {
                throw new AiException("Embedding model returned " + v.length + " dimensions, expected "
                        + EMBEDDING_DIMENSIONS);
            }
        }
        return vectors;
    }

    /** One attempt, then one retry that tells the model what was wrong, then give up. */
    @Override
    public ContractExtractionResult extractContractData(String contractText) {
        String text = contractText.length() > MAX_EXTRACTION_CHARS
                ? contractText.substring(0, MAX_EXTRACTION_CHARS) : contractText;
        String user = Prompts.extractionUser(text);
        String error;
        try {
            return validator.parseAndValidate(client.chat(Prompts.EXTRACTION_SYSTEM, user, true));
        } catch (InvalidExtractionException e) {
            error = e.getMessage();
            log.warn("Extraction output invalid, retrying once: {}", error);
        }
        try {
            return validator.parseAndValidate(
                    client.chat(Prompts.EXTRACTION_SYSTEM, user + "\n\n" + Prompts.extractionRetry(error), true));
        } catch (InvalidExtractionException e) {
            throw new AiException("Extraction failed validation after retry: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateAnswer(String question, List<RetrievedChunk> context) {
        if (context.isEmpty()) {
            return Prompts.NO_ANSWER;
        }
        return client.chat(Prompts.RAG_SYSTEM, Prompts.ragUser(question, context), false).trim();
    }
}
