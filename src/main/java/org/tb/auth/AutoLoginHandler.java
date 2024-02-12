package org.tb.auth;

import static org.tb.common.util.DateUtils.formatYear;
import static org.tb.common.util.DateUtils.today;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.struts.Globals;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.springframework.context.ApplicationListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.Warning;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

/**
 * Performs login procedures after the user has been authenticated by spring security (JWT/oauth2/azure).
 * Code copied from {@link LoginEmployeeAction}.
 */
@Component
@RequiredArgsConstructor
public class AutoLoginHandler implements ApplicationListener<AuthenticationSuccessEvent> {

  private final EmployeeDAO employeeDAO;
  private final PublicholidayDAO publicholidayDAO;
  private final EmployeecontractDAO employeecontractDAO;
  private final SuborderDAO suborderDAO;
  private final EmployeeorderDAO employeeorderDAO;
  private final AfterLogin afterLogin;
  private final VacationDAO vacationDAO;
  private final AuthorizedUser authorizedUser;
  private final HttpServletRequest request;
  private final HttpServletResponse response;

  @SneakyThrows
  @Override
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    // TODO only required if request is a user request - not REST for example ...
    if(request.getSession().getAttribute("loginEmployee") == null) {
      Authentication authentication = event.getAuthentication();
      onAuthenticationSuccess(request, response, authentication);
    } else {
      // already logged in
    }
  }

  private void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {
    // check if user is internal or extern
    setEmployeeIsInternalAttribute(request);

    Employee loginEmployee = employeeDAO.getLoginEmployee(authentication.getName());

    LocalDate today = today();
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), today);
    if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "No valid contract found for " + loginEmployee.getSign());
      return;
    }

    request.getSession().setAttribute("loginEmployee", loginEmployee);
    String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
    request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
    request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
    authorizedUser.init(loginEmployee);

    // check if public holidays are available
    publicholidayDAO.checkPublicHolidaysForCurrentYear();

    // check if employee has an employee contract and it has employee orders for all standard suborders
    if (employeecontract != null) {
      request.getSession().setAttribute("employeeHasValidContract", true);
      handleEmployeeWithValidContract(request, loginEmployee, today, employeecontract);
    } else {
      request.getSession().setAttribute("employeeHasValidContract", false);
    }

    // create collection of employeecontracts
    List<Employeecontract> employeecontracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser();
    request.getSession().setAttribute("employeecontracts", employeecontracts);
  }

  private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee, LocalDate today,
      Employeecontract employeecontract) {
    // auto generate employee orders
    if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
        Boolean.FALSE.equals(employeecontract.getFreelancer())) {
      generateEmployeeOrders(today, employeecontract);
    }

    // set used employee contract of login employee
    request.getSession().setAttribute("loginEmployeeContract", employeecontract);
    request.getSession().setAttribute("loginEmployeeContractId", employeecontract.getId());
    request.getSession().setAttribute("currentEmployeeContract", employeecontract);

    // get info about vacation, overtime and report status
    request.getSession().setAttribute("releaseWarning", employeecontract.getReleaseWarning());
    request.getSession().setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());

    String releaseDate = employeecontract.getReportReleaseDateString();
    String acceptanceDate = employeecontract.getReportAcceptanceDateString();

    request.getSession().setAttribute("releasedUntil", releaseDate);
    request.getSession().setAttribute("acceptedUntil", acceptanceDate);

    afterLogin.handleOvertime(employeecontract, request.getSession());

    // get warnings
    Employeecontract loginEmployeeContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
    List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract, getResources(
        request), getLocale(request));

    if (!warnings.isEmpty()) {
      request.getSession().setAttribute("warnings", warnings);
      request.getSession().setAttribute("warningsPresent", true);
    } else {
      request.getSession().setAttribute("warningsPresent", false);
    }
  }

  private void setEmployeeIsInternalAttribute(HttpServletRequest request) {
    String clientIP = request.getRemoteHost();
    boolean isInternal = clientIP.startsWith("10.") ||
                         clientIP.startsWith("192.168.") ||
                         clientIP.startsWith("172.16.") ||
                         clientIP.startsWith("127.0.0.");
    request.getSession().setAttribute("clientIntern", isInternal);
  }

  private void generateEmployeeOrders(LocalDate today, Employeecontract employeecontract) {
    List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
    if (standardSuborders != null && !standardSuborders.isEmpty()) {
      // test if employeeorder exists
      for (Suborder suborder : standardSuborders) {
        List<Employeeorder> employeeorders = employeeorderDAO
            .getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(
                employeecontract.getId(), suborder
                    .getId(), today);
        if (employeeorders == null || employeeorders.isEmpty()) {

          // do not create an employeeorder for past years "URLAUB" !
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
              && !formatYear(today).startsWith(suborder.getSign())) {
            continue;
          }

          // find latest untilLocalDate of all employeeorders for this suborder
          List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
              employeecontract.getId(), suborder.getId());
          LocalDate dateUntil = null;
          LocalDate dateFrom = null;
          for (Employeeorder eo : invalidEmployeeorders) {

            // employeeorder starts in the future
            if (eo.getFromDate() != null && eo.getFromDate().isAfter(today)
                && (dateUntil == null || dateUntil.isAfter(eo.getFromDate()))) {

              dateUntil = eo.getFromDate();
              continue;
            }

            // employeeorder ends in the past
            if (eo.getEffectiveUntilDate() != null && eo.getEffectiveUntilDate().isBefore(today)
                && (dateFrom == null || dateFrom.isBefore(eo.getEffectiveUntilDate()))) {

              dateFrom = eo.getEffectiveUntilDate();
            }
          }

          // calculate time period
          LocalDate ecFromDate = employeecontract.getValidFrom();
          LocalDate ecUntilDate = employeecontract.getValidUntil();
          LocalDate soFromDate = suborder.getFromDate();
          LocalDate soUntilDate = suborder.getUntilDate();
          LocalDate fromDate = ecFromDate.isBefore(soFromDate) ? soFromDate : ecFromDate;

          // fromLocalDate should not be before the ending of the most recent contract
          if (dateFrom != null && dateFrom.isAfter(fromDate)) {
            fromDate = dateFrom;
          }
          LocalDate untilDate = null;

          if (ecUntilDate == null && soUntilDate == null) {
            //untildate remains null
          } else if (ecUntilDate == null) {
            untilDate = soUntilDate;
          } else if (soUntilDate == null) {
            untilDate = ecUntilDate;
          } else if (ecUntilDate.isBefore(soUntilDate)) {
            untilDate = ecUntilDate;
          } else {
            untilDate = soUntilDate;
          }

          Employeeorder employeeorder = new Employeeorder();
          employeeorder.setFromDate(fromDate);

          // untilDate should not overreach a future employee contract
          if (untilDate == null) {
            untilDate = dateUntil;
          } else {
            if (dateUntil != null && dateUntil.isBefore(untilDate)) {
              untilDate = dateUntil;
            }
          }

          if (untilDate != null) {
            employeeorder.setUntilDate(untilDate);
          }
          if (suborder.getCustomerorder().getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
              && !suborder.getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            var vacation = employeecontract.getVacation(today().getYear());
            if(vacation.isEmpty()) {
              vacationDAO.addNewVacation(employeecontract, today().getYear(), employeecontract.getVacationEntitlement());
            }
            var vacationBudget = employeecontract.getEffectiveVacationEntitlement(today().getYear()); // create vacation employee order for current year
            employeeorder.setDebithours(vacationBudget);
            employeeorder.setDebithoursunit(GlobalConstants.DEBITHOURS_UNIT_TOTALTIME);
          } else {
            // not decided yet
          }
          employeeorder.setEmployeecontract(employeecontract);
          employeeorder.setSign(" ");
          employeeorder.setSuborder(suborder);

          if (untilDate == null || !fromDate.isAfter(untilDate)) {
            employeeorderDAO.save(employeeorder);
          }

        }
      }
    }
  }

  private Locale getLocale(HttpServletRequest request) {
    return RequestUtils.getUserLocale(request, null);
  }

  private MessageResources getResources(HttpServletRequest request) {
    return ((MessageResources) request.getAttribute(Globals.MESSAGES_KEY));
  }

}
