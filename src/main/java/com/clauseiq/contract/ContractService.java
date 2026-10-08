package com.clauseiq.contract;

import com.clauseiq.ai.embedding.VectorRepository;
import com.clauseiq.ai.extraction.ExtractedContractDataRepository;
import com.clauseiq.ai.risk.RiskFinding;
import com.clauseiq.ai.risk.RiskResult;
import com.clauseiq.ai.risk.RiskResultRepository;
import com.clauseiq.common.ApiException;
import com.clauseiq.config.ClauseIqProperties;
import com.clauseiq.contract.ContractDtos.ContractDetail;
import com.clauseiq.contract.ContractDtos.ContractSummary;
import com.clauseiq.contract.ContractDtos.ExtractedDataView;
import com.clauseiq.contract.ContractDtos.RiskReport;
import com.clauseiq.contract.ContractDtos.RiskView;
import com.clauseiq.document.DocumentTextExtractor;
import com.clauseiq.document.FileStorageService;
import com.clauseiq.security.AuthenticatedUser;
import com.clauseiq.security.TenantContext;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** All operations resolve the tenant from the JWT via {@link TenantContext}; nothing trusts client input for it. */
@Service
public class ContractService {

    private static final Map<String, String> EXTENSION_TO_TYPE = Map.of(
            "pdf", DocumentTextExtractor.PDF,
            "docx", DocumentTextExtractor.DOCX);

    private final ContractRepository contractRepository;
    private final ExtractedContractDataRepository extractedRepository;
    private final RiskResultRepository riskRepository;
    private final VectorRepository vectorRepository;
    private final FileStorageService storage;
    private final DocumentTextExtractor textExtractor;
    private final ContractProcessor processor;
    private final TaskExecutor executor;
    private final long maxFileSize;

    public ContractService(ContractRepository contractRepository, ExtractedContractDataRepository extractedRepository,
                           RiskResultRepository riskRepository, VectorRepository vectorRepository,
                           FileStorageService storage, DocumentTextExtractor textExtractor,
                           ContractProcessor processor, TaskExecutor contractProcessingExecutor,
                           ClauseIqProperties properties) {
        this.contractRepository = contractRepository;
        this.extractedRepository = extractedRepository;
        this.riskRepository = riskRepository;
        this.vectorRepository = vectorRepository;
        this.storage = storage;
        this.textExtractor = textExtractor;
        this.processor = processor;
        this.executor = contractProcessingExecutor;
        this.maxFileSize = properties.storage().maxFileSizeBytes();
    }

    /** Validates, stores and saves the file, then queues processing. Returns immediately with status UPLOADED. */
    public ContractSummary upload(MultipartFile file) {
        AuthenticatedUser user = TenantContext.currentUser();
        String filename = validate(file);
        String extension = StringUtils.getFilenameExtension(filename).toLowerCase(Locale.ROOT);

        String storagePath;
        try (InputStream in = file.getInputStream()) {
            storagePath = storage.store(user.tenantId(), extension, in);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not read upload");
        }
        Contract contract = contractRepository.save(new Contract(user.tenantId(), user.userId(), filename,
                EXTENSION_TO_TYPE.get(extension), file.getSize(), storagePath));

        Long contractId = contract.getId();
        Long tenantId = user.tenantId();
        executor.execute(() -> processor.process(contractId, tenantId));

        return contractRepository.findByIdAndTenantId(contractId, tenantId)
                .map(c -> toSummary(c, List.of()))
                .orElseThrow();
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw ApiException.badRequest("File exceeds the maximum size of " + (maxFileSize / (1024 * 1024)) + " MB");
        }
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        filename = filename.substring(filename.lastIndexOf('/') + 1);
        String extension = StringUtils.getFilenameExtension(filename);
        if (filename.isBlank() || extension == null || !EXTENSION_TO_TYPE.containsKey(extension.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("Only PDF and DOCX files are supported");
        }
        // Check the actual content, not just the extension or the client-supplied Content-Type.
        String detected;
        try (InputStream in = file.getInputStream()) {
            detected = textExtractor.detectType(in, filename);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read file");
        }
        if (!EXTENSION_TO_TYPE.get(extension.toLowerCase(Locale.ROOT)).equals(detected)) {
            throw ApiException.badRequest("File content does not match a valid PDF or DOCX document");
        }
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }

    @Transactional(readOnly = true)
    public List<ContractSummary> list() {
        Long tenantId = TenantContext.currentTenantId();
        Map<Long, List<RiskResult>> risksByContract = riskRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.groupingBy(RiskResult::getContractId));
        return contractRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(c -> toSummary(c, risksByContract.getOrDefault(c.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContractDetail get(Long id) {
        Long tenantId = TenantContext.currentTenantId();
        Contract contract = findOwned(id, tenantId);
        List<RiskResult> risks = sortedRisks(id, tenantId);
        ExtractedDataView data = extractedRepository.findByContractIdAndTenantId(id, tenantId)
                .map(ExtractedDataView::from).orElse(null);
        return new ContractDetail(toSummary(contract, risks), contract.getErrorMessage(), data,
                risks.stream().map(RiskView::from).toList());
    }

    @Transactional(readOnly = true)
    public RiskReport risks(Long id) {
        Long tenantId = TenantContext.currentTenantId();
        findOwned(id, tenantId);
        return new RiskReport(id, sortedRisks(id, tenantId).stream().map(RiskView::from).toList());
    }

    public ContractSummary reprocess(Long id) {
        Long tenantId = TenantContext.currentTenantId();
        Contract contract = findOwned(id, tenantId);
        if (contract.getStatus() == Contract.Status.PROCESSING) {
            throw ApiException.conflict("Contract is already being processed");
        }
        executor.execute(() -> processor.process(id, tenantId));
        return toSummary(findOwned(id, tenantId), List.of());
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.currentTenantId();
        Contract contract = findOwned(id, tenantId);
        riskRepository.deleteByContractIdAndTenantId(id, tenantId);
        extractedRepository.deleteByContractIdAndTenantId(id, tenantId);
        vectorRepository.deleteByContract(tenantId, id);
        contractRepository.delete(contract);
        storage.delete(contract.getStoragePath());
    }

    /** Another tenant's contract is reported as "not found", never "forbidden", so ids can't be probed. */
    private Contract findOwned(Long id, Long tenantId) {
        return contractRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> ApiException.notFound("Contract not found"));
    }

    private List<RiskResult> sortedRisks(Long contractId, Long tenantId) {
        return riskRepository.findAllByContractIdAndTenantId(contractId, tenantId).stream()
                .sorted(Comparator.comparing(RiskResult::getSeverity).reversed())
                .toList();
    }

    private static ContractSummary toSummary(Contract c, List<RiskResult> risks) {
        RiskFinding.Severity highest = risks.stream().map(RiskResult::getSeverity)
                .max(Comparator.naturalOrder()).orElse(null);
        return new ContractSummary(c.getId(), c.getOriginalFilename(), c.getStatus(), c.getExtractionStatus(),
                c.getSizeBytes(), c.getPageCount(), c.getChunkCount(), risks.size(), highest,
                c.getCreatedAt(), c.getProcessedAt());
    }
}
