package org.tb.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Schreibt die Auth-Komponenten in den Request-Scope, damit diese in JSP
 * files genutzt werden können.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthFilter extends HttpFilter {

  private final AuthorizedUser authorizedUser;
  private final Set<AuthViewHelper> authViewHelpers;

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    request.setAttribute("authorizedUser", authorizedUser);
    authViewHelpers.forEach(avh -> request.setAttribute(avh.getName(), avh));
    try {
      super.doFilter(request, response, chain);
    } finally {
      authViewHelpers.forEach(avh -> request.removeAttribute(avh.getName()));
      request.removeAttribute("authorizedUser");
    }
  }

}
