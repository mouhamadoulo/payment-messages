package com.bank.paymentmessages.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;


/**
 * Ligne de la table {@code payment_messages}.
 * <p>
 * Le schéma physique est géré par Flyway ({@code db/migration}) : les {@link Index}
 * déclarés ici ne servent qu'à la génération de schéma des tests (H2, {@code create-drop})
 * et doivent rester alignés sur les migrations.
 * <p>
 * Les horodatages sont des {@link OffsetDateTime} (colonnes {@code timestamptz}) : un
 * flux de paiement traverse plusieurs fuseaux et les conteneurs n'ont pas toujours le
 * {@code TZ} du poste de développement.
 */
@Entity
@Table(name="payment_messages",
        indexes = {
                @Index(name="idx_pm_reference", columnList="reference"),
                // Tri par défaut de la liste (receivedAt DESC).
                @Index(name="idx_pm_received_at", columnList="received_at DESC"),
                // Couvre à la fois le filtre par statut seul et le couple filtre + tri.
                @Index(name="idx_pm_status_received_at", columnList="status, received_at DESC"),
                // Filtre par type (serveur) et liste des types distincts de la barre de filtres.
                @Index(name="idx_pm_message_type", columnList="message_type"),
                // Alimente la reprise DLQ (statut DEAD_LETTER sans publication confirmée).
                @Index(name="idx_pm_status_dlq_published_at", columnList="status, dlq_published_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Verrouillage optimiste : deux changements de statut concurrents sur la même ligne
     * ne peuvent plus s'écraser silencieusement, le second échoue en 409.
     */
    @Version
    private Long version;

    @Column(nullable=false, unique=true)
    private String messageId;

    @Column(nullable=false)
    private String reference;

    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMessageStatus status;

    @Column(columnDefinition="TEXT")
    private String payload;

    /**
     * Taille du payload en octets, calculée à l'ingestion. Permet d'afficher la taille
     * dans les listes sans transporter le payload complet.
     */
    private Integer payloadSize;

    @Builder.Default
    @Column(nullable=false)
    private Integer retryCount = 0;

    @Column(columnDefinition="TEXT")
    private String errorMessage;

    private OffsetDateTime receivedAt;

    private OffsetDateTime updatedAt;

    /**
     * Horodatage de la republication effective sur la Dead Letter Queue.
     * Reste {@code null} tant que le broker n'a pas accusé réception : c'est ce
     * marqueur qui permet de détecter les divergences base / DLQ et de les rattraper.
     */
    private OffsetDateTime dlqPublishedAt;

}
