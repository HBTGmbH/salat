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

//    @Override
//    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
//            throws Failure, WebApplicationException {
//        List<String> xsrfHeader = request.getHttpHeaders().getRequestHeader("X-XSRF-TOKEN");
//        if (xsrfHeader != null && xsrfHeader.size() == 1) {
//            String xsrfToken = xsrfHeader.get(0);
//            Long employeeId = (Long) servletRequest.getSession().getAttribute("employeeId");
//            String salt = (String) servletRequest.getSession().getAttribute("jaxrs.salt");
//            String compareToken = SecureHashUtils.makeMD5(employeeId + "." + salt);
//            if (xsrfToken.equals(compareToken)) {
//                return null;
//            }
//        }
//        return new ServerResponse("Access denied for this resource", 401, new Headers<Object>());
//    }
//
//    @Override
//    @SuppressWarnings("rawtypes")
//    public boolean accept(Class declaring, Method method) {
//        return declaring == SubordersService.class;
//    }

    @Override
    public void filter(final ContainerRequestContext containerRequestContext) throws IOException {
        final MultivaluedMap<String, String> headers = containerRequestContext.getHeaders();
        List<String> xsrfHeader = headers.get("X-XSRF-TOKEN");
        if (xsrfHeader != null && xsrfHeader.size() == 1) {
            String xsrfToken = xsrfHeader.get(0);
            Long employeeId = (Long) servletRequest.getSession().getAttribute("employeeId");
            String salt = (String) servletRequest.getSession().getAttribute("jaxrs.salt");
            String compareToken = SecureHashUtils.makeMD5(employeeId + "." + salt);
            if (xsrfToken.equals(compareToken)) {
                return;
            }
        }
        containerRequestContext.abortWith(ACCESS_DENIED);
    }
}
