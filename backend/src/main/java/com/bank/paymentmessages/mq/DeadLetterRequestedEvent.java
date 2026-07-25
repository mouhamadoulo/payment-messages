package com.bank.paymentmessages.mq;

/**
 * Émis quand un message vient de passer en {@code DEAD_LETTER} en base. La publication
 * effective sur la DLQ n'a lieu qu'<b>après le commit</b> : sans cela, un rollback
 * postérieur laisserait un message dans la file sans ligne correspondante en base.
 *
 * @param id identifiant de la ligne {@code payment_messages}
 */
public record DeadLetterRequestedEvent(Long id) {
}
