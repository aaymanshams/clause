package com.clauseiq.contract;

import com.clauseiq.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;

/**
 * Hands contracts to the background processing executor.
 *
 * <p>The queue is in memory, so work queued when the app stops would be lost and those contracts would
 * stay UPLOADED/PROCESSING forever. On startup they are re-queued. This assumes a single application
 * instance; a durable queue would be needed to run several instances.
 */
@Component
public class ContractProcessingQueue {

    private static final Logger log = LoggerFactory.getLogger(ContractProcessingQueue.class);

    private final TaskExecutor executor;
    private final ContractProcessor processor;
    private final ContractRepository contractRepository;

    public ContractProcessingQueue(TaskExecutor contractProcessingExecutor, ContractProcessor processor,
                                   ContractRepository contractRepository) {
        this.executor = contractProcessingExecutor;
        this.processor = processor;
        this.contractRepository = contractRepository;
    }

    /** Queues a contract that is already in UPLOADED state; responds 503 if the queue is full. */
    public void enqueue(Long contractId, Long tenantId) {
        if (!submit(contractId, tenantId)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The processing queue is full; please reprocess this contract later");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void requeueInterruptedWork() {
        // System-level maintenance at startup (not a user request), so it intentionally spans all tenants.
        List<Contract> interrupted = contractRepository.findAllByStatusIn(
                EnumSet.of(Contract.Status.UPLOADED, Contract.Status.PROCESSING));
        if (!interrupted.isEmpty()) {
            log.info("Re-queueing {} contract(s) interrupted by a restart", interrupted.size());
        }
        interrupted.forEach(c -> submit(c.getId(), c.getTenantId()));
    }

    private boolean submit(Long contractId, Long tenantId) {
        try {
            executor.execute(() -> processor.process(contractId, tenantId));
            return true;
        } catch (TaskRejectedException e) {
            log.warn("Processing queue full; marking contract {} (tenant {}) as failed", contractId, tenantId);
            contractRepository.findByIdAndTenantId(contractId, tenantId).ifPresent(c -> {
                c.markFailed("The processing queue was full. Please reprocess this contract.");
                contractRepository.save(c);
            });
            return false;
        }
    }
}
