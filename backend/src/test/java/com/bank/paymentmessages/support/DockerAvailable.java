package com.bank.paymentmessages.support;

import org.testcontainers.DockerClientFactory;

/**
 * Condition d'exécution des tests d'intégration : sans démon Docker, ils sont
 * <i>skipped</i> plutôt qu'en échec, pour que la boucle locale reste possible.
 * <p>
 * Classe distincte de {@link AbstractPostgresIT} <b>à dessein</b> : porter la méthode sur
 * la classe de base l'initialiserait au moment d'évaluer la condition, donc démarrerait le
 * conteneur PostgreSQL avant même de savoir si Docker répond.
 * <p>
 * {@code @EnabledIfDockerAvailable} (Testcontainers 2.0.5) ne convient pas ici : sa
 * condition appelle {@code getRequiredTestClass()} au niveau conteneur de test, ce que
 * JUnit 6 refuse — « required test class is not present in the current ExtensionContext ».
 */
public final class DockerAvailable {

    private DockerAvailable() {
    }

    public static boolean isDockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }
}
