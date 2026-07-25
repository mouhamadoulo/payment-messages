package com.bank.paymentmessages.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Exécuteur dédié au rejeu massif : un seul thread et une file courte.
 * <p>
 * Le rejeu enchaîne des lots transactionnels ; l'exécuter sur l'exécuteur applicatif
 * partagé le mettrait en concurrence avec les autres traitements de fond, et le laisser
 * se paralléliser sursolliciterait la base pour aucun gain — les lots portent sur les
 * mêmes lignes.
 */
@Configuration
public class BatchRetryExecutorConfig {

    @Bean
    public TaskExecutor batchRetryExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(4);
        executor.setThreadNamePrefix("batch-retry-");
        // Le rejeu en cours doit pouvoir se terminer proprement à l'arrêt de l'application.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
