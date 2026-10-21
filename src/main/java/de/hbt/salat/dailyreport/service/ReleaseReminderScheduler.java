package de.hbt.salat.dailyreport.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reminds whoever has not released the previous month, at noon on weekdays (#215). Whether the day
 * is a working day — no public holiday — decides {@link ReleaseReminderService}.
 *
 * <p>Each contract is reminded in a transaction of its own, through the proxy: a reminder that
 * fails — a mail server that is down, a person without an address — is logged and leaves the others
 * alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseReminderScheduler {

  private final ReleaseReminderService releaseReminderService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Scheduled(cron = "${salat.release.reminder.cron:0 0 12 * * MON-FRI}")
  public void run() {
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      var contracts = releaseReminderService.getContractsToRemindToday();
      var failed = 0;
      for (var contract : contracts) {
        try {
          releaseReminderService.remind(contract);
        } catch (RuntimeException e) {
          failed++;
          log.warn("Could not remind contract {} to release", contract.employeecontractId(), e);
        }
      }
      if (!contracts.isEmpty()) {
        log.info("Reminded {} of {} contracts to release the previous month", contracts.size() - failed, contracts.size());
      }
    } catch (Exception e) {
      log.error("Release reminder scheduler failed", e);
    } finally {
      // the scope goes with the bean inside it, see SchedulerRequestAttributes (#1084)
      resetRequestAttributes();
    }
  }
}
