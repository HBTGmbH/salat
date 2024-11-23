package org.tb.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/**
 * Schreibt die Auth-Komponenten in den Request-Scope, damit diese in JSP
 * files genutzt werden können.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthFilter extends HttpFilter {

  private final AuthViewHelper authViewHelper;
  private final AuthorizedUser authorizedUser;

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    request.setAttribute("authorizedUser", authorizedUser);
    request.setAttribute("authViewHelper", authViewHelper);
    try {
      super.doFilter(request, response, chain);
    } finally {
      request.removeAttribute("authorizedUser");
      request.removeAttribute("authViewHelper");
    }
  }

}
