package com.clauseiq.ai.risk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RiskResultRepository extends JpaRepository<RiskResult, Long> {

    List<RiskResult> findAllByContractIdAndTenantId(Long contractId, Long tenantId);

    List<RiskResult> findAllByTenantId(Long tenantId);

    long countByTenantIdAndSeverity(Long tenantId, RiskFinding.Severity severity);

    @Modifying
    @Query("delete from RiskResult r where r.contractId = :contractId and r.tenantId = :tenantId")
    void deleteByContractIdAndTenantId(Long contractId, Long tenantId);
}
