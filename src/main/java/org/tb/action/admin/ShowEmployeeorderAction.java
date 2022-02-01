package org.tb.action.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.*;
import org.tb.persistence.*;
import org.tb.web.form.ShowEmployeeOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * action class for showing all employee orders
 *
 * @author oda
 */
public class ShowEmployeeorderAction extends EmployeeOrderAction<ShowEmployeeOrderForm> {

    private EmployeeorderDAO employeeorderDAO;
    private SuborderDAO suborderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowEmployeeOrderForm orderForm, HttpServletRequest request,
        HttpServletResponse response) {

        if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("adjustDates")) {
            @SuppressWarnings("unchecked")
            List<Employeeorder> employeeOrders = (List<Employeeorder>) request.getSession().getAttribute("employeeorders");
            for (Employeeorder employeeorder : employeeOrders) {
                if (!employeeorder.getFitsToSuperiorObjects()) {
                    // adjust dates for employeeorders
                    boolean changed = false;
                    // 1) adjust to employeecontract
                    // 1.1) begin
                    if (employeeorder.getFromDate().before(employeeorder.getEmployeecontract().getValidFrom())) {
                        employeeorder.setFromDate(employeeorder.getEmployeecontract().getValidFrom());
                        changed = true;
                    }
                    // 1.2) end
                    if (employeeorder.getEmployeecontract().getValidUntil() != null &&
                            (employeeorder.getUntilDate() == null ||
                                    employeeorder.getUntilDate().after(employeeorder.getEmployeecontract().getValidUntil()))) {
                        employeeorder.setUntilDate(employeeorder.getEmployeecontract().getValidUntil());
                        changed = true;
                    }
                    // 2) adjust to suborder
                    // 2.1) begin
                    if (employeeorder.getFromDate().before(employeeorder.getSuborder().getFromDate())) {
                        employeeorder.setFromDate(employeeorder.getSuborder().getFromDate());
                        changed = true;
                    }
                    // 2.2) end
                    if (employeeorder.getSuborder().getUntilDate() != null &&
                            (employeeorder.getUntilDate() == null ||
                                    employeeorder.getUntilDate().after(employeeorder.getSuborder().getUntilDate()))) {
                        employeeorder.setUntilDate(employeeorder.getSuborder().getUntilDate());
                        changed = true;
                    }
                    // 3) begin after end now?
                    if (changed && !employeeorder.getFromDate().after(employeeorder.getUntilDate())) {
                        // 4) timereports out of range?
                        List<Timereport> timereportsInvalidForDates = timereportDAO.
                                getTimereportsByEmployeeorderIdInvalidForDates(employeeorder.getFromDate(), employeeorder.getUntilDate(), employeeorder.getId());
                        if (timereportsInvalidForDates == null || timereportsInvalidForDates.isEmpty()) {
                            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
                            employeeorderDAO.save(employeeorder, loginEmployee);
                        }
                    }
                }
            }
        }

        // get valid employeecontracts
        List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();
        request.getSession().setAttribute("employeecontracts", employeeContracts);

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        List<Customerorder> orders = customerorderDAO.getCustomerorders();
        request.getSession().setAttribute("orders", orders);

        if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("back")) {
            // back to main menu
            return mapping.findForward("backtomenu");
        }

        orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());

        boolean employeeIsResponsible = false;

        if (orders != null && orders.size() > 0) {
            employeeIsResponsible = true;
        }
        request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);

        refreshEmployeeOrdersAndSuborders(request, orderForm, employeeorderDAO, employeecontractDAO, timereportDAO, suborderDAO, customerorderDAO, !orderForm.getShow());

        return mapping.findForward("success");

    }

}
