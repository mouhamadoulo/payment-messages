package com.bank.paymentmessages.service;

import com.bank.paymentmessages.dto.api.SimulationSendRequest;
import com.bank.paymentmessages.exception.SimulationDisabledException;
import com.bank.paymentmessages.mq.SimulationPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'exécuteur est synchrone : l'envoi est terminé au retour de {@code start()}, ce qui permet
 * d'observer son résultat sans attente. La cadence est poussée au plafond pour que les
 * temporisations restent négligeables.
 */
@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    private static final String QUEUE = "TEST.QUEUE";

    private static final String PAYLOAD = """
            {"messageId":"MSG-1","messageType":"SEPA","reference":"REF-1"}""";

    @Mock
    private SimulationPublisher publisher;

    /** La destination n'est pas un paramètre : elle vient de la configuration. */
    @Test
    void shouldPublishRequestedCountOnTheConfiguredQueue() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true);

        SimulationTask task = simulation(true).start(request(PAYLOAD, 3, true));

        verify(publisher, times(3)).publish(eq(QUEUE), anyString());
        assertThat(task.destination()).isEqualTo(QUEUE);
    }

    @Test
    void shouldRewriteMessageIdOnEachCopy() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true);

        simulation(true).start(request(PAYLOAD, 3, true));

        assertThat(publishedIds()).doesNotHaveDuplicates().hasSize(3);
    }

    /**
     * Sans réécriture, l'ingestion — idempotente sur {@code messageId} — ne conserverait
     * qu'une seule des copies. C'est un choix explicite de l'appelant, pas un défaut.
     */
    @Test
    void shouldLeavePayloadUntouchedWhenUniqueIdsDisabled() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true);

        simulation(true).start(request(PAYLOAD, 2, false));

        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(publisher, times(2)).publish(anyString(), bodies.capture());
        assertThat(bodies.getAllValues()).containsOnly(PAYLOAD);
    }

    /** Cas de test du rejet : le payload illisible doit partir tel quel, pas être corrigé. */
    @Test
    void shouldPublishUnparseablePayloadAsIs() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true);
        String invalid = "{ messageId: pas du JSON }";

        simulation(true).start(request(invalid, 1, true));

        verify(publisher).publish(QUEUE, invalid);
    }

    @Test
    void shouldCountPublicationFailuresWithoutStopping() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true, false, true);

        SimulationService simulation = simulation(true);
        SimulationTask started = simulation.start(request(PAYLOAD, 3, true));

        SimulationTask finished = simulation.find(started.taskId()).orElseThrow();
        assertThat(finished.state()).isEqualTo(SimulationTask.State.COMPLETED);
        assertThat(finished.sent()).isEqualTo(3);
        assertThat(finished.published()).isEqualTo(2);
        assertThat(finished.failed()).isEqualTo(1);
    }

    @Test
    void shouldRejectCountAboveTheCap() {
        assertThatThrownBy(() -> simulation(true).start(request(PAYLOAD, 5_000, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldRejectRateAboveTheCap() {
        SimulationService simulation = simulation(true);

        assertThatThrownBy(() -> simulation.start(
                new SimulationSendRequest(PAYLOAD, 1, 10_000, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ratePerSecond");
    }

    @Test
    void shouldRefuseToSendWhenDisabled() {
        assertThatThrownBy(() -> simulation(false).start(request(PAYLOAD, 1, true)))
                .isInstanceOf(SimulationDisabledException.class);
    }

    @Test
    void shouldExposeTheConfiguredQueueAndCaps() {
        var config = simulation(true).config();

        assertThat(config.enabled()).isTrue();
        assertThat(config.queue()).isEqualTo(QUEUE);
        assertThat(config.maxCount()).isEqualTo(10);
        assertThat(config.maxRate()).isEqualTo(1_000);
    }

    @Test
    void shouldForgetNothingBeforeTheTaskIsRead() {
        when(publisher.publish(anyString(), anyString())).thenReturn(true);

        SimulationService simulation = simulation(true);
        SimulationTask started = simulation.start(request(PAYLOAD, 1, true));

        assertThat(simulation.find(started.taskId())).isPresent();
        assertThat(simulation.find("inconnu")).isEmpty();
    }

    private List<String> publishedIds() {
        ArgumentCaptor<String> bodies = ArgumentCaptor.forClass(String.class);
        verify(publisher, times(3)).publish(anyString(), bodies.capture());
        JsonMapper mapper = JsonMapper.builder().build();
        return bodies.getAllValues().stream()
                .map(body -> mapper.readTree(body).get("messageId").asString())
                .toList();
    }

    private static SimulationSendRequest request(String payload, int count, boolean uniqueIds) {
        // Cadence au plafond : la boucle n'attend quasiment pas entre deux publications.
        return new SimulationSendRequest(payload, count, 1_000, uniqueIds);
    }

    private SimulationService simulation(boolean enabled) {
        return new SimulationService(publisher, JsonMapper.builder().build(), new SyncTaskExecutor(),
                enabled, 10, 1_000, QUEUE);
    }
}
