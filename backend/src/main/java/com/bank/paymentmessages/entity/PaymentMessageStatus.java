package com.bank.paymentmessages.entity;

public enum PaymentMessageStatus {
    RECEIVED,     // Message consommé depuis MQ et sauvegardé, en attente de traitement
    PROCESSED,    // Traitement terminé avec succès
    FAILED,       // Erreur technique ou métier, rejouable via /retry
    DEAD_LETTER   // Abandonné après trop de tentatives, republié sur la Dead Letter Queue
}
