package de.hbt.salat.achievement.service;

import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;

import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.common.util.ClockProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Records the achievements reached by the day, once a night (#1501). An anniversary is a matter of
 * the date, so the night is soon enough: the popup comes with the first page of the day.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementScheduler {

  private final AchievementService achievementService;
  private final ObjectProvider<AuthorizedUser> authorizedUserProvider;

  @Scheduled(cron = "${salat.achievement.cron:0 30 4 * * *}")
  public void run() {
    setRequestAttributes(new SchedulerRequestAttributes(), true);
    try {
      authorizedUserProvider.getObject().initForJob();
      achievementService.recordReached(ClockProvider.today());
    } catch (Exception e) {
      log.error("Achievement scheduler failed", e);
    } finally {
      // the scope goes with the bean inside it, see SchedulerRequestAttributes (#1084)
      resetRequestAttributes();
    }
  }
}
