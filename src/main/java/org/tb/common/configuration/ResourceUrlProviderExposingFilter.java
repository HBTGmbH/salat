package org.tb.common.configuration;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

@Slf4j
@RequiredArgsConstructor
public class ResourceUrlProviderExposingFilter extends HttpFilter {

  public static final String RESOURCE_URL_PROVIDER_ATTR = ResourceUrlProvider.class.getName();
  private final ResourceUrlProvider resourceUrlProvider;

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      request.setAttribute(RESOURCE_URL_PROVIDER_ATTR, this.resourceUrlProvider);
    }
    catch (IllegalArgumentException ex) {
      throw new ServletRequestBindingException(ex.getMessage(), ex);
    }
    super.doFilter(request, response, chain);
  }

}
