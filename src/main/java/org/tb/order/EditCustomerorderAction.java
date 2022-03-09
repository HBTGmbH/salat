package org.tb.order;

import java.time.LocalDate;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.customer.Customer;
import org.tb.customer.CustomerDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;

/**
 * action class for editing a customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditCustomerorderAction extends LoginRequiredAction<AddCustomerorderForm> {

    private final CustomerorderDAO customerorderDAO;
    private final CustomerDAO customerDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerorderForm coForm, HttpServletRequest request, HttpServletResponse response) {

        //		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long coId = Long.parseLong(request.getParameter("coId"));
        Customerorder co = customerorderDAO.getCustomerorderById(coId);
        request.getSession().setAttribute("coId", co.getId());

        List<Customer> customers = customerDAO.getCustomers();
        List<Customerorder> customerorders = customerorderDAO.getCustomerorders();

        if (customers == null || customers.size() <= 0) {
            request.setAttribute("errorMessage",
                    "No customers found - please call system administrator.");
            return mapping.findForward("error");
        }

        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("customers", customers);

        // get list of employees with employee contract
        List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithContracts();
        request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);

        // fill the form with properties of customerorder to be edited
        setFormEntries(request, coForm, co);

        // forward to customer order add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills customer order form with properties of given customer
     */
    private void setFormEntries(HttpServletRequest request, AddCustomerorderForm coForm, Customerorder co) {

        coForm.setCustomerId(co.getCustomer().getId());
        coForm.setOrderCustomer(co.getOrder_customer());

        coForm.setResponsibleCustomerContractually(co.getResponsible_customer_contractually());
        coForm.setResponsibleCustomerTechnical(co.getResponsible_customer_technical());
        if (co.getResponsible_hbt() != null) {
            coForm.setEmployeeId(co.getResponsible_hbt().getId());
        }
        if (co.getRespEmpHbtContract() != null) {
            coForm.setRespContrEmployeeId(co.getRespEmpHbtContract().getId());
        }
        coForm.setSign(co.getSign());
        coForm.setDescription(co.getDescription());
        coForm.setShortdescription(co.getShortdescription());

        LocalDate fromDate = co.getFromDate();
        coForm.setValidFrom(DateUtils.format(fromDate));
        if (co.getUntilDate() != null) {
            LocalDate untilDate = co.getUntilDate();
            coForm.setValidUntil(DateUtils.format(untilDate));
        } else {
            coForm.setValidUntil("");
        }

        if (co.getDebithours() != null && !co.getDebithours().isZero()) {
            coForm.setDebithours(DurationUtils.format(co.getDebithours()));
            coForm.setDebithoursunit(co.getDebithoursunit());
        } else {
            coForm.setDebithours(null);
            coForm.setDebithoursunit(null);
        }
        if (co.getStatusreport() == null) {
            coForm.setStatusreport(0);
        } else {
            coForm.setStatusreport(co.getStatusreport());
        }

        coForm.setHide(co.getHide());

    }

}
