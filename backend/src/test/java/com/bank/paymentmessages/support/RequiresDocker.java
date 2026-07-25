package com.bank.paymentmessages.support;

import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ignore la classe de test si aucun démon Docker ne répond.
 * <p>
 * À porter sur <b>chaque</b> classe concrète : les annotations de condition JUnit ne sont
 * pas {@code @Inherited}, les poser sur {@link AbstractPostgresIT} ne protégerait donc
 * aucune des classes dérivées.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnabledIf(value = "com.bank.paymentmessages.support.DockerAvailable#isDockerAvailable",
        disabledReason = "Aucun démon Docker joignable : test d'intégration ignoré")
public @interface RequiresDocker {
}
