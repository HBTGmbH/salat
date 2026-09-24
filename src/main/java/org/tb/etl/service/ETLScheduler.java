package org.tb.etl.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static org.tb.common.exception.ErrorCode.ETL_RUN_ALREADY_RUNNING;
import static org.tb.etl.domain.ETLRunHistory.Trigger.SCHEDULED;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.scheduling.SchedulerRequestAttributes;
import org.tb.common.util.DateUtils;

/**
 * Der nächtliche ETL-Lauf.
 *
 * <p>Bis #1071 stand er in {@link ETLService} und kam ohne Anmeldung aus, weil ein Parameter
 * {@code scheduled} die Rechteprüfung schlicht übersprang. Derselbe Parameter entschied auch, was
 * als Auslöser in der Historie landete — zwei Bedeutungen in einem Schalter, und für einen von Hand
 * angestoßenen Lauf fallen sie auseinander. Der Schalter ist damit gefallen; der geplante Lauf
 * bringt stattdessen eine Anmeldung mit, wie jeder andere geplante Job der Anwendung auch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ETLScheduler {

  private final ETLService etlService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Scheduled(cron = "0 0 2 * * *") // täglich um 02:00
  public void runDaily() {
    var today = DateUtils.today();
    var range = new LocalDateRange(today.minusMonths(3), today);
    log.info("Starting scheduled daily ETL run for date range: {}", range);

    // Ein geplanter Lauf hat keine HTTP-Anfrage und damit keinen SecurityContext; die
    // request-scoped AuthorizedUser-Bohne muss dafür in den Job-Modus (→ ADR-0006).
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      etlService.executeAll(range, SCHEDULED);
      log.info("Successfully completed scheduled daily ETL run");
    } catch (BusinessRuleException e) {
      if (e.getMessages().stream().noneMatch(m -> m.getErrorCode() == ETL_RUN_ALREADY_RUNNING)) {
        throw e;
      }
      // Ein von Hand angestoßener Lauf läuft noch. Zwei Läufe vertragen sich nicht — die
      // Definitionen schreiben in dieselben Zieltabellen. Der Ausfall gehört aber dorthin, wo man
      // ihn sucht: in die Liste, nicht nur ins Log.
      log.warn("Scheduled daily ETL run skipped, another run is in progress", e);
      etlService.recordSkippedRun(range, SCHEDULED,
          "Übersprungen: zum Startzeitpunkt lief bereits ein Lauf.");
    } catch (Exception e) {
      log.error("Scheduled daily ETL run failed", e);
      throw new RuntimeException(e);
    } finally {
      // Hier wird nichts von Hand zerstört: resetRequestAttributes() wirft den ganzen Scope
      // samt Bohne weg. Warum destroyScopedBean("authorizedUser") nicht zurückkommen darf,
      // steht an SchedulerRequestAttributes (#1084).
      resetRequestAttributes();
    }
  }

}
