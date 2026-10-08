package com.clauseiq.ai;

import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.extraction.ContractExtractionResult;

import java.util.List;

/**
 * Single seam for every AI operation. Controllers and domain services depend on this interface,
 * never on a specific LLM vendor, so the provider can be swapped (OpenAI, offline fallback, others).
 */
public interface AiService {

    /** Every implementation must return vectors of this size (matches the pgvector column). */
    int EMBEDDING_DIMENSIONS = 1536;

    String providerName();

    /**
     * Minimum cosine similarity for a chunk to count as relevant. Provider-specific because
     * different embedding models produce very different similarity ranges.
     */
    double minRelevantSimilarity();

    List<float[]> generateEmbeddings(List<String> texts);

    default float[] generateEmbedding(String text) {
        return generateEmbeddings(List.of(text)).get(0);
    }

    /**
     * Extracts structured fields. Implementations validate the output and retry once on invalid
     * output, then throw {@link AiException}.
     */
    ContractExtractionResult extractContractData(String contractText);

    /**
     * Answers strictly from the given context. Sources are labelled [S1]..[Sn] in context order;
     * the answer cites them with those labels, or returns {@link Prompts#NO_ANSWER}.
     */
    String generateAnswer(String question, List<RetrievedChunk> context);
}
