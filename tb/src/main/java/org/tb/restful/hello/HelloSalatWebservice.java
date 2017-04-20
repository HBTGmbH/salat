package org.tb.restful.hello;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.tb.bdom.Employee;
import org.tb.persistence.EmployeeDAO;

@Path("/rest/HelloSalatWebservice")
public class HelloSalatWebservice {
	
	private EmployeeDAO employeeDAO;
	
	@GET
	@Path("/sayHello")
	@Produces(MediaType.APPLICATION_JSON)
	public Message sayHello(@QueryParam("sign") String sign) {
		Employee employee = employeeDAO.getEmployeeBySign(sign);
		
		return new Message("Hello!", new Person(employee.getFirstname()));
	}

	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

}

