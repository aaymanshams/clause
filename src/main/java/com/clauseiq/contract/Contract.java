package com.clauseiq.contract;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "contracts")
public class Contract {

    public enum Status { UPLOADED, PROCESSING, READY, FAILED }

    public enum ExtractionStatus { PENDING, SUCCESS, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.UPLOADED;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false)
    private ExtractionStatus extractionStatus = ExtractionStatus.PENDING;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Contract() {
    }

    public Contract(Long tenantId, Long uploadedBy, String originalFilename, String contentType,
                    long sizeBytes, String storagePath) {
        this.tenantId = tenantId;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
    }

    public void markProcessing() {
        this.status = Status.PROCESSING;
        this.errorMessage = null;
    }

    public void markReady(int pageCount, int chunkCount, ExtractionStatus extractionStatus, String warning) {
        this.status = Status.READY;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.extractionStatus = extractionStatus;
        this.errorMessage = warning;
        this.processedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = Status.FAILED;
        this.errorMessage = truncate(errorMessage, 1000);
        this.processedAt = Instant.now();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Status getStatus() {
        return status;
    }

    public ExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
