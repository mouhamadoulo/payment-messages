package com.bank.paymentmessages.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMessageStatusTest {

    @Test
    void receivedShouldOnlyReachProcessedOrFailed() {
        assertThat(PaymentMessageStatus.RECEIVED.canTransitionTo(PaymentMessageStatus.PROCESSED)).isTrue();
        assertThat(PaymentMessageStatus.RECEIVED.canTransitionTo(PaymentMessageStatus.FAILED)).isTrue();
        // Aucune tentative n'a eu lieu : l'abandon direct n'a pas de sens.
        assertThat(PaymentMessageStatus.RECEIVED.canTransitionTo(PaymentMessageStatus.DEAD_LETTER)).isFalse();
    }

    @Test
    void failedShouldBeReplayableResolvableOrAbandonable() {
        assertThat(PaymentMessageStatus.FAILED.allowedTransitions())
                .containsExactlyInAnyOrder(PaymentMessageStatus.RECEIVED,
                        PaymentMessageStatus.PROCESSED,
                        PaymentMessageStatus.DEAD_LETTER);
    }

    @Test
    void processedAndDeadLetterShouldBeTerminal() {
        assertThat(PaymentMessageStatus.PROCESSED.isTerminal()).isTrue();
        assertThat(PaymentMessageStatus.DEAD_LETTER.isTerminal()).isTrue();
        assertThat(PaymentMessageStatus.PROCESSED.canTransitionTo(PaymentMessageStatus.RECEIVED)).isFalse();
        assertThat(PaymentMessageStatus.DEAD_LETTER.canTransitionTo(PaymentMessageStatus.RECEIVED)).isFalse();
    }

    /** Rejouer une commande déjà appliquée ne doit pas être une erreur. */
    @Test
    void sameStatusShouldAlwaysBeAccepted() {
        for (PaymentMessageStatus status : PaymentMessageStatus.values()) {
            assertThat(status.canTransitionTo(status)).isTrue();
        }
    }
}
