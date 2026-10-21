package de.hbt.salat.employee.service;

import de.hbt.salat.common.notification.NotificationKind;
import de.hbt.salat.common.notification.NotificationKindContributor;
import java.util.List;
import org.springframework.stereotype.Component;

/** The notification about a new employee contract (#828); it was never mailed before, so mail starts off. */
@Component
public class EmployeeNotificationKinds implements NotificationKindContributor {

  public static final NotificationKind EMPLOYEECONTRACT_CREATED = NotificationKind.of("employeecontract-created", 20, false);

  @Override
  public List<NotificationKind> getNotificationKinds() {
    return List.of(EMPLOYEECONTRACT_CREATED);
  }
}
