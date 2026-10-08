package com.clauseiq.ai.extraction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ExtractedContractDataRepository extends JpaRepository<ExtractedContractData, Long> {

    Optional<ExtractedContractData> findByContractIdAndTenantId(Long contractId, Long tenantId);

    @Modifying
    @Query("delete from ExtractedContractData d where d.contractId = :contractId and d.tenantId = :tenantId")
    void deleteByContractIdAndTenantId(Long contractId, Long tenantId);
}
