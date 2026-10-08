package com.clauseiq.ai.embedding;

/** A chunk returned by vector search. pageNumber is null when the source format has no pages (DOCX). */
public record RetrievedChunk(
        Long chunkId,
        Long contractId,
        String contractName,
        int chunkIndex,
        Integer pageNumber,
        String text,
        double similarity) {
}
