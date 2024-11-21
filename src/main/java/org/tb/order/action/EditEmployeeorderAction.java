package org.tb.order.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;

/**
 * action class for editing an employee order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditEmployeeorderAction extends EmployeeOrderAction<AddEmployeeOrderForm> {

    private final EmployeeorderService employeeorderService;
    private final CustomerorderService customerorderService;
    private final EmployeecontractService employeecontractService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeOrderForm eoForm, HttpServletRequest request, HttpServletResponse response) {
//		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long eoId = Long.parseLong(request.getParameter("eoId"));
        Employeeorder eo = employeeorderService.getEmployeeorderById(eoId);
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

        List<Employeecontract> employeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());

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
            orders = customerorderService.getAllCustomerorders();
        } else {
            orders = customerorderService.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
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

        if (eo.getDebithours() != null && !eo.getDebithours().isZero()) {
            eoForm.setDebithours(DurationUtils.format(eo.getDebithours()));
            eoForm.setDebithoursunit(eo.getDebithoursunit());
        } else {
            eoForm.setDebithours(null);
            eoForm.setDebithoursunit(null);
        }

        eoForm.setSign(eo.getSign());

        eoForm.setValidFrom(DateUtils.format(eo.getFromDate()));
        if (eo.getUntilDate() != null) {
            eoForm.setValidUntil(DateUtils.format(eo.getUntilDate()));
        } else {
            eoForm.setValidUntil("");
        }
    }

}
