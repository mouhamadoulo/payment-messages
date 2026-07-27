package com.bank.paymentmessages.mq;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.repository.PaymentMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Publie sur la Dead Letter Queue une fois la transaction métier <b>committée</b>.
 * <p>
 * Publier pendant la transaction exposait à une incohérence : un rollback après l'envoi
 * (violation de contrainte, verrou optimiste, panne de la base) laissait un message dans
 * la DLQ sans ligne {@code DEAD_LETTER} en base. Ici, la publication ne peut plus précéder
 * le commit ; l'échec inverse — commit sans publication — reste couvert par
 * {@link DeadLetterRecoveryJob}, puisque {@code dlqPublishedAt} n'est posé qu'après un
 * accusé du broker.
 */
@Component
public class DeadLetterDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterDispatcher.class);

    private final PaymentMessageRepository repository;
    private final DeadLetterPublisher publisher;

    public DeadLetterDispatcher(PaymentMessageRepository repository, DeadLetterPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * {@code fallbackExecution = true} : hors transaction (tests unitaires, appel direct),
     * l'événement est traité immédiatement au lieu d'être silencieusement perdu.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDeadLetterRequested(DeadLetterRequestedEvent event) {

        Optional<PaymentMessage> found = repository.findById(event.id());

        if (found.isEmpty()) {
            // Ligne supprimée entre le commit et la publication : plus rien à republier.
            log.warn("Publication DLQ abandonnée, message {} introuvable", event.id());
            return;
        }

        PaymentMessage message = found.get();

        if (message.getDlqPublishedAt() != null) {
            return;
        }

        if (publisher.publish(message)) {
            message.setDlqPublishedAt(OffsetDateTime.now());
            repository.save(message);
        }
        // Sinon la ligne reste sans dlqPublishedAt : DeadLetterRecoveryJob la reprendra.
    }
}
