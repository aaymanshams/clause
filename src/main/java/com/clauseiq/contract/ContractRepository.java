package com.clauseiq.contract;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Every method used while handling a request is scoped by tenantId; callers pass the tenant from
 * {@link com.clauseiq.security.TenantContext}. The only unscoped finder is {@link #findAllByStatusIn},
 * used by startup recovery, which is not tied to any user.
 */
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findAllByTenantIdOrderByCreatedAtDesc(Long tenantId);

    Optional<Contract> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, Contract.Status status);

    /** Row lock so a delete cannot interleave with a status change by the processor or a reprocess. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contract c where c.id = :id and c.tenantId = :tenantId")
    Optional<Contract> findForUpdate(Long id, Long tenantId);

    /**
     * Atomic compare-and-set on status. Returns 1 if this caller won the transition, 0 otherwise, so two
     * concurrent reprocess requests can never both queue the same contract.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Contract c set c.status = :to, c.errorMessage = null "
            + "where c.id = :id and c.tenantId = :tenantId and c.status in :from")
    int transitionStatus(Long id, Long tenantId, Collection<Contract.Status> from, Contract.Status to);

    List<Contract> findAllByStatusIn(Collection<Contract.Status> statuses);
}
