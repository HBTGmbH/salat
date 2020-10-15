package org.tb.web.action.admin;

import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.*;
import org.tb.bdom.CustomerOrderViewDecorator;
import org.tb.bdom.Customerorder;
import org.tb.bdom.ProjectID;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.ProjectIDDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.ShowCustomerOrderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;

/**
 * action class for deleting a customer order
 *
 * @author oda
 */
public class DeleteCustomerorderAction extends LoginRequiredAction {

    private CustomerorderDAO customerorderDAO;
    private TimereportDAO timereportDAO;
    private ProjectIDDAO projectIDDAO;

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setProjectIDDAO(ProjectIDDAO projectIDDAO) {
        this.projectIDDAO = projectIDDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) {

        if (GenericValidator.isBlankOrNull(request.getParameter("coId")) ||
                !GenericValidator.isLong(request.getParameter("coId"))) {
            return mapping.getInputForward();
        }

        ActionMessages errors = new ActionMessages();
        long coId = Long.parseLong(request.getParameter("coId"));
        Customerorder co = customerorderDAO.getCustomerorderById(coId);
        if (co == null) {
            return mapping.getInputForward();
        }

        ShowCustomerOrderForm orderForm = (ShowCustomerOrderForm) form;

        // before deleting the customerorder, delete all ProjectIDs that contain this customerorder
        List<ProjectID> pIDs = projectIDDAO.getProjectIDsByCustomerorderID(co.getId());
        for (ProjectID pID : pIDs) {
            projectIDDAO.deleteProjectID(pID);
        }

        boolean deleted = customerorderDAO.deleteCustomerorderById(coId);

        Long coID = (Long) request.getSession().getAttribute("currentOrderId");

        //fix for accessing deleted Order in EmployeeOrderAction
        if (deleted && coID != null && coID.equals(coId)) {
            request.getSession().setAttribute("currentOrderId", -2L);
        }

        if (!deleted) {
            errors.add(null, new ActionMessage("form.customerorder.error.hassuborders"));
        }
        saveErrors(request, errors);
        String filter = null;
        Boolean show = null;
        Long customerId = null;
        if (request.getSession().getAttribute("customerorderFilter") != null) {
            filter = (String) request.getSession().getAttribute("customerorderFilter");
        }
        if (request.getSession().getAttribute("customerorderShow") != null) {
            show = (Boolean) request.getSession().getAttribute("customerorderShow");
        }
        if (request.getSession().getAttribute("customerorderCustomerId") != null) {
            customerId = (Long) request.getSession().getAttribute("customerorderCustomerId");
        }

        orderForm.setFilter(filter);
        orderForm.setShow(show);
        orderForm.setCustomerId(customerId);

        boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
        orderForm.setShowActualHours(showActualHours);
        if (showActualHours) {
            /* show actual hours */
            List<Customerorder> customerOrders = customerorderDAO.getCustomerordersByFilters(show, filter, customerId);
            List<CustomerOrderViewDecorator> decorators = new LinkedList<CustomerOrderViewDecorator>();
            for (Customerorder customerorder : customerOrders) {
                CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportDAO, customerorder);
                decorators.add(decorator);
            }
            request.getSession().setAttribute("customerorders", decorators);
        } else {
            request.getSession().setAttribute("customerorders", customerorderDAO.getCustomerordersByFilters(show, filter, customerId));
        }

        // back to customer order display jsp
        return mapping.getInputForward();
    }
}
