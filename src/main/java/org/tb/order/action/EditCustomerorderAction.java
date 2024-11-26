package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.customer.domain.Customer;
import org.tb.customer.service.CustomerService;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.service.CustomerorderService;

/**
 * action class for editing a customer order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditCustomerorderAction extends LoginRequiredAction<AddCustomerorderForm> {

    private final CustomerorderService customerorderService;
    private final CustomerService customerService;
    private final EmployeeService employeeService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerorderForm coForm, HttpServletRequest request, HttpServletResponse response) {

        //		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long coId = Long.parseLong(request.getParameter("coId"));
        Customerorder co = customerorderService.getCustomerorderById(coId);
        request.getSession().setAttribute("coId", co.getId());

        List<Customer> customers = customerService.getAllCustomers();
        List<Customerorder> customerorders = customerorderService.getAllCustomerorders();

        if (customers == null || customers.size() <= 0) {
            request.setAttribute("errorMessage",
                    "No customers found - please call system administrator.");
            return mapping.findForward("error");
        }

        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("customers", customers);

        // get list of employees with employee contract
        List<Employee> employeesWithContracts = employeeService.getEmployeesWithValidContracts();

        // ensure already used employees are still in the list
        if(!employeesWithContracts.contains(co.getRespEmpHbtContract())) {
            employeesWithContracts.add(co.getRespEmpHbtContract());
        }
        if(!employeesWithContracts.contains(co.getResponsible_hbt())) {
            employeesWithContracts.add(co.getResponsible_hbt());
        }
        request.getSession().setAttribute("employeeswithcontract", employeesWithContracts);

        request.getSession().setAttribute("orderTypes", OrderType.values());

        // fill the form with properties of customerorder to be edited
        setFormEntries(coForm, co);

        // forward to customer order add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills customer order form with properties of given customer
     */
    private void setFormEntries(AddCustomerorderForm coForm, Customerorder co) {
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
        if (co.getOrderType() == null) {
            coForm.setOrderType(OrderType.STANDARD);
        } else {
            coForm.setOrderType(co.getOrderType());
        }

        coForm.setHide(co.getHide());

    }

}
