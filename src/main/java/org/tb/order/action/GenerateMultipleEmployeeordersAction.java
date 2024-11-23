package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.EmployeeOrderViewDecorator;

/**
 * Class for generating multiple Employeeorders for one suborder at once
 *
 * @author sql
 */
@Component
@RequiredArgsConstructor
public class GenerateMultipleEmployeeordersAction extends LoginRequiredAction<GenerateMultipleEmployeeordersForm> {

    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        GenerateMultipleEmployeeordersForm generateMultipleEmployeeordersForm, HttpServletRequest request,
        HttpServletResponse response) {

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refresh")) {

            Long customerOrderId = generateMultipleEmployeeordersForm.getCustomerOrderId();
            Long suborderId = generateMultipleEmployeeordersForm.getSuborderId();
            List<Suborder> sos;
            if (customerOrderId != -1) {
                sos = suborderService.getSubordersByCustomerorderId(customerOrderId, generateMultipleEmployeeordersForm.getShowOnlyValid());
                request.getSession().setAttribute("showAllSuborders", true);
                request.getSession().setAttribute("currentSuborder", suborderId);
            } else if (suborderId != -1) {
                sos = suborderService.getSubordersByValidity(generateMultipleEmployeeordersForm.getShowOnlyValid());
                request.getSession().setAttribute("showAllSuborders", false);
                request.getSession().setAttribute("currentSuborder", suborderId);
            } else {
                sos = suborderService.getSubordersByValidity(generateMultipleEmployeeordersForm.getShowOnlyValid());
                request.getSession().setAttribute("showAllSuborders", true);
                request.getSession().setAttribute("currentSuborder", -1l);
            }
            request.getSession().setAttribute("currentCustomer", customerOrderId);
            request.getSession().setAttribute("suborders", sos);
            return mapping.findForward("start");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("multiplechange")) {

            if (request.getSession().getAttribute("suborderId") != null) {
                Long suborderId = (Long) request.getSession().getAttribute("suborderId");
                generateMultipleEmployeeordersForm.setSuborderId(suborderId);
            }
            String[] employeecontractIdArray = generateMultipleEmployeeordersForm.getEmployeecontractIdArray();
            Suborder so = suborderService.getSuborderById((Long) request.getSession().getAttribute("currentSuborder"));

            if (so == null) {
                errors.add("footer", new ActionMessage("form.multipleEmployeeorders.error.notSelectedSuborder"));
                saveErrors(request, errors);
                return mapping.getInputForward();
            }

            if (employeecontractIdArray != null) {
                // for every employeecontract that was chosen via multibox
                for (String ecID : employeecontractIdArray) {
                    List<Employeeorder> eos = employeeorderService.getEmployeeOrdersByEmployeeContractIdAndSuborderId(Long.parseLong(ecID), so.getId());
                    //create a new employeeorder only if no employeeorders for this employee/suborder already exist
                    if (eos.isEmpty()) {
                        Employeeorder eo = new Employeeorder();
                        Employeecontract ec = employeecontractService.getEmployeeContractById(Long.parseLong(ecID));
                        eo.setEmployeecontract(ec);
                        eo.setSuborder(so);
                        if (so.getFromDate().isBefore(ec.getValidFrom())) {
                            eo.setFromDate(ec.getValidFrom());
                        } else {
                            eo.setFromDate(so.getFromDate());
                        }
                        if (so.getUntilDate() != null && ec.getValidUntil() != null && so.getUntilDate().isBefore(ec.getValidUntil())
                                || so.getUntilDate() != null && ec.getValidUntil() == null || so.getUntilDate() == null && ec.getValidUntil() == null) {
                            eo.setUntilDate(so.getUntilDate());
                        } else if (so.getUntilDate() != null && ec.getValidUntil() != null && ec.getValidUntil().isBefore(so.getUntilDate()) || so.getUntilDate() == null && ec.getValidUntil() != null) {
                            eo.setUntilDate(ec.getValidUntil());
                        }
                        eo.setSign("");
                        eo.setDebithours(so.getDebithours());
                        eo.setDebithoursunit(so.getDebithoursunit());
                        employeeorderService.save(eo);

                        Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");

                        if (currentEmployeeId.equals(ec.getEmployee().getId())) {
                            @SuppressWarnings("unchecked")
                            List<EmployeeOrderViewDecorator> decorators = (List<EmployeeOrderViewDecorator>) request.getSession().getAttribute("employeeorders");
                            EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(timereportService, eo);
                            decorators.add(decorator);
                            request.getSession().setAttribute("employeeorders", decorators);
                        }
                    }
                }
            } else {
                generateMultipleEmployeeordersForm.setEmployeecontractIdArray(null);
                errors.add("footer", new ActionMessage("form.multipleEmployeeorders.error.notSelectedEmployee"));
                saveErrors(request, errors);
                return mapping.getInputForward();
            }
            generateMultipleEmployeeordersForm.setEmployeecontractIdArray(null);

            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("initialize")) {
            generateMultipleEmployeeordersForm.setShowOnlyValid(true);
            List<Customerorder> customerOrders;
            if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV)
                    || loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
                customerOrders = customerorderService.getAllCustomerorders();
            } else {
                customerOrders = customerorderService.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
            }
            request.getSession().setAttribute("visibleCustomerOrders", customerOrders);
            List<Employeecontract> employeecontracts = employeecontractService.getAllVisibleEmployeeContractsValidAtOrderedByFirstname(
                DateUtils.today());
            request.getSession().setAttribute("employeecontracts", employeecontracts);
            long selectedCustomerOrder = (Long) request.getSession().getAttribute("currentOrderId");
            long selectedSuborder = (Long) request.getSession().getAttribute("currentSub");
            List<Suborder> suborders;
            if (selectedSuborder != -1 || selectedCustomerOrder != -1) {
                request.getSession().setAttribute("showAllSuborders", false);
                suborders = suborderService.getSubordersByCustomerorderId(selectedCustomerOrder, generateMultipleEmployeeordersForm.getShowOnlyValid());
            } else {
                request.getSession().setAttribute("showAllSuborders", true);
                suborders = suborderService.getSubordersByValidity(generateMultipleEmployeeordersForm.getShowOnlyValid());
            }
            request.getSession().setAttribute("suborders", suborders);
            request.getSession().setAttribute("currentCustomer", selectedCustomerOrder);
            request.getSession().setAttribute("currentSuborder", selectedSuborder);
            return mapping.findForward("start");
        }
        return mapping.findForward("start");
    }
}
