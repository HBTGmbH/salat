package org.tb.action.admin;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.web.form.AddEmployeeOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * action class for editing an employee order
 *
 * @author oda
 */
public class EditEmployeeorderAction extends EmployeeOrderAction<AddEmployeeOrderForm> {

    private EmployeeorderDAO employeeorderDAO;
    private CustomerorderDAO customerorderDAO;
    private EmployeecontractDAO employeecontractDAO;

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeOrderForm eoForm, HttpServletRequest request, HttpServletResponse response) {
//		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long eoId = Long.parseLong(request.getParameter("eoId"));
        Employeeorder eo = employeeorderDAO.getEmployeeorderById(eoId);
        request.getSession().setAttribute("eoId", eo.getId());

        request.getSession().setAttribute("selectedcustomerorder", eo.getSuborder().getCustomerorder());
        request.getSession().setAttribute("selectedsuborder", eo.getSuborder());

        // fill the form with properties of employee order to be edited
        setFormEntries(request, eoForm, eo);

        // check if the employeeorder already exists and fill the form with the existing data
//		checkDatabaseForEmployeeOrder(request, eoForm, employeecontractDAO, employeeorderDAO);

        request.getSession().setAttribute("newemployeeorder", false);

        // forward to employee order add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills employee order form with properties of given employee order
     */
    private void setFormEntries(HttpServletRequest request, AddEmployeeOrderForm eoForm, Employeeorder eo) {
        Employeecontract ec = eo.getEmployeecontract();
        Employee theEmployee = ec.getEmployee();
        request.getSession().setAttribute("currentEmployee", theEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", theEmployee.getId());
        request.getSession().setAttribute("currentEmployeeContract", ec);

        List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();

        if ((employeeContracts == null) || (employeeContracts.size() <= 0)) {
            request.setAttribute("errorMessage",
                    "No employees with valid contracts found - please call system administrator.");
        }

        // set relevant attributes
        request.getSession().setAttribute("employeecontracts", employeeContracts);

        List<Customerorder> orders;
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            orders = customerorderDAO.getCustomerorders();
        } else {
            orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }

        request.getSession().setAttribute("orders", orders);

        List<Customerorder> orderswithsuborders = new ArrayList<Customerorder>();
        for (Customerorder customerorder : orders) {
            if (!(customerorder.getSuborders() == null || customerorder.getSuborders().isEmpty())) {
                orderswithsuborders.add(customerorder);
            }
        }
        if (orderswithsuborders.size() <= 0) {
            request.setAttribute("errorMessage",
                    "No customerorders with valid suborders found - please call system administrator.");
        }
//		request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("orderswithsuborders", orderswithsuborders);

        List<Suborder> suborders = eo.getSuborder().getCustomerorder().getSuborders();
//			 remove hidden suborders
        suborders.removeIf(Suborder::isHide);
        request.getSession().setAttribute("suborders", suborders);

        eoForm.setEmployeeContractId(ec.getId());
        eoForm.setOrderId(eo.getSuborder().getCustomerorder().getId());
        eoForm.setSuborderId(eo.getSuborder().getId());

        eoForm.setDebithours(eo.getDebithours());
        if (eo.getDebithours() != null) {
            eoForm.setDebithours(eo.getDebithours());
            eoForm.setDebithoursunit(eo.getDebithoursunit());
        } else {
            eoForm.setDebithours(null);
            eoForm.setDebithoursunit(null);
        }

        eoForm.setSign(eo.getSign());

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        eoForm.setValidFrom(simpleDateFormat.format(eo.getFromDate()));
        if (eo.getUntilDate() != null) {
            eoForm.setValidUntil(simpleDateFormat.format(eo.getUntilDate()));
        } else {
            eoForm.setValidUntil("");
        }
    }

}
