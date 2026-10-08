package com.clauseiq.search;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.embedding.RetrievedChunk;
import com.clauseiq.ai.embedding.VectorRepository;
import com.clauseiq.config.ClauseIqProperties;
import com.clauseiq.contract.ContractRepository;
import com.clauseiq.common.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;

/** query -> embedding -> pgvector similarity search (tenant-filtered) -> top K relevant chunks. */
@Service
public class SemanticSearchService {

    private final AiService aiService;
    private final VectorRepository vectorRepository;
    private final ContractRepository contractRepository;
    private final ClauseIqProperties.Rag rag;

    public SemanticSearchService(AiService aiService, VectorRepository vectorRepository,
                                 ContractRepository contractRepository, ClauseIqProperties properties) {
        this.aiService = aiService;
        this.vectorRepository = vectorRepository;
        this.contractRepository = contractRepository;
        this.rag = properties.rag();
    }

    public List<RetrievedChunk> search(Long tenantId, String query, Long contractId, Integer topK) {
        if (contractId != null && contractRepository.findByIdAndTenantId(contractId, tenantId).isEmpty()) {
            // Same response as a truly missing contract: never reveal that another tenant's id exists.
            throw ApiException.notFound("Contract not found");
        }
        float[] embedding = aiService.generateEmbedding(query);
        int k = topK == null ? rag.topK() : topK;
        return vectorRepository.search(tenantId, embedding, k, contractId).stream()
                .filter(c -> c.similarity() >= aiService.minRelevantSimilarity())
                .toList();
    }
}
