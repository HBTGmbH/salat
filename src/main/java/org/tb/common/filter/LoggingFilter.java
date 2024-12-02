package org.tb.common.filter;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

@Slf4j
@RequiredArgsConstructor
public class LoggingFilter extends HttpFilter {

  private final Set<MdcDataSource> mdcDataSources;

  @Override
  protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    MDC.put("request-uri", request.getRequestURI());
    MDC.put("request-method", request.getMethod());
    MDC.put("request-query-string", ofNullable(request.getQueryString()).orElse("<empty>"));
    var data = mdcDataSources.stream()
        .flatMap(ds -> ds.getData().entrySet().stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    data.forEach(MDC::put);

    super.doFilter(request, response, chain);

    data.keySet().forEach(MDC::remove);
    MDC.remove("request-query-string");
    MDC.remove("request-method");
    MDC.remove("request-uri");
  }

  public interface MdcDataSource {
    Map<String, String> getData();
  }

}
