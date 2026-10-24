package de.hbt.salat.reporting.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.reporting.domain.ReportGroup;
import de.hbt.salat.reporting.domain.ReportGroupInfo;

/**
 * The names report groups are shown with (#1523). A personal group carries no name of its own: it is
 * shown with the name of its person, read from the employee module for all groups at once — a copy in
 * the group would go stale with the next change of the name. That is the one thing {@code reporting}
 * reads from {@code employee}, a value per login (ADR-0021); {@code employee} does not import
 * {@code reporting}, so the edge closes no cycle. A login without an employee is shown by its login
 * name.
 *
 * <p>No authorization of its own: the callers decide which groups to name — the groups of the reports
 * a person may run, or every group for a manager.
 */
@Component
@RequiredArgsConstructor
public class ReportGroupNames {

  private final EmployeeService employeeService;

  public Map<Long, ReportGroupInfo> of(Collection<ReportGroup> groups) {
    var ownerIds = groups.stream()
        .map(ReportGroup::getOwnerUserId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    var names = ownerIds.isEmpty() ? Map.<Long, String>of() : employeeService.getNamesBySalatUserIds(ownerIds);
    var infos = new HashMap<Long, ReportGroupInfo>();
    groups.forEach(group -> infos.put(group.getId(), group.isPersonal()
        ? new ReportGroupInfo(group.getId(), nameOf(group, names), true)
        : new ReportGroupInfo(group.getId(), group.getName(), false)));
    return infos;
  }

  /** The person's name; the login name only where there is no person, which loads the login. */
  private static String nameOf(ReportGroup group, Map<Long, String> names) {
    var name = names.get(group.getOwnerUserId());
    return name != null ? name : group.getOwner().getLoginname();
  }

  /** The name a personal group of this login is shown with, for one that is not created yet. */
  public String nameOfOwner(Long salatUserId) {
    return employeeService.getNamesBySalatUserIds(Set.of(salatUserId)).get(salatUserId);
  }

  public ReportGroupInfo of(ReportGroup group) {
    return group == null ? ReportGroupInfo.NONE : of(List.of(group)).get(group.getId());
  }
}
