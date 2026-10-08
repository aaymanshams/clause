package com.clauseiq.contract;

import com.clauseiq.common.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractProcessingQueueTest {

    @Mock
    private TaskExecutor executor;

    @Mock
    private ContractProcessor processor;

    @Mock
    private ContractRepository repository;

    @InjectMocks
    private ContractProcessingQueue queue;

    @Test
    void fullQueueMarksContractFailedAndReturns503() {
        Contract contract = new Contract(1L, 1L, "a.pdf", "application/pdf", 10, "p");
        doThrow(new TaskRejectedException("full")).when(executor).execute(any());
        when(repository.findByIdAndTenantId(5L, 1L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> queue.enqueue(5L, 1L))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(contract.getStatus()).isEqualTo(Contract.Status.FAILED);
        verify(repository).save(contract);
    }

    @Test
    void contractsInterruptedByARestartAreRequeuedWithTheirOwnTenant() {
        Contract stuck = new Contract(42L, 1L, "a.pdf", "application/pdf", 10, "p");
        org.springframework.test.util.ReflectionTestUtils.setField(stuck, "id", 9L);
        when(repository.findAllByStatusIn(anyCollection())).thenReturn(List.of(stuck));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any());

        queue.requeueInterruptedWork();

        verify(processor).process(9L, 42L);
    }
}
