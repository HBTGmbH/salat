package org.tb.restful.auth;

import org.jboss.resteasy.core.Headers;
import org.jboss.resteasy.core.ServerResponse;
import org.tb.util.SecureHashUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.List;

@Provider
public class AuthenticationInterceptor implements ContainerRequestFilter {

    private static final ServerResponse ACCESS_DENIED = new ServerResponse("Access denied for this resource", 401, new Headers<Object>());
    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(final ContainerRequestContext containerRequestContext) throws IOException {
        if(containerRequestContext.getUriInfo().getMatchedURIs().contains("rest/AuthenticationService")) {
            // calls to the AuthenticationService must be allowed to login
            return;
        }
        final MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
        String xsrfTokenHeader = headers.getFirst("x-xsrf-token");
        if (xsrfTokenHeader != null) {
            String xsrfToken = (String) servletRequest.getSession().getAttribute("x-xsrf-token");
            if (xsrfToken != null && xsrfToken.equals(xsrfTokenHeader)) {
                return;
            }
        }
        containerRequestContext.abortWith(ACCESS_DENIED);
    }

}
