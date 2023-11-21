package org.tb.auth;

import java.io.IOException;
import java.util.Arrays;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.persistence.SuborderDAO;

@Component
//@SuperBuilder
//@RequiredArgsConstructor
@Profile({"e2etest"})
@Slf4j
//@SessionScope
public class AuthenticationSuccessTestListener extends AuthenticationSuccessListener implements
    Filter {

  public AuthenticationSuccessTestListener(AuthorizedUser authorizedUser,
      EmployeecontractDAO employeecontractDAO, EmployeeRepository employeeRepository,
      EmployeeorderDAO employeeorderDAO, PublicholidayDAO publicholidayDAO, SuborderDAO suborderDAO,
      AfterLogin afterLogin, VacationDAO vacationDAO) {
    super(authorizedUser, employeecontractDAO, employeeRepository, employeeorderDAO,
        publicholidayDAO,
        suborderDAO, afterLogin, vacationDAO);
  }

  @Override
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    try {
      if (event.getAuthentication().getPrincipal() instanceof DefaultOidcUser user) {
        String userSign = user.getAttribute("preferred_username");
        log.info("LOGIN name: " + user.getAttributes().get("preferred_username")); //TODO
        userSign = getuser(((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
            .getRequest());
        if (userSign == null) {
          userSign = "tt";
        }
        Employee loginEmployee = findEmployee(userSign);
        //todoauthorizedUser.init(loginEmployee);
        processLoginEmployee(loginEmployee, session());
      }
    } catch (Exception e) {
      log.info("", e);
    }
  }


  private String getuser(HttpServletRequest request) {
    if (request.getHeader("user") != null) {
      return request.getHeader("user");
    } else if (request.getCookies() != null) {
      return Arrays.stream(request.getCookies())
          .filter(cookie -> cookie.getName().equals("user"))
          .map(Cookie::getValue)
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
      FilterChain filterChain) throws IOException, ServletException {
    try {
      if (servletRequest instanceof HttpServletRequest httpServletRequest) {
        String userSign = getuser(httpServletRequest);
        if (userSign == null) {
          userSign = "tt";
        }
        Employee loginEmployee = findEmployee(userSign);
        //todoauthorizedUser.init(loginEmployee);
        processLoginEmployee(loginEmployee, session());
      } else {
        log.info("httpServletRequest has wrong class: {}", servletRequest.getClass());
      }
    } catch (Exception e) {
      log.info("", e);
    }
    filterChain.doFilter(servletRequest, servletResponse);
  }
}
