package com.bank.paymentmessages.entity;

public enum PaymentMessageStatus {
    RECEIVED,        // Message consommé depuis MQ et sauvegardé
    VALIDATING,      // Validation métier/structure en cours
    PROCESSING,      // Traitement métier en cours
    PROCESSED,       // Traitement terminé avec succès
    FAILED,          // Erreur technique ou métier
    RETRY_PENDING,   // À rejouer après une erreur temporaire
    REJECTED,        // Message invalide définitivement
    DEAD_LETTER      // Envoyé vers une Dead Letter Queue
}
