-- Schéma de référence de payment_messages.
--
-- Écrit de façon idempotente (IF NOT EXISTS) : les bases existantes ont été créées par
-- Hibernate `ddl-auto: update` avant l'introduction de Flyway. Avec
-- `baseline-on-migrate: true` et `baseline-version: 0`, cette migration s'applique aussi
-- à ces bases sans rien détruire ; V2 se charge ensuite des colonnes et types manquants.

CREATE TABLE IF NOT EXISTS payment_messages (
    id                BIGSERIAL    PRIMARY KEY,
    version           BIGINT,
    message_id        VARCHAR(255) NOT NULL UNIQUE,
    reference         VARCHAR(255) NOT NULL,
    message_type      VARCHAR(255),
    status            VARCHAR(255) NOT NULL,
    payload           TEXT,
    payload_size      INTEGER,
    retry_count       INTEGER      NOT NULL DEFAULT 0,
    error_message     TEXT,
    received_at       TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ,
    dlq_published_at  TIMESTAMPTZ
);

-- Recherche par référence métier.
CREATE INDEX IF NOT EXISTS idx_pm_reference ON payment_messages (reference);

-- Tri par défaut de la liste (receivedAt DESC).
CREATE INDEX IF NOT EXISTS idx_pm_received_at ON payment_messages (received_at DESC);

-- Couvre le filtre par statut seul ET le couple filtre + tri : c'est cet index qui évite
-- le seq scan + tri complet sur les pages de la liste.
CREATE INDEX IF NOT EXISTS idx_pm_status_received_at ON payment_messages (status, received_at DESC);

-- Reprise DLQ : messages DEAD_LETTER dont la publication n'est pas confirmée.
CREATE INDEX IF NOT EXISTS idx_pm_status_dlq_published_at ON payment_messages (status, dlq_published_at);
