package org.tb.etl.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static org.tb.common.exception.ErrorCode.ETL_RUN_EXECUTOR_BUSY;
import static org.tb.etl.configuration.ETLExecutorConfiguration.ETL_TASK_EXECUTOR;
import static org.tb.etl.domain.ETLRunHistory.Status.FAILED;
import static org.tb.etl.domain.ETLRunHistory.Trigger.MANUAL;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.scheduling.SchedulerRequestAttributes;
import org.tb.common.util.DateTimeUtils;
import org.tb.etl.domain.ETLRunHistory;
import org.tb.etl.persistence.ETLRunHistoryRepository;

/**
 * Stößt einen ETL-Lauf aus der Oberfläche an (#1071).
 *
 * <p>Der Lauf läuft nebenläufig weiter, statt die Anfrage zu blockieren: er geht über mehrere
 * Definitionen und Referenzperioden und läuft in die Minuten. Das kostet nichts an
 * Nachvollziehbarkeit — die Zeile in {@code etl_run_history} entsteht beim Start (#573), der
 * angestoßene Lauf steht also sofort in derselben Liste, auf die umgeleitet wird.
 *
 * <p><b>Die Arbeitsteilung zwischen den beiden Threads ist der Kern dieser Klasse.</b> Im Thread der
 * Anfrage fallen alle Entscheidungen, die die anfragende Person betreffen: Zeitraum, Berechtigung,
 * welche Definitionen laufen, und ob überhaupt gestartet werden darf. Erst danach geht die Arbeit
 * auf den Hintergrundthread, und dort läuft sie als {@code SYSTEM}. Eine Rechteprüfung im
 * Hintergrund käme immer durch ({@link AuthorizedUser#initForJob}) — sie sähe aus wie eine Prüfung
 * und wäre keine.
 *
 * <p>Kein {@code @Async}: der gemeinsame Executor hat genau einen Thread und wird für die
 * Fortschreibung der Statistiken mitbenutzt. Die Begründung steht bei
 * {@link org.tb.etl.configuration.ETLExecutorConfiguration}.
 */
@Slf4j
@Service
@Authorized
public class ETLRunLauncher {

  private final ETLService etlService;
  private final ETLRunHistoryRepository runHistoryRepository;
  private final ConfigurableListableBeanFactory beanFactory;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;
  private final ThreadPoolTaskExecutor etlTaskExecutor;

  /**
   * Konstruktor von Hand statt {@code @RequiredArgsConstructor}: der Executor muss über
   * {@link Qualifier} benannt werden, und Lombok reicht die Annotation ohne {@code lombok.config}
   * nicht an den Konstruktorparameter weiter. Ohne den Namen träfe die Auswahl den Standard-Pool
   * von Spring Boot.
   */
  public ETLRunLauncher(ETLService etlService,
                        ETLRunHistoryRepository runHistoryRepository,
                        ConfigurableListableBeanFactory beanFactory,
                        ObjectProvider<AuthorizedUser> authorizedUserProvider,
                        @Qualifier(ETL_TASK_EXECUTOR) ThreadPoolTaskExecutor etlTaskExecutor) {
    this.etlService = etlService;
    this.runHistoryRepository = runHistoryRepository;
    this.beanFactory = beanFactory;
    this.authorizedUserProvider = authorizedUserProvider;
    this.etlTaskExecutor = etlTaskExecutor;
  }

  /**
   * Stößt einen Lauf an und kehrt zurück, sobald er eröffnet ist.
   *
   * @param etlName die gewählte Definition, oder {@code null} für „alle, die ich ausführen darf"
   * @return der eröffnete Lauf — er steht ab sofort mit Status {@code RUNNING} in der Liste
   */
  public ETLRunHistory startManualRun(LocalDateRange dateRange, String etlName) {
    var etlNames = etlService.resolveManualRun(dateRange, etlName);
    var run = etlService.startRun(dateRange, MANUAL);

    try {
      etlTaskExecutor.execute(() -> runInBackground(run.getId(), dateRange, etlNames));
    } catch (TaskRejectedException e) {
      // Der Weg hierher ist schmal, aber vorgesehen (→ ADR-0028): wer einen laufenden Lauf von Hand
      // als beendet markiert, hält ihn damit nicht an — die Zeile gibt die Sperre frei, der Thread
      // bleibt belegt. Genau dann weist der Executor ab, und das ist die Rückfalllinie, die einen
      // zweiten gleichzeitigen Lauf verhindert.
      //
      // Zwei Dinge müssen hier passieren: die eben eröffnete Zeile darf nicht als „läuft" stehen
      // bleiben und jeden weiteren Start sperren, und die anfragende Person braucht eine Meldung
      // statt einer Fehlerseite — eine durchgereichte TaskRejectedException fängt niemand.
      log.error("Manual ETL run could not be handed to the executor", e);
      markRejected(run);
      throw new BusinessRuleException(ETL_RUN_EXECUTOR_BUSY, e);
    }
    return run;
  }

  private void runInBackground(long runId, LocalDateRange dateRange, List<String> etlNames) {
    // Ohne HTTP-Anfrage gibt es keinen Request-Scope; die request-scoped AuthorizedUser-Bohne
    // braucht denselben Vorlauf wie in einem geplanten Job (→ ADR-0006).
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      etlService.continueRun(runId, dateRange, etlNames);
    } catch (Exception e) {
      // Der Lauf hält sein Scheitern in seiner eigenen Zeile fest; hier bliebe die Ausnahme sonst
      // im Thread stecken, ohne dass irgendwo etwas davon stünde.
      log.error("Manual ETL run failed", e);
    } finally {
      try {
        beanFactory.destroyScopedBean("authorizedUser");
      } catch (Exception ignored) {
        // nothing to clean up if the scoped bean was never created
      }
      resetRequestAttributes();
    }
  }

  private void markRejected(ETLRunHistory run) {
    run.setStatus(FAILED);
    run.setFinishedAt(DateTimeUtils.now());
    run.setMessage("Der Lauf konnte nicht gestartet werden: kein freier Ausführungsthread.");
    runHistoryRepository.save(run);
  }

}
