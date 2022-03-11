package org.tb.chicoree;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.GERMAN;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;

@Component
@RequiredArgsConstructor
public class ChicoreeSessionStore {

  private final HttpSession httpSession;

  public void setLoginEmployee(Employee employee) {
    httpSession.setAttribute("loginEmployee", employee);
    httpSession.setAttribute("loginEmployeeFirstname", employee.getFirstname());
  }

  public void setLoginEmployeecontractId(long id) {
    httpSession.setAttribute("loginEmployeecontractId", id);
  }

  public Optional<Long> getLoginEmployeecontractId() {
    return Optional.ofNullable((Long) httpSession.getAttribute("loginEmployeecontractId"));
  }

  public void setDashboardDate(LocalDate date) {
    httpSession.setAttribute("dashboardDate", date);
    httpSession.setAttribute("dashboardDateMonth", date.format(ofPattern("LLL").withLocale(GERMAN)));
    httpSession.setAttribute("dashboardDateDay", date.format(ofPattern("d").withLocale(GERMAN)));
    httpSession.setAttribute("dashboardDateWeekday", date.format(ofPattern("EEEE").withLocale(GERMAN)));
  }

  public Optional<LocalDate> getDashboardDate() {
    return Optional.ofNullable((LocalDate) httpSession.getAttribute("dashboardDate"));
  }

  public void setTimereports(List<Timereport> timereports) {
    var durationSum = timereports
        .stream()
        .map(Timereport::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    httpSession.setAttribute("timereports", timereports);
    httpSession.setAttribute("timereportsExist", !timereports.isEmpty());
    httpSession.setAttribute("timereportsDuration", DurationUtils.format(durationSum));
    var dashboardTimereports = timereports
        .stream()
        .map(DashboardTimereport::valueOf)
        .collect(Collectors.toList());
    httpSession.setAttribute("dashboardTimereports", dashboardTimereports);
  }

  public void invalidate() {
    httpSession.removeAttribute("loginEmployee");
    httpSession.removeAttribute("loginEmployeeFirstname");
    httpSession.removeAttribute("loginEmployeecontractId");
    httpSession.removeAttribute("dashboardDate");
    httpSession.removeAttribute("dashboardDateMonth");
    httpSession.removeAttribute("dashboardDateDay");
    httpSession.removeAttribute("dashboardDateWeekday");
    httpSession.removeAttribute("timereports");
    httpSession.removeAttribute("timereportsExist");
  }

}
