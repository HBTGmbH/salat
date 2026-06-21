package org.tb.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(99)
public class PerformanceLoggingFilter extends HttpFilter {

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        long startTime = System.currentTimeMillis();
        super.doFilter(request, response, chain);
        long duration = System.currentTimeMillis() - startTime;
        log.debug(request.getRequestURI() + " took " + duration + "ms.");
    }

}
