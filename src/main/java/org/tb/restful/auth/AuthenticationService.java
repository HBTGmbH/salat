package org.tb.restful.auth;

import lombok.Setter;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
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
import javax.ws.rs.core.NewCookie;
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
    @Path("authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(@Context HttpServletRequest request, @QueryParam("username") String username,
                                 @QueryParam("password") String password) {

        Employee employee = employeeDAO.getLoginEmployee(username, SecureHashUtils.makeMD5(password));

        if (employee != null) {
            Long employeeId = employee.getId();
            Date date = new Date();

            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
                    employee.getId(), date);

            if (employeecontract == null && !employee.getStatus()
                    .equalsIgnoreCase(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                return Response.noContent().status(401).build();
            }
            if (employeecontract != null) {
                Long employeecontractId = employeecontract.getId();
                request.getSession().setAttribute("employeeId", employeeId);
                request.getSession().setAttribute("employeecontractId", employeecontractId);
            }
            String salt = UUID.randomUUID().toString();
            request.getSession().setAttribute("jaxrs.salt", salt);

            Map<String, Object> attributes = employeeDAO.getAttributes(request, employee);
            attributes.forEach((key, value) -> request.getSession().setAttribute(key, value));

            AfterLogin.handleOvertime(employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO,
                    request.getSession());


            //  TODO Here are severel parts missing from LoginEmployeeAction

            // XSRF-TOKEN must be read from Client and be put into a HTTP-header
            // X-XSRF-TOKEN or as a query param named XSRF_TOKEN
            NewCookie xsrfCookie = new NewCookie("XSRF-TOKEN", SecureHashUtils.makeMD5(employeeId + "." + salt));

            return Response.noContent().cookie(xsrfCookie).status(200).build();
        }
        return Response.noContent().status(401).build();
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

}
