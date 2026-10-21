package de.hbt.salat.budget.service;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.util.List;
import org.springframework.stereotype.Component;

/** The budget alert (#828); it was mailed before, so mail stays on by default. */
@Component
public class BudgetNotificationKinds implements NotificationKindContributor {

  public static final NotificationKind BUDGET_ALERT = NotificationKind.of("budget-alert", 30, true);

  @Override
  public List<NotificationKind> getNotificationKinds() {
    return List.of(BUDGET_ALERT);
  }
}
