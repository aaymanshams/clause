package com.clauseiq.contract;

import com.clauseiq.ai.AiException;
import com.clauseiq.ai.AiService;
import com.clauseiq.ai.embedding.VectorRepository;
import com.clauseiq.ai.extraction.ContractExtractionResult;
import com.clauseiq.ai.extraction.ExtractedContractData;
import com.clauseiq.ai.extraction.ExtractedContractDataRepository;
import com.clauseiq.ai.risk.RiskAnalyzer;
import com.clauseiq.ai.risk.RiskFinding;
import com.clauseiq.ai.risk.RiskResult;
import com.clauseiq.ai.risk.RiskResultRepository;
import com.clauseiq.document.DocumentTextExtractor;
import com.clauseiq.document.DocumentTextExtractor.ExtractedDocument;
import com.clauseiq.document.FileStorageService;
import com.clauseiq.document.TextChunker;
import com.clauseiq.document.TextChunker.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Upload pipeline: file -> Tika text -> chunks -> embeddings (pgvector) -> LLM extraction -> risk rules.
 * Slow AI calls happen outside any DB transaction; results are written in one short transaction.
 * Extraction failure does not fail the contract: it stays searchable/chat-able with extraction=FAILED.
 */
@Service
public class ContractProcessor {

    private static final Logger log = LoggerFactory.getLogger(ContractProcessor.class);

    private final ContractRepository contractRepository;
    private final FileStorageService storage;
    private final DocumentTextExtractor textExtractor;
    private final TextChunker chunker;
    private final AiService aiService;
    private final VectorRepository vectorRepository;
    private final ExtractedContractDataRepository extractedRepository;
    private final RiskResultRepository riskRepository;
    private final RiskAnalyzer riskAnalyzer;
    private final TransactionTemplate tx;

    public ContractProcessor(ContractRepository contractRepository, FileStorageService storage,
                             DocumentTextExtractor textExtractor, TextChunker chunker, AiService aiService,
                             VectorRepository vectorRepository, ExtractedContractDataRepository extractedRepository,
                             RiskResultRepository riskRepository, RiskAnalyzer riskAnalyzer, TransactionTemplate tx) {
        this.contractRepository = contractRepository;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.chunker = chunker;
        this.aiService = aiService;
        this.vectorRepository = vectorRepository;
        this.extractedRepository = extractedRepository;
        this.riskRepository = riskRepository;
        this.riskAnalyzer = riskAnalyzer;
        this.tx = tx;
    }

    public void process(Long contractId, Long tenantId) {
        Contract contract = contractRepository.findByIdAndTenantId(contractId, tenantId).orElse(null);
        if (contract == null) {
            log.warn("Contract {} for tenant {} no longer exists; skipping processing", contractId, tenantId);
            return;
        }
        contract.markProcessing();
        contractRepository.save(contract);

        try {
            ExtractedDocument document;
            try (InputStream in = Files.newInputStream(storage.resolve(contract.getStoragePath()))) {
                document = textExtractor.extract(in);
            }
            String fullText = document.fullText();
            if (fullText.isBlank()) {
                throw new IllegalStateException("No extractable text found (the file may be a scanned image)");
            }

            List<TextChunk> chunks = chunker.chunk(document.pages());
            List<float[]> embeddings = aiService.generateEmbeddings(chunks.stream().map(TextChunk::text).toList());

            ContractExtractionResult extraction = null;
            String warning = null;
            try {
                extraction = aiService.extractContractData(fullText);
            } catch (AiException e) {
                log.warn("Structured extraction failed for contract {}: {}", contractId, e.getMessage());
                warning = "Structured extraction failed: " + e.getMessage();
            }
            List<RiskFinding> risks = extraction == null ? List.of() : riskAnalyzer.analyze(extraction);

            final ContractExtractionResult extracted = extraction;
            final String warningMessage = warning;
            tx.executeWithoutResult(status -> {
                vectorRepository.deleteByContract(tenantId, contractId);
                vectorRepository.saveChunks(tenantId, contractId, chunks, embeddings);
                extractedRepository.deleteByContractIdAndTenantId(contractId, tenantId);
                riskRepository.deleteByContractIdAndTenantId(contractId, tenantId);
                if (extracted != null) {
                    extractedRepository.save(ExtractedContractData.from(tenantId, contractId, extracted));
                    risks.forEach(r -> riskRepository.save(new RiskResult(tenantId, contractId, r)));
                }
                contract.markReady(document.pages().size(), chunks.size(),
                        extracted != null ? Contract.ExtractionStatus.SUCCESS : Contract.ExtractionStatus.FAILED,
                        warningMessage);
                contractRepository.save(contract);
            });
            log.info("Processed contract {} (tenant {}): {} pages, {} chunks, extraction={}, risks={}",
                    contractId, tenantId, document.pages().size(), chunks.size(),
                    contract.getExtractionStatus(), risks.size());
        } catch (Exception e) {
            log.error("Processing failed for contract {} (tenant {})", contractId, tenantId, e);
            contract.markFailed(e.getMessage());
            contractRepository.save(contract);
        }
    }
}
