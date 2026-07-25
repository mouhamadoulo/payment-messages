package com.bank.paymentmessages;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@OpenAPIDefinition(
        info = @Info(
                title = "Payment Messages API",
                version = "1.0",
                description = "API REST de gestion des messages IBM MQ",
                contact = @Contact(
                        name = "Mouhamadou LO",
                        email = "mouhamadoulo39@gmail.com"
                )
        )
)
@SpringBootApplication
@EnableScheduling
public class PaymentMessagesApplication {

	/** Répertoire des journaux du client IBM MQ, relatif au répertoire de travail. */
	private static final String MQ_LOG_DIR = "logs";

	public static void main(String[] args) {

		redirectMqClientLogs();

		SpringApplication.run(PaymentMessagesApplication.class, args);
	}

	/**
	 * Sort les journaux du client IBM MQ du répertoire de travail.
	 * <p>
	 * Par défaut le client écrit {@code mqjms.log.*} et ses clichés d'incident (FFST) là où
	 * la JVM a été lancée : à la racine du module en développement, dans une couche en
	 * lecture seule en conteneur. Ces propriétés sont lues une seule fois, au premier
	 * chargement des classes de services communs — donc avant tout bean Spring, d'où leur
	 * positionnement ici plutôt que dans une {@code @Configuration}.
	 * <p>
	 * Les valeurs déjà fournies en ligne de commande ({@code -Dcom.ibm.msg.client...}) sont
	 * respectées : elles restent le moyen d'ajuster le niveau de trace en exploitation.
	 */
	private static void redirectMqClientLogs() {

		try {
			// Le gestionnaire de fichiers sous-jacent (java.util.logging) ne crée pas
			// l'arborescence : sans ce répertoire, le client retomberait sur stderr.
			Files.createDirectories(Path.of(MQ_LOG_DIR));
		} catch (IOException e) {
			// Répertoire de travail en lecture seule : le client basculera sur stderr,
			// ce qui reste préférable à un refus de démarrage pour un journal secondaire.
			System.err.println("Répertoire de journaux IBM MQ non créé (" + MQ_LOG_DIR + ") : " + e.getMessage());
			return;
		}

		setIfAbsent("com.ibm.msg.client.commonservices.log.outputName", MQ_LOG_DIR + "/mqjms.log");
		setIfAbsent("com.ibm.msg.client.commonservices.trace.outputName", MQ_LOG_DIR + "/mqjms.trc");
		setIfAbsent("com.ibm.msg.client.commonservices.dumplocation", MQ_LOG_DIR);
	}

	private static void setIfAbsent(String key, String value) {
		if (System.getProperty(key) == null) {
			System.setProperty(key, value);
		}
	}

}
