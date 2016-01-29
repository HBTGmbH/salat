package org.tb.restful;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
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
	public List<Booking> getBookings(@QueryParam("datum") String dateStr, @QueryParam("mitarbeiter") String employeeStr) {
		if(employeeStr == null) throw new BadRequestException("'mitarbeiter' is not set!");
		if(dateStr == null) throw new BadRequestException("'datum' is not set!");
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		Date date = null;
		try {
			date = sdf.parse(dateStr);
		} catch (ParseException e) {
			throw new BadRequestException(e);
		}
		
		Employee employee = employeeDAO.getEmployeeBySign(employeeStr);
		if(employee == null) throw new BadRequestException("Could not find employee '"+employeeStr+"'!");
		Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employee.getId(), date);
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
