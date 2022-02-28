package org.tb.common.configuration;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
