package org.tb.restful.employee;

import lombok.RequiredArgsConstructor;
import org.tb.bdom.Employee;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Path("/rest/employee")
@RequiredArgsConstructor
public class EmployeeService {

    @GET
    @Path("/id")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public long getId(
            @Context HttpServletRequest request) {
        Employee employee = (Employee) request.getSession().getAttribute("loginEmployee");
        return employee.getId();
    }

    @GET
    @Path("/overtime")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public OvertimeData getOvertime(
            @Context HttpServletRequest request) {
        return OvertimeData.builder()
                .overtime((String) request.getSession().getAttribute("overtime"))
                .monthlyOvertime((String) request.getSession().getAttribute("monthlyOvertime"))
                .build();
    }


}
