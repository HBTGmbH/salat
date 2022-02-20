package org.tb.action.admin;

import org.apache.struts.action.*;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.bdom.*;
import org.tb.persistence.*;
import org.tb.action.LoginRequiredAction;
import org.tb.form.GenerateMultipleEmployeeordersForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * Class for generating multiple Employeeorders for one suborder at once
 *
 * @author sql
 */
@Component
public class GenerateMultipleEmployeeordersAction extends LoginRequiredAction<GenerateMultipleEmployeeordersForm> {

    private SuborderDAO suborderDAO;
    private CustomerorderDAO customerorderDAO;
    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private TimereportDAO timereportDAO;

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

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
                sos = suborderDAO.getSubordersByCustomerorderId(customerOrderId, generateMultipleEmployeeordersForm.getShowOnlyValid());
                request.getSession().setAttribute("showAllSuborders", true);
                request.getSession().setAttribute("currentSuborder", suborderId);
            } else if (suborderId != -1) {
                sos = suborderDAO.getSuborders(generateMultipleEmployeeordersForm.getShowOnlyValid());
                request.getSession().setAttribute("showAllSuborders", false);
                request.getSession().setAttribute("currentSuborder", suborderId);
            } else {
                sos = suborderDAO.getSuborders(generateMultipleEmployeeordersForm.getShowOnlyValid());
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
            Suborder so = suborderDAO.getSuborderById((Long) request.getSession().getAttribute("currentSuborder"));

            if (so == null) {
                errors.add("footer", new ActionMessage("form.multipleEmployeeorders.error.notSelectedSuborder"));
                saveErrors(request, errors);
                return mapping.getInputForward();
            }

            if (employeecontractIdArray != null) {
                // for every employeecontract that was chosen via multibox
                for (String ecID : employeecontractIdArray) {
                    List<Employeeorder> eos = employeeorderDAO.getEmployeeOrdersByEmployeeContractIdAndSuborderId(Long.parseLong(ecID), so.getId());
                    //create a new employeeorder only if no employeeorders for this employee/suborder already exist
                    if (eos.size() <= 0) {
                        Employeeorder eo = new Employeeorder();
                        Employeecontract ec = employeecontractDAO.getEmployeeContractById(Long.parseLong(ecID));
                        eo.setEmployeecontract(ec);
                        eo.setSuborder(so);
                        if (so.getFromDate().before(ec.getValidFrom())) {
                            eo.setFromDate(ec.getValidFrom());
                        } else {
                            eo.setFromDate(so.getFromDate());
                        }
                        if (so.getUntilDate() != null && ec.getValidUntil() != null && so.getUntilDate().before(ec.getValidUntil())
                                || so.getUntilDate() != null && ec.getValidUntil() == null || so.getUntilDate() == null && ec.getValidUntil() == null) {
                            eo.setUntilDate(so.getUntilDate());
                        } else if (so.getUntilDate() != null && ec.getValidUntil() != null && ec.getValidUntil().before(so.getUntilDate()) || so.getUntilDate() == null && ec.getValidUntil() != null) {
                            eo.setUntilDate(ec.getValidUntil());
                        }
                        eo.setSign("");
                        eo.setDebithours(so.getDebithours());
                        eo.setDebithoursunit(so.getDebithoursunit());
                        employeeorderDAO.save(eo, loginEmployee);

                        Long currentEmployeeId = (Long) request.getSession().getAttribute("currentEmployeeId");

                        if (currentEmployeeId.equals(ec.getEmployee().getId())) {
                            @SuppressWarnings("unchecked")
                            List<EmployeeOrderViewDecorator> decorators = (List<EmployeeOrderViewDecorator>) request.getSession().getAttribute("employeeorders");
                            EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(timereportDAO, eo);
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
                customerOrders = customerorderDAO.getCustomerorders();
            } else {
                customerOrders = customerorderDAO.getCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
            }
            request.getSession().setAttribute("visibleCustomerOrders", customerOrders);
            List<Employeecontract> employeecontracts = employeecontractDAO.getValidEmployeeContractsOrderedByFirstname();
            request.getSession().setAttribute("employeecontracts", employeecontracts);
            long selectedCustomerOrder = (Long) request.getSession().getAttribute("currentOrderId");
            long selectedSuborder = (Long) request.getSession().getAttribute("currentSub");
            List<Suborder> suborders;
            if (selectedSuborder != -1 || selectedCustomerOrder != -1) {
                request.getSession().setAttribute("showAllSuborders", false);
                suborders = suborderDAO.getSubordersByCustomerorderId(selectedCustomerOrder, generateMultipleEmployeeordersForm.getShowOnlyValid());
            } else {
                request.getSession().setAttribute("showAllSuborders", true);
                suborders = suborderDAO.getSuborders(generateMultipleEmployeeordersForm.getShowOnlyValid());
            }
            request.getSession().setAttribute("suborders", suborders);
            request.getSession().setAttribute("currentCustomer", selectedCustomerOrder);
            request.getSession().setAttribute("currentSuborder", selectedSuborder);
            return mapping.findForward("start");
        }
        return mapping.findForward("start");
    }
}
