package org.tb.restful;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.spi.BadRequestException;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.TimereportDAO;

@Path("/rest/buchungen/")
public class BookingsService {
	
	private EmployeeDAO employeeDAO;
	public void setEmployeeDAO(EmployeeDAO employeeDAO) {
		this.employeeDAO = employeeDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

	public void setTimereportDAO(TimereportDAO timereportDAO) {
		this.timereportDAO = timereportDAO;
	}

	private EmployeecontractDAO employeecontractDAO;
	private TimereportDAO timereportDAO;

	@GET
	@Path("list")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Booking> getBookings(@Context HttpServletRequest request, @QueryParam("datum") Date date, @QueryParam("mitarbeiter") String employeeStr) {
		Long employeeId = null;
		Employee employee;
		if(employeeStr != null) {
			employee = employeeDAO.getEmployeeBySign(employeeStr);
			if(employee == null) throw new BadRequestException("Could not find employee '"+employeeStr+"'!");
			employeeId = employee.getId();
		}
		if(employeeId == null) {
			employeeId = (Long) request.getSession().getAttribute("employeeId");
		}
		if(date == null) date = new Date();
		
		Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
		List<Timereport> timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), new java.sql.Date(date.getTime()));
		
		List<Booking> results = new ArrayList<Booking>();
		for(Timereport tr : timeReports) {
			Booking booking = new Booking();
			booking.setEmployeeorderId(tr.getEmployeeorder().getId());
			Suborder suborder = tr.getSuborder();
			booking.setOrderLabel(tr.getSuborder().getCustomerorder().getDescription());
			booking.setSuborderLabel(tr.getSuborder().getDescription());
			booking.setComment(tr.getTaskdescription());
			booking.setTraining(tr.getSuborder().getCommentnecessary());
			booking.setHours(tr.getDurationhours());
			booking.setMinutes(tr.getDurationminutes());
			booking.setSuborderSign(suborder.getSign());
			booking.setOrderSign(suborder.getCustomerorder().getSign());
			results.add(booking);
		}
		
		return results;
	}
}
