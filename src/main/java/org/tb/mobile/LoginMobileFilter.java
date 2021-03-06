package org.tb.mobile;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Login filter for addDailyReportMobile.
 */
public class LoginMobileFilter implements Filter {

    public void destroy() {
    }

    /**
     * Checks on request to addDailyReportMobile page whether the user is
     * already logged in and redirects to loginMobile page if not.
     *
     * @param req   the servlet request
     * @param res   the servlet response
     * @param chain the filter chain generated from config
     */
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        if (employeeId == null) {
            response.sendRedirect("login.html");
            return;
        }
        chain.doFilter(request, response);
    }

    public void init(FilterConfig arg0) throws ServletException {
    }

}
