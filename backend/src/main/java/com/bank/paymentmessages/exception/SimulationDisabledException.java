package com.bank.paymentmessages.exception;

/**
 * La simulation d'envoi est coupée sur cet environnement ({@code app.simulation.enabled}).
 * Elle injecte de vrais messages dans la file de production du flux : elle n'a rien à faire
 * ailleurs qu'en développement ou en recette.
 */
public class SimulationDisabledException extends RuntimeException {

    public SimulationDisabledException() {
        super("La simulation d'envoi est désactivée sur cet environnement");
    }
}
