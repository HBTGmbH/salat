package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Customer;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.persistence.CustomerDAO;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddCustomerOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * action class for creating a new customer order
 *
 * @author oda
 */
public class CreateCustomerorderAction extends LoginRequiredAction {

    private EmployeeDAO employeeDAO;
    private CustomerDAO customerDAO;
    private CustomerorderDAO customerorderDAO;

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setCustomerDAO(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }


    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {
        // form presettings
        Long customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
        request.getSession().setAttribute("projectIDExistsCustomerOrder", false);
        AddCustomerOrderForm addForm = (AddCustomerOrderForm) form;

        if (customerId == null) {
            customerId = 0l;
        }
        addForm.setCustomerId(customerId);

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        AddCustomerOrderForm customerOrderForm = (AddCustomerOrderForm) form;

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
