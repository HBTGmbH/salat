package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.customer.Customer;
import org.tb.customer.CustomerService;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.service.CustomerorderService;

/**
 * action class for creating a new customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateCustomerorderAction extends LoginRequiredAction<AddCustomerorderForm> {

    private final EmployeeService employeeService;
    private final CustomerService customerService;
    private final CustomerorderService customerorderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerorderForm addForm, HttpServletRequest request, HttpServletResponse response) {
        // form presettings
        Long customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
        addForm.setCustomerId(customerId);

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        AddCustomerorderForm customerOrderForm = addForm;

        // get list of existing customers and customer orders
        List<Customer> customers = customerService.getAllCustomers();
        List<Customerorder> customerorders = customerorderService.getAllCustomerorders();

        if ((customers == null) || (customers.size() <= 0)) {
            request.setAttribute("errorMessage", "No customers found - please call system administrator.");
            return mapping.findForward("error");
        }

        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("customers", customers);

        // get list of employees with employee contract
        List<Employee> employeesWithContracts = employeeService.getEmployeesWithValidContracts();
        request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);

        request.getSession().setAttribute("orderTypes", OrderType.values());

        // reset/init form entries
        customerOrderForm.reset(mapping, request);

        // make sure, no coId still exists in the session
        request.getSession().removeAttribute("coId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
