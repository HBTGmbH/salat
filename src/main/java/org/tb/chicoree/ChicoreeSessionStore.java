package org.tb.chicoree;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.GERMAN;
import static org.tb.common.util.DateUtils.format;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.common.OptionItem;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.OvertimeStatus;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.comparator.CustomerOrderComparator;
import org.tb.order.domain.comparator.SubOrderComparator;

@Component
@RequiredArgsConstructor
public class ChicoreeSessionStore {

  private final HttpSession httpSession;

  public void setGreeting(String greeting) {
    httpSession.setAttribute("greeting", greeting);
  }

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
    httpSession.setAttribute("dashboardDateString", format(date));
    httpSession.setAttribute("dashboardDateMonth", date.format(ofPattern("LLL").withLocale(GERMAN)));
    httpSession.setAttribute("dashboardDateDay", date.format(ofPattern("d").withLocale(GERMAN)));
    httpSession.setAttribute("dashboardDateWeekday", date.format(ofPattern("EEEE").withLocale(GERMAN)));
  }

  public Optional<LocalDate> getDashboardDate() {
    return Optional.ofNullable((LocalDate) httpSession.getAttribute("dashboardDate"));
  }

  public void setTimereports(LocalDate date, List<TimereportDTO> timereports) {
    setDashboardDate(date);
    var durationSum = timereports
        .stream()
        .map(TimereportDTO::getDuration)
        .reduce(Duration.ZERO, Duration::plus);
    httpSession.setAttribute("timereports", timereports);
    httpSession.setAttribute("timereportsExist", !timereports.isEmpty());
    httpSession.setAttribute("timereportsDuration", DurationUtils.format(durationSum));
    var dashboardTimereports = timereports
        .stream()
        .sorted(Comparator.comparing(TimereportDTO::getSequencenumber).reversed())
        .map(DashboardTimereport::valueOf)
        .collect(Collectors.toList());
    httpSession.setAttribute("dashboardTimereports", dashboardTimereports);
  }

  public void setOvertimeStatus(Optional<OvertimeStatus> overtimeStatus) {
    httpSession.setAttribute("overtimeStatusAvailable", overtimeStatus.isPresent());
    httpSession.setAttribute("overtimeStatus", overtimeStatus.orElse(null));
  }

  public void setEmployeeorders(List<Employeeorder> orders) {
    var orderOptions = orders.stream()
        .map(Employeeorder::getSuborder)
        .map(Suborder::getCustomerorder)
        .distinct()
        .sorted(CustomerOrderComparator.INSTANCE)
        .map(o -> new OptionItem(o.getId().toString(), o.getSignAndDescription()))
        .collect(Collectors.toList());
    httpSession.setAttribute("orderOptions", orderOptions);
    httpSession.setAttribute("suborderOptions", Collections.emptyList());
  }

  public List<OptionItem> getOrderOptions() {
    return (List<OptionItem>) httpSession.getAttribute("orderOptions");
  }

  public List<OptionItem> getSuborderOptions() {
    return (List<OptionItem>) httpSession.getAttribute("suborderOptions");
  }

  public void setCustomerorder(long customerorderId, List<Employeeorder> orders) {
    var suborderOptions = orders.stream()
        .map(Employeeorder::getSuborder)
        .filter(suborder -> suborder.getCustomerorder().getId().equals(customerorderId))
        .sorted(SubOrderComparator.INSTANCE)
        .map(s -> new OptionItem(s.getId().toString(), s.getSignAndDescription()))
        .collect(Collectors.toList());
    httpSession.setAttribute("suborderOptions", suborderOptions);
  }

  public void invalidate() {
    httpSession.removeAttribute("greeting");
    httpSession.removeAttribute("loginEmployee");
    httpSession.removeAttribute("loginEmployeeFirstname");
    httpSession.removeAttribute("loginEmployeecontractId");
    httpSession.removeAttribute("dashboardDate");
    httpSession.removeAttribute("dashboardDateMonth");
    httpSession.removeAttribute("dashboardDateDay");
    httpSession.removeAttribute("dashboardDateWeekday");
    httpSession.removeAttribute("timereports");
    httpSession.removeAttribute("timereportsExist");
    httpSession.removeAttribute("timereportsDuration");
    httpSession.removeAttribute("dashboardTimereports");
    httpSession.removeAttribute("overtimeStatusAvailable");
    httpSession.removeAttribute("overtimeStatus");
    httpSession.removeAttribute("orderOptions");
    httpSession.removeAttribute("suborderOptions");
  }

}
