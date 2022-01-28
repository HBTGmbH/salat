package org.tb.restful.hello;

import lombok.RequiredArgsConstructor;
import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@RequiredArgsConstructor
@Path("/rest/HelloSalatWebservice")
public class HelloSalatWebservice {

    private final EmployeeDAO employeeDAO;

    @GET
    @Path("/sayHello")
    @Produces(MediaType.APPLICATION_JSON)
    public Message sayHello(@QueryParam("sign") String sign) {
        Employee employee = employeeDAO.getEmployeeBySign(sign);
        if (employee != null) {
          return new Message("Hello!", new Person(employee.getFirstname()));
        } else {
          return new Message("not found " + sign, null);
        }
    }

}

