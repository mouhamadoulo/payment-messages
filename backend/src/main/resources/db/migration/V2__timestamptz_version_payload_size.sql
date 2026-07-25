-- Mise à niveau des bases créées par Hibernate `ddl-auto: update` avant Flyway.
-- Sur une base créée par V1, tout est déjà en place : chaque instruction est un no-op.

-- Verrouillage optimiste des changements de statut concurrents.
ALTER TABLE payment_messages ADD COLUMN IF NOT EXISTS version BIGINT;

-- Taille du payload calculée à l'ingestion : les listes n'ont plus à transporter le payload.
ALTER TABLE payment_messages ADD COLUMN IF NOT EXISTS payload_size INTEGER;

-- Horodatages avec fuseau : un flux de paiement traverse plusieurs zones et les conteneurs
-- n'ont pas forcément le TZ du poste de développement. Les valeurs existantes ont été
-- écrites par une JVM dont le fuseau était celui du serveur : c'est donc lui qui sert de
-- référence pour la conversion.
DO $$
DECLARE
    col TEXT;
BEGIN
    FOREACH col IN ARRAY ARRAY['received_at', 'updated_at', 'dlq_published_at'] LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'payment_messages'
              AND column_name = col
              AND data_type = 'timestamp without time zone'
        ) THEN
            EXECUTE format(
                'ALTER TABLE payment_messages ALTER COLUMN %I TYPE TIMESTAMPTZ USING %I AT TIME ZONE current_setting(''TimeZone'')',
                col, col);
        END IF;
    END LOOP;
END $$;

-- Backfill de payload_size sur l'historique (octets, comme le calcul applicatif).
UPDATE payment_messages
SET payload_size = octet_length(payload)
WHERE payload_size IS NULL;

-- Index absents des bases générées par Hibernate.
CREATE INDEX IF NOT EXISTS idx_pm_received_at ON payment_messages (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_pm_status_received_at ON payment_messages (status, received_at DESC);
CREATE INDEX IF NOT EXISTS idx_pm_status_dlq_published_at ON payment_messages (status, dlq_published_at);

-- L'index historique portait un autre nom (idx_message_reference).
DROP INDEX IF EXISTS idx_message_reference;
CREATE INDEX IF NOT EXISTS idx_pm_reference ON payment_messages (reference);
