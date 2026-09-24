package org.tb.etl.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Der Thread, auf dem ein von Hand angestoßener ETL-Lauf läuft (#1071).
 *
 * <p><b>Bewusst nicht der gemeinsame Async-Pool.</b> Der hat genau einen Thread
 * ({@code SalatApplication#getAsyncExecutor}, {@code corePoolSize = maxPoolSize = 1}) und wird von
 * {@code StatisticService} für die Fortschreibung der Ist-Aufwände mitbenutzt. Ein Lauf über drei
 * Monate und alle Definitionen läuft in die Minuten und legte diese Fortschreibung so lange still —
 * unsichtbar, denn die Aufgaben gehen nicht verloren, sie kommen nur später.
 *
 * <p><b>Warteschlange der Länge null.</b> Damit weist der Executor eine zweite Einreichung ab
 * ({@code TaskRejectedException}), statt sie anzustellen. Das ist nicht die Sperre — die ist die
 * {@code RUNNING}-Zeile in {@code etl_run_history} —, sondern die Rückfalllinie dahinter: was an der
 * Sperre vorbeikommt, wird abgewiesen und nicht heimlich für später vorgemerkt.
 *
 * <p>Die einschaltende Annotation {@code @EnableAsync} bleibt an {@code SalatApplication}
 * (→ AGENTS.md, „Spring Boot annotations placement"); ein modul-eigener Executor ist keine solche
 * Annotation.
 */
@Configuration
public class ETLExecutorConfiguration {

  /** Bean-Name des Executors, auf dem ein angestoßener Lauf läuft. */
  public static final String ETL_TASK_EXECUTOR = "etlTaskExecutor";

  @Bean(ETL_TASK_EXECUTOR)
  public ThreadPoolTaskExecutor etlTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("EtlRun-");
    return executor;
  }

}
