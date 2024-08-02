package org.tb.auth;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
import org.springframework.web.server.ResponseStatusException;
import org.tb.common.GlobalConstants;
import org.tb.common.Warning;
import org.tb.dailyreport.service.PublicholidayService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.service.EmployeeorderService;

/**
 * Performs login procedures after the user has been authenticated by spring security (JWT/oauth2/azure).
 * Code copied from {@link LoginEmployeeAction}.
 */
@Component
@RequiredArgsConstructor
public class AutoLoginHandler implements ApplicationListener<AuthenticationSuccessEvent> {

  private final EmployeeDAO employeeDAO;
  private final PublicholidayService publicholidayService;
  private final EmployeecontractDAO employeecontractDAO;
  private final AfterLogin afterLogin;
  private final AuthorizedUser authorizedUser;
  private final HttpServletRequest request;
  private final HttpServletResponse response;
  private final EmployeeorderService employeeorderService;
  private final UserRoleRepository userRoleRepository;

  @SneakyThrows
  @Override
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    HttpSession session = request.getSession(false);
    if(session == null || session.getAttribute("loginEmployee") == null) {
      Authentication authentication = event.getAuthentication();
      onAuthenticationSuccess(request, response, authentication);
    } else {
      // already logged in
    }
  }

  private void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException {

    authorizedUser.setLoginSign(authentication.getName());

    Employee loginEmployee = employeeDAO.getLoginEmployee(authentication.getName());
    // TODO dieser Check sollte im Rahmen der Authentifizierung geschehen - ist hier eigentlich zu spät
    if (loginEmployee == null) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No matching employee found for " + authentication.getName());
    }

    LocalDate today = today();
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), today);

    // TODO dieser Check sollte im Rahmen der Authentifizierung geschehen - ist hier eigentlich zu spät
    if (employeecontract == null && !loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "No valid contract found for " + loginEmployee.getSign());
    }

    var loginEmployeeRoles = userRoleRepository.findAllByUserId(loginEmployee.getSign());
    authorizedUser.init(loginEmployee, loginEmployeeRoles);

    // no further stuff for REST API calls - all is just for struts and old web UI
    if(request.getRequestURL().toString().contains("/api/") || request.getRequestURL().toString().contains("/rest/")) return;

    request.getSession().setAttribute("loginEmployee", loginEmployee);
    request.getSession().setAttribute("loginEmployeeRoles", loginEmployeeRoles);
    String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
    request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
    request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());

    // check if public holidays are available
    publicholidayService.checkPublicHolidaysForCurrentYear();

    // check if employee has an employee contract and it has employee orders for all standard suborders
    if (employeecontract != null) {
      request.getSession().setAttribute("employeeHasValidContract", true);
      handleEmployeeWithValidContract(request, loginEmployee, employeecontract);
    } else {
      request.getSession().setAttribute("employeeHasValidContract", false);
    }

    // create collection of employeecontracts
    List<Employeecontract> employeecontracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser();
    request.getSession().setAttribute("employeecontracts", employeecontracts);
  }

  private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee,
      Employeecontract employeecontract) {
    // auto generate employee orders
    if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM) &&
        Boolean.FALSE.equals(employeecontract.getFreelancer())) {
      employeeorderService.generateMissingStandardOrders(employeecontract.getId());
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

  private Locale getLocale(HttpServletRequest request) {
    return RequestUtils.getUserLocale(request, null);
  }

  private MessageResources getResources(HttpServletRequest request) {
    return ((MessageResources) request.getAttribute(Globals.MESSAGES_KEY));
  }

}
