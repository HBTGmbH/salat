package org.tb.dailyreport.viewhelper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.action.AddDailyReportForm;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@Component
@RequiredArgsConstructor
public class TimereportHelper {

  private final EmployeeorderService employeeorderService;
  private final AuthorizedUser authorizedUser;
  private final EmployeecontractService employeecontractService;

  /**
   * refreshes hours after change of begin/end times
   */
  public void refreshHours(AddDailyReportForm reportForm) {
    reportForm.recalcDurationFromBeginAndEnd();
  }

  /**
   * refreshes period after change of hours
   */
  public void refreshPeriod(AddDailyReportForm reportForm) {
    reportForm.recalcEndFromBeginAndDuration();
  }

  public ActionMessages validateNewDate(ActionMessages errors, LocalDate theNewDate, TimereportDTO timereport,
      Employeecontract loginEmployeeContract) {

    // check date range (must be in current or previous year)
    if (DateUtils.getCurrentYear() - DateUtils.getYear(theNewDate).getValue() >= 2) {
      errors.add("referenceday", new ActionMessage("form.timereport.error.date.invalidyear"));
    }

    // check date vs release status
    Employeecontract employeecontract = employeecontractService.getEmployeecontractById(
        timereport.getEmployeecontractId());
    LocalDate releaseDate = employeecontract.getReportReleaseDate();
    LocalDate acceptanceDate = employeecontract.getReportAcceptanceDate();

    if (!authorizedUser.isAdmin()) {
      if (authorizedUser.isManager() && !Objects.equals(loginEmployeeContract.getId(),
          timereport.getEmployeecontractId())) {
        if (releaseDate != null && releaseDate.isBefore(theNewDate)) {
          errors.add("release", new ActionMessage("form.timereport.error.not.released"));
        }
      } else {
        if (releaseDate != null && !releaseDate.isBefore(theNewDate)) {
          errors.add("release", new ActionMessage("form.timereport.error.released"));
        }
      }
      if (acceptanceDate != null && !theNewDate.isAfter(acceptanceDate)) {
        errors.add("release", new ActionMessage("form.timereport.error.accepted"));
      }
    }

    // check for adequate employee order
    List<Employeeorder> employeeorders = employeeorderService.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndValidAt(
        timereport.getEmployeecontractId(), timereport.getSuborderId(), theNewDate);
    if (employeeorders == null || employeeorders.isEmpty()) {
      errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.notfound"));
    } else if (employeeorders.size() > 1) {
      errors.add("employeeorder", new ActionMessage("form.timereport.error.employeeorder.multiplefound"));
    }

    return errors;
  }

  public String calculateLaborTime(List<TimereportDTO> timereports) {
    Duration total = timereports.stream().map(TimereportDTO::getDuration).reduce(Duration.ZERO, Duration::plus);
    return "%02d:%02d".formatted(total.toHours(), total.toMinutesPart());
  }


}
