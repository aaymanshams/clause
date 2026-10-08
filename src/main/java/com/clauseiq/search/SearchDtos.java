package com.clauseiq.search;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SearchDtos {

    private SearchDtos() {
    }

    /** No tenantId field on purpose: the tenant always comes from the JWT. */
    public record SearchRequest(
            @NotBlank @Size(max = 1000) String query,
            Long contractId,
            @Min(1) @Max(20) Integer topK) {
    }

    public record SearchHit(
            Long contractId,
            String contractName,
            Long chunkId,
            int chunkIndex,
            Integer pageNumber,
            double similarity,
            String text) {
    }

    public record SearchResponse(String query, List<SearchHit> results) {
    }
}
