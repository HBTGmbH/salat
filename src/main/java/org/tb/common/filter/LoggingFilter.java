package org.tb.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.tb.auth.AuthorizedUser;

@Slf4j
@RequiredArgsConstructor
public class LoggingFilter extends HttpFilter {

  private final AuthorizedUser authorizedUser;

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if(authorizedUser.isAuthenticated()) {
      MDC.put("login-sign", authorizedUser.getLoginSign());
    }
    MDC.put("request-uri", request.getRequestURI());
    super.doFilter(request, response, chain);
    MDC.remove("login-sign");
    MDC.remove("request-uri");
  }
}
