package com.clauseiq.ai.risk;

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
@Table(name = "risk_results")
public class RiskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "contract_id", nullable = false, updatable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskFinding.Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskFinding.Severity severity;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RiskResult() {
    }

    public RiskResult(Long tenantId, Long contractId, RiskFinding finding) {
        this.tenantId = tenantId;
        this.contractId = contractId;
        this.type = finding.type();
        this.severity = finding.severity();
        this.description = finding.description();
    }

    public Long getId() {
        return id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public Long getContractId() {
        return contractId;
    }

    public RiskFinding.Type getType() {
        return type;
    }

    public RiskFinding.Severity getSeverity() {
        return severity;
    }

    public String getDescription() {
        return description;
    }
}
