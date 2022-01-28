package org.tb.restful.auth;

import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ServerResponse;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class AuthenticationInterceptor implements ContainerRequestFilter {

    private static final ServerResponse ACCESS_DENIED = new ServerResponse("\"Access denied for this resource\"", 401,
            new Headers<Object>());
    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(final ContainerRequestContext containerRequestContext) throws IOException {
        if (containerRequestContext.getUriInfo().getRequestUri().getPath().startsWith("/tb/rest/AuthenticationService/authenticate"))
            return;
        if (servletRequest.getSession().getAttribute("employeeId") == null) {
            containerRequestContext.abortWith(ACCESS_DENIED);
        }
    }

}
