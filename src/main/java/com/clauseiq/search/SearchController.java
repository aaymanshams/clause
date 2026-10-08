package com.clauseiq.search;

import com.clauseiq.search.SearchDtos.SearchHit;
import com.clauseiq.search.SearchDtos.SearchRequest;
import com.clauseiq.search.SearchDtos.SearchResponse;
import com.clauseiq.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/api/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        var hits = searchService.search(TenantContext.currentTenantId(), request.query(),
                        request.contractId(), request.topK()).stream()
                .map(c -> new SearchHit(c.contractId(), c.contractName(), c.chunkId(), c.chunkIndex(),
                        c.pageNumber(), Math.round(c.similarity() * 1000) / 1000.0, c.text()))
                .toList();
        return new SearchResponse(request.query(), hits);
    }
}
