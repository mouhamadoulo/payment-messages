package com.bank.paymentmessages.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur dédié aux envois de test : un seul thread, une file courte.
 * <p>
 * L'envoi passe l'essentiel de son temps à attendre entre deux publications pour tenir la
 * cadence demandée. Le laisser sur l'exécuteur applicatif partagé y immobiliserait un thread
 * pendant toute la durée de l'envoi ; le paralléliser casserait la cadence, qui est
 * précisément ce que l'écran permet de régler.
 */
@Configuration
public class SimulationExecutorConfig {

    @Bean
    public TaskExecutor simulationExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("mq-simulation-");
        // Un envoi cadencé peut durer une minute : à l'arrêt on l'interrompt plutôt que
        // de retarder d'autant l'extinction du contexte. La tâche le détecte et se termine.
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
