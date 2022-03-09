package org.tb.order.action;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DurationUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.domain.Suborder;

/**
 * action class for creating a new employee order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateEmployeeorderAction extends EmployeeOrderAction<AddEmployeeOrderForm> {

    private final EmployeeorderDAO employeeorderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final EmployeecontractDAO employeecontractDAO;

    @Override
    public ActionForward executeAuthenticated(final ActionMapping mapping,
                                              final AddEmployeeOrderForm employeeOrderForm, final HttpServletRequest request,
                                              final HttpServletResponse response) {

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // get lists of existing employee contracts and suborders
        final List<Employeecontract> employeeContracts = employeecontractDAO.getVisibleEmployeeContractsOrderedByEmployeeSign();

        if ((employeeContracts == null) || (employeeContracts.size() <= 0)) {
            request.setAttribute("errorMessage", "No employees with valid contracts found - please call system administrator.");
            return mapping.findForward("error");
        }

        // set relevant attributes
        // request.getSession().setAttribute("employees", employees);
        request.getSession().setAttribute("employeecontracts", employeeContracts);

        List<Customerorder> orders;
        final Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL)
                || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            orders = customerorderDAO.getCustomerorders();
        } else {
            orders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }

        List<Customerorder> orderswithsuborders = new ArrayList<>();
        for (Customerorder customerorder : orders) {
            if (!(customerorder.getSuborders() == null || customerorder.getSuborders().isEmpty())) {
                orderswithsuborders.add(customerorder);
            }
        }
        if (orderswithsuborders.size() <= 0) {
            request.setAttribute("errorMessage", "No customerorders with valid suborders found - please call system administrator.");
            return mapping.findForward("error");
        }

        request.getSession().setAttribute("orderswithsuborders", orderswithsuborders);

        final List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeorders();
        request.getSession().setAttribute("employeeorders", employeeorders);

        final Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (employeecontract != null) {
            employeeOrderForm.setEmployeeContractId(employeecontract.getId());
        }

        // init form with first order and corresponding suborders
        final List<Suborder> theSuborders = new ArrayList<>();
        request.getSession().setAttribute("suborders", theSuborders);
        if (!orderswithsuborders.isEmpty()) {
            Customerorder selectedCustomerorder;

            final Long orderId = (Long) request.getSession().getAttribute("currentOrderId");
            final Customerorder customerOrderFromFilter = customerorderDAO.getCustomerorderById(orderId);
            if (orderswithsuborders.contains(customerOrderFromFilter)) {
                selectedCustomerorder = customerOrderFromFilter;
            } else {
                selectedCustomerorder = orderswithsuborders.get(0);
            }

            request.getSession().setAttribute("selectedcustomerorder", selectedCustomerorder);

            // reset/init form entries
            boolean showOnlyValid = true;
            employeeOrderForm.reset(mapping, request, true);
            employeeOrderForm.setShowOnlyValid(showOnlyValid);
            employeeOrderForm.useDatesFromCustomerOrder(selectedCustomerorder);

            employeeOrderForm.setOrder(selectedCustomerorder.getSign());
            employeeOrderForm.setOrderId(selectedCustomerorder.getId());

            final List<Suborder> suborders = selectedCustomerorder.getSuborders();
            // remove hidden suborders
            final Iterator<Suborder> suborderIterator = suborders.iterator();
            while (suborderIterator.hasNext()) {
                final Suborder suborder = suborderIterator.next();
                if (suborder.isHide()) {
                    suborderIterator.remove();
                } else if (!suborder.getCurrentlyValid()) {
                    suborderIterator.remove();
                }
            }

            request.getSession().setAttribute("suborders", suborders);
            if (!suborders.isEmpty()) {
                request.getSession().setAttribute("selectedsuborder", suborders.get(0));
            }
            if ((selectedCustomerorder.getSuborders() != null) && (!selectedCustomerorder.getSuborders().isEmpty())) {
                employeeOrderForm.setSuborder(selectedCustomerorder.getSuborders().get(0).getSign());
                employeeOrderForm.setSuborderId(selectedCustomerorder.getSuborders().get(0).getId());
            }

            if (!selectedCustomerorder.getSuborders().isEmpty()
                && selectedCustomerorder.getSuborders().get(0).getDebithours() != null
                && !selectedCustomerorder.getSuborders().get(0).getDebithours().isZero()) {
                employeeOrderForm.setDebithours(DurationUtils.format(selectedCustomerorder.getSuborders().get(0).getDebithours()));
                /* set unit if applicable */
                employeeOrderForm.setDebithoursunit(selectedCustomerorder.getSuborders().get(0).getDebithoursunit());
            } else {
                employeeOrderForm.setDebithoursunit(null);
            }

        }

        setFormDates(request, employeeOrderForm);

        // make sure, no eoId still exists in session
        request.getSession().removeAttribute("eoId");

        request.getSession().setAttribute("newemployeeorder", true);
        return mapping.findForward("success");
    }
}
