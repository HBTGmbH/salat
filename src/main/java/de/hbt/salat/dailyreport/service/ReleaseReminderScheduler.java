package de.hbt.salat.dailyreport.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.employee.domain.OverdueRelease;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Reminds whoever has not released a month (#215, #1557): first at 3 pm on its last working day,
 * then at 9 am on the working days that follow. Which day is a working day, and whether it is the
 * last of the month, decides {@link ReleaseReminderService}.
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

  @Scheduled(cron = "${salat.release.reminder.first-cron:0 0 15 * * MON-FRI}")
  public void remindFirst() {
    run(releaseReminderService::getContractsToRemindFirst, releaseReminderService::remindFirst);
  }

  @Scheduled(cron = "${salat.release.reminder.cron:0 0 9 * * MON-FRI}")
  public void remindAgain() {
    run(releaseReminderService::getContractsToRemindAgain, releaseReminderService::remindAgain);
  }

  private void run(Supplier<List<OverdueRelease>> contractsToRemind, Consumer<OverdueRelease> remind) {
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      var contracts = contractsToRemind.get();
      var failed = 0;
      for (var contract : contracts) {
        try {
          remind.accept(contract);
        } catch (RuntimeException e) {
          failed++;
          log.warn("Could not remind contract {} to release", contract.employeecontractId(), e);
        }
      }
      if (!contracts.isEmpty()) {
        log.info("Reminded {} of {} contracts to release", contracts.size() - failed, contracts.size());
      }
    } catch (Exception e) {
      log.error("Release reminder scheduler failed", e);
    } finally {
      // the scope goes with the bean inside it, see SchedulerRequestAttributes (#1084)
      resetRequestAttributes();
    }
  }
}
