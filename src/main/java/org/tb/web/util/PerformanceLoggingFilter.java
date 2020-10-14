package org.tb.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class PerformanceLoggingFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceLoggingFilter.class);

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        long starttime = System.currentTimeMillis();
        chain.doFilter(request, response);
        long duration = System.currentTimeMillis() - starttime;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        LOG.debug(httpServletRequest.getRequestURL() + " took " + duration + "ms.");
    }

    public void init(FilterConfig config) throws ServletException {
        /* nothing to init */
    }

    public void destroy() {
        /* nothing to destroy */
    }

}
