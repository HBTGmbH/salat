package org.tb.restful.bookings;

import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;
import static org.tb.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import org.jboss.resteasy.spi.BadRequestException;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.*;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Path("/rest/buchungen/")
public class BookingsService {

    private EmployeeDAO employeeDAO;
    private EmployeecontractDAO employeecontractDAO;
    private TimereportDAO timereportDAO;
    private EmployeeorderDAO employeeorderDAO;
    private ReferencedayDAO referencedayDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setReferencedayDAO(ReferencedayDAO referencedayDAO) {
        this.referencedayDAO = referencedayDAO;
    }

    @GET
    @Path("list")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Booking> getBookings(@Context HttpServletRequest request, @QueryParam("datum") Date date, @QueryParam("mitarbeiter") String employeeStr) {
        Long employeeId = null;
        Employee employee;
        if (employeeStr != null) {
            employee = employeeDAO.getEmployeeBySign(employeeStr);
            if (employee == null) throw new BadRequestException("Could not find employee '" + employeeStr + "'!");
            employeeId = employee.getId();
        }
        if (employeeId == null) {
            employeeId = (Long) request.getSession().getAttribute("employeeId");
        }
        if (date == null) date = new Date();

        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, date);
        List<Timereport> timeReports = timereportDAO.getTimereportsByDateAndEmployeeContractId(ec.getId(), new java.sql.Date(date.getTime()));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        List<Booking> results = new ArrayList<>();
        for (Timereport tr : timeReports) {
            Booking booking = new Booking();
            booking.setEmployeeorderId(tr.getEmployeeorder().getId());
            booking.setCosts(tr.getCosts());
            booking.setDate(sdf.format(tr.getReferenceday().getRefdate()));
            Suborder suborder = tr.getSuborder();
            booking.setOrderLabel(tr.getSuborder().getCustomerorder().getShortdescription());
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

    @POST
    @Path("buchung")
    @Consumes(MediaType.APPLICATION_JSON)
    public void createBooking(@Context HttpServletRequest request, Booking booking) throws ParseException {
        Employeeorder eo = employeeorderDAO.getEmployeeorderById(booking.getEmployeeorderId());
        if (eo == null)
            throw new BadRequestException("Could not find employeeorder with id " + booking.getEmployeeorderId());

        Timereport timereport = new Timereport();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        Referenceday referenceday = referencedayDAO.getReferencedayByDate(sdf.parse(booking.getDate()));
        java.sql.Date datesql = new java.sql.Date(sdf.parse(booking.getDate()).getTime());
        // Check if the reference day already exists and if not add a new one
        if (referenceday == null) {
            referencedayDAO.addReferenceday(datesql);
            referenceday = referencedayDAO.getReferencedayByDate(datesql);
        }

        timereport.setEmployeeorder(eo);
        timereport.setEmployeecontract(eo.getEmployeecontract());
        timereport.setCreatedby(eo.getEmployeecontract().getEmployee().getSign());
        timereport.setCreated(new Date());
        timereport.setStatus(TIMEREPORT_STATUS_OPEN);
        timereport.setSortofreport(SORT_OF_REPORT_WORK);
        timereport.setReferenceday(referenceday);
        timereport.setTraining(false);
        timereport.setDurationhours(booking.getHours());
        timereport.setDurationminutes(booking.getMinutes());
        timereport.setSuborder(eo.getSuborder());
        timereport.setTaskdescription(booking.getComment());
    }
}
