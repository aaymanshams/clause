package com.clauseiq.ai.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    /** Optional contractId narrows the question to one contract (still within the caller's tenant). */
    public record ChatRequest(@NotBlank @Size(max = 2000) String question, Long contractId) {
    }

    /** pageNumber is null when unknown (e.g. DOCX); chunkIndex is always present as a fallback reference. */
    public record Source(
            String label,
            Long contractId,
            String contractName,
            Long chunkId,
            int chunkIndex,
            Integer pageNumber,
            String excerpt) {
    }

    public record ChatResponse(String answer, boolean answered, List<Source> sources, String provider) {
    }
}
