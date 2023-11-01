package org.tb.auth;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.tb.common.exception.ErrorCodeException;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;
import org.tb.user.UserAccessTokenService;

@Slf4j
@Component
@Profile({"test"})
public class HbtTestAuthenticationFilter extends HbtAuthenticationFilter {

  private final AuthorizedUser authorizedUser;

  public HbtTestAuthenticationFilter(AuthorizedUser authorizedUser,
      EmployeecontractDAO employeecontractDAO, EmployeeRepository employeeRepository,
      EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, SuborderDAO suborderDAO,
      AfterLogin afterLogin, VacationDAO vacationDAO, UserAccessTokenService userAccessTokenService,
      AuthorizedUser authorizedUser1) {
    super(authorizedUser, employeecontractDAO, employeeRepository, employeeorderDAO,
        publicholidayDAO,
        suborderDAO, afterLogin, vacationDAO, userAccessTokenService);
    this.authorizedUser = authorizedUser1;
  }

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    try {
      AtomicReference<String> userSign = new AtomicReference<>("empty");
      if (shouldNotFilter(request)) {
        log.trace("excluded {}", request.getRequestURI());
        chain.doFilter(request, response);
      } else {
        userSign.set(request.getHeader("user"));
        if (userSign.get() == null) {
          userSign.set("adm");
        }
        if (userSign.get() == null) {
          log.error(
              "sign was null please contact the Administrator to configure the user correctly");
        } else {
          log.debug("userSign: {}", userSign.get());
          getEmployee(request, userSign).ifPresentOrElse(loginEmployee -> {
            authorizedUser.init(loginEmployee);
            processLoginEmployee(loginEmployee, request);
          }, () -> {
            // FIXME generate user from Principal
            log.info("no user found for sign " + userSign.get()
                     + " please contact the Administrator to create your user");

          });
        }
        if (!authorizedUser.isAuthenticated()) {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
              "No user found for sign " + userSign.get()
              + ". Please contact the Administrator to create your user.");
        } else {
          chain.doFilter(request, response);
        }
      }
    } catch (ErrorCodeException e) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getErrorCode().getMessage());
    } catch (Exception e) {
      log.error("error while authentication", e);
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
    }
  }

}
