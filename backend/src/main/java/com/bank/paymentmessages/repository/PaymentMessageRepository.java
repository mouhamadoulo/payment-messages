package com.bank.paymentmessages.repository;

import com.bank.paymentmessages.entity.PaymentMessage;
import com.bank.paymentmessages.entity.PaymentMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentMessageRepository  extends JpaRepository<PaymentMessage,Long> {

    Optional<PaymentMessage> findByMessageId(String messageId);

    boolean existsByMessageId(String messageId);

    Optional<PaymentMessage> findByReference(String reference);

    // --- Listes paginées : projections sans payload (cf. PaymentMessageSummary) ---

    Page<PaymentMessageSummary> findAllProjectedBy(Pageable pageable);

    Page<PaymentMessageSummary> findByStatus(PaymentMessageStatus status, Pageable pageable);

    Page<PaymentMessageSummary> findByReceivedAtAfter(OffsetDateTime receivedAfter, Pageable pageable);

    Page<PaymentMessageSummary> findByStatusAndReceivedAtAfter(PaymentMessageStatus status,
                                                               OffsetDateTime receivedAfter,
                                                               Pageable pageable);

    /**
     * Pagination par curseur (<i>keyset</i>) : pas de {@code COUNT(*)}, pas d'{@code OFFSET},
     * donc un coût constant quelle que soit la profondeur de navigation. Le curseur est le
     * couple {@code (receivedAt, id)} de la dernière ligne rendue ; {@code null} sur la
     * première page.
     * <p>
     * Le tri est figé sur {@code received_at DESC, id DESC} : c'est ce qui rend le curseur
     * total (l'{@code id} départage deux messages reçus au même instant) et ce qui permet à
     * l'index {@code idx_pm_status_received_at} de servir la requête.
     */
    @Query("""
            SELECT new com.bank.paymentmessages.repository.PaymentMessageSummary(
                       p.id, p.messageId, p.reference, p.messageType, p.status,
                       p.payloadSize, p.retryCount, p.errorMessage, p.receivedAt, p.updatedAt)
            FROM PaymentMessage p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:receivedAfter IS NULL OR p.receivedAt > :receivedAfter)
              AND (:cursorReceivedAt IS NULL
                   OR p.receivedAt < :cursorReceivedAt
                   OR (p.receivedAt = :cursorReceivedAt AND p.id < :cursorId))
            ORDER BY p.receivedAt DESC, p.id DESC
            """)
    List<PaymentMessageSummary> findNextPage(@Param("status") PaymentMessageStatus status,
                                             @Param("receivedAfter") OffsetDateTime receivedAfter,
                                             @Param("cursorReceivedAt") OffsetDateTime cursorReceivedAt,
                                             @Param("cursorId") Long cursorId,
                                             Pageable pageable);

    // --- Traitements par lots : entités complètes ---

    /** Lot borné de messages dans un statut donné, pour un traitement itératif. */
    Page<PaymentMessage> findAllByStatus(PaymentMessageStatus status, Pageable pageable);

    /**
     * Messages passés en DEAD_LETTER dont la republication sur la DLQ n'a jamais été
     * confirmée. Alimente la reprise planifiée qui rattrape les divergences base / broker.
     */
    List<PaymentMessage> findByStatusAndDlqPublishedAtIsNull(PaymentMessageStatus status, Pageable pageable);

    /** Identifiants purgeables par la rétention : lot borné pour éviter un DELETE massif. */
    @Query("SELECT p.id FROM PaymentMessage p WHERE p.status = :status AND p.receivedAt < :cutoff")
    List<Long> findPurgeableIds(@Param("status") PaymentMessageStatus status,
                                @Param("cutoff") OffsetDateTime cutoff,
                                Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM PaymentMessage p GROUP BY p.status")
    List<Object[]> countByStatus();
}
