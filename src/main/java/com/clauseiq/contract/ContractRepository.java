package com.clauseiq.contract;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every read method is scoped by tenantId. There is deliberately no tenant-agnostic finder used by
 * request-handling code: callers must pass the tenant from {@link com.clauseiq.security.TenantContext}.
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<Contract> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, Contract.Status status);
}
