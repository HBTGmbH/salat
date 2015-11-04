package org.tb.web.util;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
