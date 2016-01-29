package org.tb.restful;

import java.lang.reflect.Method;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.tb.util.MD5Util;

@Provider
@ServerInterceptor
public class AuthenticationInterceptor implements PreProcessInterceptor, AcceptedByMethod {
	
	@Context
	private HttpServletRequest servletRequest;

	@Override
	public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
			throws Failure, WebApplicationException {
		List<String> xsrfHeader = request.getHttpHeaders().getRequestHeader("X-XSRF-TOKEN");
		if(xsrfHeader != null && xsrfHeader.size() == 1) {
			String xsrfToken = xsrfHeader.get(0);
			Long employeeId = (Long) servletRequest.getSession().getAttribute("employeeId");
			String salt = (String) servletRequest.getSession().getAttribute("jaxrs.salt");
			String compareToken = MD5Util.makeMD5(employeeId + "." + salt);
			if(xsrfToken.equals(compareToken)) {
				return null;
			}
		}
		return new ServerResponse("Access denied for this resource", 401, new Headers<Object>());
	}

	@Override
	@SuppressWarnings("rawtypes")
	public boolean accept(Class declaring, Method method) {
		return declaring == SubordersService.class;
	}

}
