package org.tb.restful.auth;

import lombok.Setter;
import org.tb.GlobalConstants;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;

import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeecontract;
import org.tb.helper.AfterLogin;
import org.tb.persistence.*;
import org.tb.util.SecureHashUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.Map;
import java.util.UUID;


@Path("/rest/AuthenticationService")
@Setter
public class AuthenticationService {

    private EmployeecontractDAO employeecontractDAO;
    private EmployeeDAO employeeDAO;
    private EmployeeorderDAO employeeorderDAO;
    private PublicholidayDAO publicholidayDAO;
    private TimereportDAO timereportDAO;
    private OvertimeDAO overtimeDAO;


    @GET
    @Path("/authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(@Context HttpServletRequest request, @QueryParam("username") String username,
                                 @QueryParam("password") String password) {

        Employee employee = employeeDAO.getLoginEmployee(username);
        if (employee != null && SecureHashUtils.passwordMatches(password, employee.getPassword())) {
            long employeeId = employee.getId();

            Employeecontract contract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, new Date());

            if (contract == null && !employee.getStatus()
                    .equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                return Response.noContent().status(UNAUTHORIZED).build();
            }
            if(contract != null) {
                long employeecontractId = contract.getId();
                request.getSession().setAttribute("employeeId", employeeId);
                request.getSession().setAttribute("employeecontractId", employeecontractId);

                // XSRF-TOKEN must be read from Client and be put into a HTTP-header x-xsrf-token
                String xsrfToken = UUID.randomUUID().toString();
                request.getSession().setAttribute("x-xsrf-token", xsrfToken);

                Map<String, Object> attributes = employeeDAO.getAttributes(request, employee);
                attributes.forEach((key, value) -> request.getSession().setAttribute(key, value));

                AfterLogin.handleOvertime(contract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO,
                        request.getSession());

                //  TODO Here are several parts missing from LoginEmployeeAction

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
