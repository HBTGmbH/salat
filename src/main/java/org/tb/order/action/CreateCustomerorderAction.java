package org.tb.order.action;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.customer.Customer;
import org.tb.customer.CustomerDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;

/**
 * action class for creating a new customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CreateCustomerorderAction extends LoginRequiredAction<AddCustomerorderForm> {

    private final EmployeeDAO employeeDAO;
    private final CustomerDAO customerDAO;
    private final CustomerorderDAO customerorderDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerorderForm addForm, HttpServletRequest request, HttpServletResponse response) {
        // form presettings
        Long customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");

        if (customerId == null) {
            customerId = 0L;
        }
        addForm.setCustomerId(customerId);

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        AddCustomerorderForm customerOrderForm = addForm;

        // get list of existing customers and customer orders
        List<Customer> customers = customerDAO.getCustomers();
        List<Customerorder> customerorders = customerorderDAO.getCustomerorders();

        if ((customers == null) || (customers.size() <= 0)) {
            request.setAttribute("errorMessage", "No customers found - please call system administrator.");
            return mapping.findForward("error");
        }

        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("customers", customers);

        // get list of employees with employee contract
        List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithContracts();
        request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);

        // reset/init form entries
        customerOrderForm.reset(mapping, request);

        // make sure, no coId still exists in the session
        request.getSession().removeAttribute("coId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
