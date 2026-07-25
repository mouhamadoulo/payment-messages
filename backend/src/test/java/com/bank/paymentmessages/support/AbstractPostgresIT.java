package com.bank.paymentmessages.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Socle des tests d'intégration exécutés sur un vrai PostgreSQL.
 * <p>
 * Les tests unitaires tournent sur H2 avec un schéma généré par Hibernate et
 * {@code spring.flyway.enabled: false} : ils ne peuvent donc <b>pas</b> détecter une
 * migration cassée, un type de colonne divergent ni une syntaxe propre à PostgreSQL
 * ({@code TIMESTAMPTZ}, blocs {@code DO $$}, {@code ON CONFLICT}). Ce socle rejoue les
 * migrations sur le moteur réel, Hibernate en {@code validate}.
 * <p>
 * Le conteneur est démarré une fois pour toute la campagne (motif <i>singleton</i>) plutôt
 * que par classe : les valeurs injectées étant identiques d'une classe à l'autre, le
 * contexte Spring est mis en cache et réutilisé. L'arrêt est délégué au conteneur
 * <i>Ryuk</i> de Testcontainers, qui nettoie à la fin de la JVM.
 * <p>
 * La connexion est câblée par {@link DynamicPropertySource} et non par
 * {@code @ServiceConnection} : la fabrique de personnalisation de contexte lit les champs
 * annotés <b>par réflexion dès la découverte des tests</b>, ce qui initialise la classe —
 * et donc démarrerait le conteneur — avant même que la condition « Docker joignable » ait
 * été évaluée. Le test échouait alors au lieu d'être ignoré. La recherche d'une méthode,
 * elle, n'initialise rien.
 * <p>
 * Les classes dérivées doivent porter {@link RequiresDocker} : les conditions JUnit ne
 * sont pas héritées.
 */
@SpringBootTest
@ActiveProfiles({"test", "integration"})
public abstract class AbstractPostgresIT {

    /**
     * Même version majeure que l'environnement d'exécution ({@code docker-compose.yaml}) :
     * tester les migrations sur une autre version reviendrait à ne pas les tester.
     */
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("payment_messages")
            .withUsername("payment")
            .withPassword("payment");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
