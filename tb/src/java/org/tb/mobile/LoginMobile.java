package org.tb.mobile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;
import org.tb.util.MD5Util;

import com.google.gson.Gson;

/**
 * Servlet for managing the mobile login.
 * 
 */

public class LoginMobile extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * Manages POST-requests with login data from the mobile login page.
     * 
     * @param  request  the Http request
     * @param  response the Http response to write to
     * 
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Map<String, Object> map = new HashMap<String, Object>();
        boolean isValid = false;
        String username = request.getParameter("username");
        String password = request.getParameter("password");
        ApplicationContext appContext = (ApplicationContext) getServletContext().getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        EmployeeDAO employeeDAO = (EmployeeDAO) appContext.getBean("employeeDAO");
        Employee employee = employeeDAO.getLoginEmployee(username, MD5Util.makeMD5(password));
        
        if (employee != null) {
            Long employeeId = employee.getId();
            isValid = true;
            request.getSession().setAttribute("employeeId", employeeId);
        }

        map.put("isValid", isValid);
        write(response, map);
    }

    /**
     * Writes the response with a map data in json format.
     * 
     * @param response the Http response to write to
     * @param map      contains the data to encode
     * 
     */
    private void write(HttpServletResponse response, Map<String, Object> map) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(new Gson().toJson(map));
    }

}
