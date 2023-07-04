//package org.tb.auth;
//
//import java.io.IOException;
//import java.util.Optional;
//import javax.servlet.FilterChain;
//import javax.servlet.ServletException;
//import javax.servlet.ServletRequest;
//import javax.servlet.ServletResponse;
//import javax.servlet.http.HttpServletRequest;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.core.context.SecurityContextHolder;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.stereotype.Component;
//import org.springframework.web.filter.GenericFilterBean;
//import org.tb.employee.domain.Employee;
//import org.tb.employee.persistence.EmployeeDAO;
//import org.tb.user.UserAccessTokenService;
//
//@Slf4j
//@RequiredArgsConstructor
//@Component
//public class EmployeeFilter extends GenericFilterBean {
//
//
//  private final AuthorizedUser authorizedUser;
//  private final EmployeeDAO employeeDAO;
//  private final UserAccessTokenService userAccessTokenService;
//
//  @Override
//  public void doFilter(ServletRequest request, ServletResponse response,
//      FilterChain filterChain) throws IOException, ServletException {
//    Object auth = SecurityContextHolder.getContext().getAuthentication();
//    if (auth != null) {
//      Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//      final String username;
//      if (principal instanceof UserDetails) {
//        username = ((UserDetails) principal).getUsername();
//      } else {
//        username = principal.toString();
//      }
//
//      Employee loginEmployee = (Employee) request.getAttribute("loginEmployee");
//      if (loginEmployee != null && loginEmployee.getId() != null) {
//        Employee employee = employeeDAO.getEmployeeBySign(username);
//        if (employee != null) {
//          authorizedUser.init(employee);
//        }
//      } else {
//        getEmployeeByApiKey((HttpServletRequest) request).ifPresent(authorizedUser::init);
//      }
//      Object oldValue = request.getAttribute("authorizedUser");
//      request.setAttribute("authorizedUser", authorizedUser);
//      filterChain.doFilter(request, response);
//      request.setAttribute("authorizedUser", oldValue);
//    } else {
//      filterChain.doFilter(request, response);
//    }
//
//  }
//
//  Optional<Employee> getEmployeeByApiKey(HttpServletRequest request) {
//
//    final var apiKeyValue = request.getHeader("x-api-key");
//    if (apiKeyValue != null && !apiKeyValue.isBlank()) {
//      // apiKeyValue = username:password
//      final var tokenIdAndSecret = apiKeyValue.split(":", 2);
//      if (tokenIdAndSecret.length == 2) {
//        return userAccessTokenService.authenticate(tokenIdAndSecret[0], tokenIdAndSecret[1]);
//      } else {
//        log.warn("Could not interpret value of http header x-api-key.");
//      }
//    }
//    return Optional.empty();
//  }
//
//
//}
