package org.tb.restful.auth;

import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.Response.Status;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.util.SecureHashUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.UUID;

@Path("/rest/AuthenticationService")
public class AuthenticationService {

    private EmployeecontractDAO employeecontractDAO;
    private EmployeeDAO employeeDAO;

    @GET
    @Path("/authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(@Context HttpServletRequest request, @QueryParam("username") String username, @QueryParam("password") String password) {
        Employee employee = employeeDAO.getLoginEmployee(username, SecureHashUtils.makeMD5(password));
        if (employee != null) {
            long employeeId = employee.getId();
            Employeecontract contract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, new Date());
            if(contract != null) {
                long employeecontractId = contract.getId();
                request.getSession().setAttribute("employeeId", employeeId);
                request.getSession().setAttribute("employeecontractId", employeecontractId);

                // XSRF-TOKEN must be read from Client and be put into a HTTP-header x-xsrf-token
                String xsrfToken = UUID.randomUUID().toString();
                request.getSession().setAttribute("x-xsrf-token", xsrfToken);

                return Response.noContent().header("x-csrf-token", xsrfToken).status(OK).build();
            }
        }
        return Response.noContent().status(UNAUTHORIZED).build();
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

}
