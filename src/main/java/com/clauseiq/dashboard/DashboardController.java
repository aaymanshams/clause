package com.clauseiq.dashboard;

import com.clauseiq.ai.AiService;
import com.clauseiq.ai.risk.RiskFinding;
import com.clauseiq.ai.risk.RiskResultRepository;
import com.clauseiq.contract.Contract;
import com.clauseiq.contract.ContractRepository;
import com.clauseiq.security.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    public record DashboardStats(long contracts, long processed, long processing, long failed,
                                 long highRisks, String aiProvider) {
    }

    private final ContractRepository contractRepository;
    private final RiskResultRepository riskRepository;
    private final AiService aiService;

    public DashboardController(ContractRepository contractRepository, RiskResultRepository riskRepository,
                               AiService aiService) {
        this.contractRepository = contractRepository;
        this.riskRepository = riskRepository;
        this.aiService = aiService;
    }

    @GetMapping("/api/dashboard")
    public DashboardStats stats() {
        Long tenantId = TenantContext.currentTenantId();
        return new DashboardStats(
                contractRepository.countByTenantId(tenantId),
                contractRepository.countByTenantIdAndStatus(tenantId, Contract.Status.READY),
                contractRepository.countByTenantIdAndStatus(tenantId, Contract.Status.PROCESSING)
                        + contractRepository.countByTenantIdAndStatus(tenantId, Contract.Status.UPLOADED),
                contractRepository.countByTenantIdAndStatus(tenantId, Contract.Status.FAILED),
                riskRepository.countByTenantIdAndSeverity(tenantId, RiskFinding.Severity.HIGH),
                aiService.providerName());
    }
}
