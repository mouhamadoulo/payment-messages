package com.bank.paymentmessages.db;

import com.bank.paymentmessages.support.AbstractPostgresIT;
import com.bank.paymentmessages.support.RequiresDocker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrôle du schéma tel que Flyway le produit réellement sur PostgreSQL.
 * <p>
 * Le simple démarrage du contexte couvre déjà l'essentiel : les migrations s'appliquent et
 * Hibernate, en {@code validate}, refuse de démarrer si une entité et le schéma divergent.
 * Les assertions ci-dessous portent sur ce que {@code validate} ne regarde pas — les index
 * et le fuseau des horodatages — et sur ce que H2 ne sait pas exprimer.
 */
@RequiresDocker
class SchemaMigrationIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsShouldAllBeApplied() {

        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(row -> row.get("version")).contains("1", "2", "3");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    /**
     * Un flux de paiement traverse plusieurs fuseaux et les conteneurs n'ont pas celui du
     * poste de développement : une colonne {@code timestamp without time zone} ferait
     * dériver les horodatages d'un environnement à l'autre (D8).
     */
    @Test
    void timestampsShouldCarryTheirTimeZone() {

        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_name = 'payment_messages'
                  AND column_name IN ('received_at', 'updated_at', 'dlq_published_at')
                """);

        assertThat(columns).hasSize(3);
        assertThat(columns).allSatisfy(column ->
                assertThat(column.get("data_type")).isEqualTo("timestamp with time zone"));
    }

    /** Les index conditionnent le coût des listes et de la reprise DLQ (D1). */
    @Test
    void queryIndexesShouldExist() {

        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'payment_messages'", String.class);

        assertThat(indexes).contains(
                "idx_pm_reference",
                "idx_pm_received_at",
                "idx_pm_status_received_at",
                "idx_pm_status_dlq_published_at",
                // Filtre par type côté serveur et liste des types distincts (F3).
                "idx_pm_message_type");
    }

    /** L'idempotence de l'ingestion repose entièrement sur cette contrainte (B4). */
    @Test
    void messageIdShouldBeUnique() {

        Integer uniqueConstraints = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_index i
                JOIN pg_class t ON t.oid = i.indrelid
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY (i.indkey)
                WHERE t.relname = 'payment_messages'
                  AND i.indisunique
                  AND a.attname = 'message_id'
                """, Integer.class);

        assertThat(uniqueConstraints).isEqualTo(1);
    }
}
