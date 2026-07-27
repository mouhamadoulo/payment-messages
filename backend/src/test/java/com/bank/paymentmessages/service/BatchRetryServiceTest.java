package com.bank.paymentmessages.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'exécuteur est synchrone : le rejeu est terminé au retour de {@code start()}, ce qui
 * permet d'observer son résultat sans attente.
 */
@ExtendWith(MockitoExtension.class)
class BatchRetryServiceTest {

    @Mock
    private PaymentMessageService service;

    @Test
    void shouldChainBatchesUntilOnePartialBatch() {
        when(service.retryFailedBatch(500)).thenReturn(500, 500, 120);

        BatchRetryTask task = batchRetryService(500, 100_000).start();

        verify(service, times(3)).retryFailedBatch(500);
        assertThat(task.taskId()).isNotBlank();
    }

    @Test
    void shouldStopAtSafetyCapAndFlagTruncation() {
        when(service.retryFailedBatch(500)).thenReturn(500);

        BatchRetryService batchRetry = batchRetryService(500, 1_000);
        BatchRetryTask started = batchRetry.start();

        BatchRetryTask finished = batchRetry.find(started.taskId()).orElseThrow();
        assertThat(finished.state()).isEqualTo(BatchRetryTask.State.COMPLETED);
        assertThat(finished.processed()).isEqualTo(1_000);
        assertThat(finished.truncated()).isTrue();
        verify(service, times(2)).retryFailedBatch(500);
    }

    @Test
    void shouldReportFailureInsteadOfPropagating() {
        when(service.retryFailedBatch(500)).thenThrow(new IllegalStateException("base indisponible"));

        BatchRetryService batchRetry = batchRetryService(500, 100_000);
        BatchRetryTask started = batchRetry.start();

        BatchRetryTask finished = batchRetry.find(started.taskId()).orElseThrow();
        assertThat(finished.state()).isEqualTo(BatchRetryTask.State.FAILED);
        assertThat(finished.error()).contains("base indisponible");
    }

    @Test
    void shouldForgetNothingBeforeTheTaskIsRead() {
        when(service.retryFailedBatch(500)).thenReturn(10);

        BatchRetryService batchRetry = batchRetryService(500, 100_000);
        BatchRetryTask started = batchRetry.start();

        assertThat(batchRetry.find(started.taskId())).isPresent();
        assertThat(batchRetry.find("inconnu")).isEmpty();
    }

    private BatchRetryService batchRetryService(int batchSize, int maxMessages) {
        return new BatchRetryService(service, new SyncTaskExecutor(), batchSize, maxMessages);
    }
}
