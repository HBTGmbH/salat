package org.tb.auth;

import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.common.util.DateUtils.formatYear;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.springframework.context.ApplicationListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tb.common.ErrorCode;
import org.tb.common.GlobalConstants;
import org.tb.common.Warning;
import org.tb.common.exception.AuthorizationException;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Component
@RequiredArgsConstructor
@Slf4j
@SessionScope
public class AuthenticationSuccessListener implements
    ApplicationListener<InteractiveAuthenticationSuccessEvent> {

  private final AuthorizedUser authorizedUser;
  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeRepository employeeRepository;
  private final EmployeeorderDAO employeeorderDAO;
  private final PublicholidayDAO publicholidayDAO;
  private final SuborderDAO suborderDAO;
  private final AfterLogin afterLogin;
  private final VacationDAO vacationDAO;

  @Override
  public void onApplicationEvent(InteractiveAuthenticationSuccessEvent event) {
    try {
      if (event.getAuthentication().getPrincipal() instanceof DefaultOidcUser user) {
        String userSign = user.getAttribute("preferred_username");
        log.info("LOGIN name: " + user.getAttributes().get("preferred_username")); //TODO
        log.debug("userSign: {}", userSign);

        if (userSign != null) {
          findEmployee(List.of(() -> employeeRepository.findBySign(userSign),
              () -> employeeRepository.findBySign(
                  userSign.replace("@hbt.de", "")))).ifPresentOrElse(loginEmployee -> {
            authorizedUser.init(loginEmployee);
            processLoginEmployee(loginEmployee, session());
          }, () -> {
            // FIXME generate user from Principal
            throw new AuthenticationCredentialsNotFoundException(
                "no user found for sign " + userSign
                + " please contact the Administrator to create your user");
          });
        } else {
          throw new AuthenticationCredentialsNotFoundException(
              "sign was null please contact the Administrator to configure the user correctly");
        }
      }
    } catch (Exception e) {
      log.info("",e);
    }
  }

  private Optional<Employee> findEmployee(List<Supplier<Optional<Employee>>> functions)
      throws AuthenticationCredentialsNotFoundException {
    for (Supplier<Optional<Employee>> function : functions) {
      Optional<Employee> res = function.get();
      if (res.isPresent()) {
        return res;
      }
    }
    return Optional.empty();
  }

  public static HttpSession session() {
    ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attr.getRequest().getSession(true); // true == allow create
  }

  private void processLoginEmployee(Employee loginEmployee, HttpSession session) {

    session.setAttribute("authorizedUser", authorizedUser);

    LocalDate today = today();
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
        loginEmployee.getId(), today);
    if (employeecontract == null && !loginEmployee.getStatus()
        .equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      throw new AuthorizationException(ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND);
    }

    session.setAttribute("loginEmployee", loginEmployee);
    String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
    session.setAttribute("loginEmployeeFullName", loginEmployeeFullName);
    session.setAttribute("currentEmployeeId", loginEmployee.getId());
    authorizedUser.init(loginEmployee);

    // check if public holidays are available
    publicholidayDAO.checkPublicHolidaysForCurrentYear();

    // check if employee has an employee contract and is has employee orders for all standard suborders
    if (employeecontract != null) {
      session.setAttribute("employeeHasValidContract", true);
      handleEmployeeWithValidContract(session, loginEmployee, today, employeecontract);
    } else {
      session.setAttribute("employeeHasValidContract", false);
    }
  }

  private void handleEmployeeWithValidContract(HttpSession session, Employee loginEmployee,
      LocalDate today, Employeecontract employeecontract) {
    // auto generate employee orders
    if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)
        && Boolean.FALSE.equals(employeecontract.getFreelancer())) {
      generateEmployeeOrders(today, employeecontract);
    }

    // set used employee contract of login employee
    session.setAttribute("loginEmployeeContract", employeecontract);
    session.setAttribute("loginEmployeeContractId", employeecontract.getId());
    session.setAttribute("currentEmployeeContract", employeecontract);

    // get info about vacation, overtime and report status
    session.setAttribute("releaseWarning", employeecontract.getReleaseWarning());
    session.setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());

    String releaseDate = employeecontract.getReportReleaseDateString();
    String acceptanceDate = employeecontract.getReportAcceptanceDateString();

    session.setAttribute("releasedUntil", releaseDate);
    session.setAttribute("acceptedUntil", acceptanceDate);

    afterLogin.handleOvertime(employeecontract, session);

    // get warnings
    Employeecontract loginEmployeeContract = (Employeecontract) session.getAttribute(
        "loginEmployeeContract");
    List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract,
        getResources(), getLocale());

    if (!warnings.isEmpty()) {
      session.setAttribute("warnings", warnings);
      session.setAttribute("warningsPresent", true);
    } else {
      session.setAttribute("warningsPresent", false);
    }
  }

  private Locale getLocale() {
    return LocaleContextHolder.getLocale();
  }

  private MessageResources getResources() {
    return (MessageResources) RequestContextHolder.getRequestAttributes()
        .getAttribute("org.apache.struts.action.MESSAGE", RequestAttributes.SCOPE_SESSION);
  }


  private void generateEmployeeOrders(LocalDate today, Employeecontract employeecontract) {
    List<Suborder> standardSuborders = suborderDAO.getStandardSuborders();
    if (standardSuborders != null && !standardSuborders.isEmpty()) {
      // test if employeeorder exists
      for (Suborder suborder : standardSuborders) {
        List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(
            employeecontract.getId(), suborder.getId(), today);
        if (employeeorders == null || employeeorders.isEmpty()) {

          // do not create an employeeorder for past years "URLAUB" !
          if (suborder.getCustomerorder().getSign()
                  .equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) && !formatYear(
              today).startsWith(suborder.getSign())) {
            continue;
          }

          // find latest untilLocalDate of all employeeorders for this suborder
          List<Employeeorder> invalidEmployeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
              employeecontract.getId(), suborder.getId());
          LocalDate dateUntil = null;
          LocalDate dateFrom = null;
          for (Employeeorder eo : invalidEmployeeorders) {

            // employeeorder starts in the future
            if (eo.getFromDate() != null && eo.getFromDate().isAfter(today) && (dateUntil == null
                                                                                || dateUntil.isAfter(
                eo.getFromDate()))) {

              dateUntil = eo.getFromDate();
              continue;
            }

            // employeeorder ends in the past
            if (eo.getUntilDate() != null && eo.getUntilDate().isBefore(today) && (dateFrom == null
                                                                                   || dateFrom.isBefore(
                eo.getUntilDate()))) {

              dateFrom = eo.getUntilDate();
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
          if (suborder.getCustomerorder().getSign()
                  .equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) && !suborder.getSign()
              .equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            var vacation = employeecontract.getVacation(today().getYear());
            if (vacation.isEmpty()) {
              vacationDAO.addNewVacation(employeecontract, today().getYear(),
                  employeecontract.getVacationEntitlement());
            }
            var vacationBudget = employeecontract.getEffectiveVacationEntitlement(
                today().getYear()); // create vacation employee order for current year
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

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of(DEFAULT_TIMEZONE_ID));
  }


}