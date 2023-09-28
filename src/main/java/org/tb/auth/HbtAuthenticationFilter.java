package org.tb.auth;

import static org.tb.common.GlobalConstants.DEFAULT_TIMEZONE_ID;
import static org.tb.common.util.DateUtils.formatYear;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.util.MessageResources;
import org.apache.struts.util.RequestUtils;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
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
import org.tb.user.UserAccessTokenService;

@Slf4j
@RequiredArgsConstructor
@Component
public class HbtAuthenticationFilter extends HttpFilter {

  private final AuthorizedUser authorizedUser;
  private final EmployeecontractDAO employeecontractDAO;
  private final EmployeeRepository employeeRepository;
  private final EmployeeorderDAO employeeorderDAO;
  private final PublicholidayDAO publicholidayDAO;
  private final SuborderDAO suborderDAO;
  private final AfterLogin afterLogin;
  private final VacationDAO vacationDAO;
  private final UserAccessTokenService userAccessTokenService;
  public final static List<String> EXCLUDE_PATTERN = List.of(
      "/favicon.ico",
      "/error",
      "/**error**",
      "/error.jsp");
  private final List<AntPathRequestMatcher> excludePattern = EXCLUDE_PATTERN.stream()
      .map(s -> new AntPathRequestMatcher(s, null)).toList();

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws IOException, ServletException {

    AtomicReference<String> userSign = new AtomicReference<>("empty");
    if (shouldNotFilter(request)) {
      log.trace("excluded {}", request.getRequestURI());
      chain.doFilter(request, response);
    } else {
      {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
          if (auth.getPrincipal() instanceof DefaultOidcUser user) {
            userSign.set(user.getAttribute("preferred_username"));
            if (userSign.get() == null) {
              log.error(
                  "sign was null please contact the Administrator to configure the user correctly");
            } else {
              log.debug("userSign: {}", userSign.get());
              findEmployee(List.of(() -> employeeRepository.findBySign(userSign.get()),
                  () -> employeeRepository.findBySign(userSign.get().replace("@hbt.de", "")),
                  () -> getEmployeeByApiKey(request))).ifPresentOrElse(loginEmployee -> {
                authorizedUser.init(loginEmployee);
                processLoginEmployee(loginEmployee, request);
              }, () -> {
                // FIXME generate user from Principal
                log.info("no user found for sign " + userSign.get()
                         + " please contact the Administrator to create your user");

              });
            }
          } else {
            log.error("got Principal of {}, but expected DefaultOAuth2User: {}",
                auth.getPrincipal().getClass(), auth.getPrincipal().toString());
          }
        } else {
          log.error("no user given from Auth-Service");
        }
      }
      if (!authorizedUser.isAuthenticated()) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
            "No user found for sign " + userSign.get()
            + ". Please contact the Administrator to create your user.");
      } else {
        chain.doFilter(request, response);
      }
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

  Optional<Employee> getEmployeeByApiKey(HttpServletRequest request) {

    final var apiKeyValue = request.getHeader("x-api-key");
    if (apiKeyValue != null && !apiKeyValue.isBlank()) {
      // apiKeyValue = username:password
      final var tokenIdAndSecret = apiKeyValue.split(":", 2);
      if (tokenIdAndSecret.length == 2) {
        return userAccessTokenService.authenticate(tokenIdAndSecret[0], tokenIdAndSecret[1]);
      } else {
        log.warn("Could not interpret value of http header x-api-key.");
      }
    }
    return Optional.empty();
  }

  private void processLoginEmployee(Employee loginEmployee, HttpServletRequest request) {

    request.setAttribute("authorizedUser", authorizedUser);

    LocalDate today = today();
    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
        loginEmployee.getId(), today);
    if (employeecontract == null && !loginEmployee.getStatus()
        .equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
      throw new AuthorizationException(ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND);
    }

    request.getSession().setAttribute("loginEmployee", loginEmployee);
    String loginEmployeeFullName = loginEmployee.getFirstname() + " " + loginEmployee.getLastname();
    request.getSession().setAttribute("loginEmployeeFullName", loginEmployeeFullName);
    request.getSession().setAttribute("currentEmployeeId", loginEmployee.getId());
    authorizedUser.init(loginEmployee);

    // check if public holidays are available
    publicholidayDAO.checkPublicHolidaysForCurrentYear();

    // check if employee has an employee contract and is has employee orders for all standard suborders
    if (employeecontract != null) {
      request.getSession().setAttribute("employeeHasValidContract", true);
      handleEmployeeWithValidContract(request, loginEmployee, today, employeecontract);
    } else {
      request.getSession().setAttribute("employeeHasValidContract", false);
    }
  }

  private void handleEmployeeWithValidContract(HttpServletRequest request, Employee loginEmployee,
      LocalDate today, Employeecontract employeecontract) {
    // auto generate employee orders
    if (!loginEmployee.getStatus().equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)
        && Boolean.FALSE.equals(employeecontract.getFreelancer())) {
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
    Employeecontract loginEmployeeContract = (Employeecontract) request.getSession()
        .getAttribute("loginEmployeeContract");
    List<Warning> warnings = afterLogin.createWarnings(employeecontract, loginEmployeeContract,
        getResources(request), getLocale(request));

    if (!warnings.isEmpty()) {
      request.getSession().setAttribute("warnings", warnings);
      request.getSession().setAttribute("warningsPresent", true);
    } else {
      request.getSession().setAttribute("warningsPresent", false);
    }
  }

  private Locale getLocale(HttpServletRequest request) {
    return RequestUtils.getUserLocale(request, (String) null);
  }

  private MessageResources getResources(HttpServletRequest request) {

    return (MessageResources) request.getAttribute("org.apache.struts.action.MESSAGE");
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


  protected boolean shouldNotFilter(HttpServletRequest request) {
    return excludePattern.stream().anyMatch(matcher -> matcher.matches(request));
  }

}
