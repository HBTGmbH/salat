package org.tb.order.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

/**
 * action class for showing all employee orders
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowEmployeeorderAction extends EmployeeOrderAction<ShowEmployeeOrderForm> {

    private final EmployeeorderService employeeorderService;
    private final SuborderService suborderService;
    private final EmployeecontractService employeecontractService;
    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;

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
                    if (employeeorder.getFromDate().isBefore(employeeorder.getEmployeecontract().getValidFrom())) {
                        employeeorder.setFromDate(employeeorder.getEmployeecontract().getValidFrom());
                        changed = true;
                    }
                    // 1.2) end
                    if (employeeorder.getEmployeecontract().getValidUntil() != null &&
                            (employeeorder.getUntilDate() == null ||
                                    employeeorder.getUntilDate().isAfter(employeeorder.getEmployeecontract().getValidUntil()))) {
                        employeeorder.setUntilDate(employeeorder.getEmployeecontract().getValidUntil());
                        changed = true;
                    }
                    // 2) adjust to suborder
                    // 2.1) begin
                    if (employeeorder.getFromDate().isBefore(employeeorder.getSuborder().getFromDate())) {
                        employeeorder.setFromDate(employeeorder.getSuborder().getFromDate());
                        changed = true;
                    }
                    // 2.2) end
                    if (employeeorder.getSuborder().getUntilDate() != null &&
                            (employeeorder.getUntilDate() == null ||
                                    employeeorder.getUntilDate().isAfter(employeeorder.getSuborder().getUntilDate()))) {
                        employeeorder.setUntilDate(employeeorder.getSuborder().getUntilDate());
                        changed = true;
                    }
                    // 3) begin after end now?
                    if (changed && !employeeorder.getFromDate().isAfter(employeeorder.getUntilDate())) {
                        employeeorderService.update(employeeorder);
                    } else {
                        employeeorderService.deleteEmployeeorderById(employeeorder.getId());
                    }
                }
            }
        }

        // get valid employeecontracts
        List<Employeecontract> employeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        request.getSession().setAttribute("employeecontracts", employeeContracts);

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        List<Customerorder> orders = customerorderService.getAllCustomerorders();
        request.getSession().setAttribute("orders", orders);

        if (request.getParameter("task") != null && request.getParameter("task").equalsIgnoreCase("back")) {
            // back to main menu
            return mapping.findForward("backtomenu");
        }

        orders = customerorderService.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());

        boolean employeeIsResponsible = false;

        if (orders != null && !orders.isEmpty()) {
            employeeIsResponsible = true;
        }
        request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);

        refreshEmployeeOrdersAndSuborders(request, orderForm, employeeorderService, employeecontractService, timereportService,
            suborderService,
            customerorderService, !orderForm.getShow());

        return mapping.findForward("success");

    }

}
