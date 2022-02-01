package org.tb.web.action.admin;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Suborder;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.web.action.LoginRequiredAction;
import org.tb.web.form.AddSuborderForm;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * action class for creating a new suborder
 *
 * @author oda, th
 */
public class CreateSuborderAction extends LoginRequiredAction<AddSuborderForm> {

    private CustomerorderDAO customerorderDAO;
    private SuborderDAO suborderDAO;

    public void setCustomerorderDAO(CustomerorderDAO customerorderDAO) {
        this.customerorderDAO = customerorderDAO;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddSuborderForm suborderForm, HttpServletRequest request, HttpServletResponse response) {

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // get login employee
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        // get lists of existing customerorders and suborders
        List<Customerorder> customerorders;
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            customerorders = customerorderDAO.getVisibleCustomerorders();
        } else {
            customerorders = customerorderDAO.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }


        List<Suborder> suborders = suborderDAO.getSuborders(false);

        if ((customerorders == null) || (customerorders.size() <= 0)) {
            request.setAttribute("errorMessage", "No customer orders found - please call system administrator.");
            return mapping.findForward("error");
        }

        // set relevant attributes
        request.getSession().setAttribute("customerorders", customerorders);
        request.getSession().setAttribute("suborders", suborders);
        request.getSession().setAttribute("invoice", "J");

        // use customer order from filter
        Long customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
        if (customerOrderId != null) {
            suborderForm.setCustomerorderId(customerOrderId);
        }

        // use last customer order als default if present
        if (request.getSession().getAttribute("lastCoId") != null) {
            long id = (Long) request.getSession().getAttribute("lastCoId");
            request.getSession().setAttribute("currentOrderId", id);
            Customerorder customerorder = customerorderDAO.getCustomerorderById(id);
            request.getSession().setAttribute("currentOrder", customerorder);
            suborderForm.setCustomerorderId(id);
        }

        // reset/init form entries
        suborderForm.reset(mapping, request);

        if (customerorders.size() > 0) {
            if (request.getSession().getAttribute("lastCoId") == null) {
                request.getSession().setAttribute("currentOrderId", customerorders.get(0).getId());
                request.getSession().setAttribute("currentOrder", customerorders.get(0));
            }

            Customerorder customerorder;

            if (customerOrderId != null && customerorderDAO.getCustomerorderById(customerOrderId) != null) {
                customerorder = customerorderDAO.getCustomerorderById(customerOrderId);
            } else {
                customerorder = customerorders.get(0);
            }
            request.getSession().setAttribute("currentOrderId", customerorder.getId());
            request.getSession().setAttribute("currentOrder", customerorder);
            request.getSession().setAttribute("hourlyRate", customerorder.getHourly_rate());
            request.getSession().setAttribute("parentDescriptionAndSign", customerorder.getSignAndDescription());
            request.getSession().setAttribute("suborderParent", customerorder);
            request.getSession().setAttribute("currentSuborderID", null);
            request.getSession().setAttribute("currency", customerorder.getCurrency());

            suborderForm.setParentDescriptionAndSign(customerorder.getSignAndDescription());
            suborderForm.setParentId(customerorder.getId());
            suborderForm.setHourlyRate(customerorder.getHourly_rate());
            suborderForm.setCurrency(customerorder.getCurrency());

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
            suborderForm.setValidFrom(simpleDateFormat.format(customerorder.getFromDate()));

            if (customerorder.getUntilDate() != null) {
                suborderForm.setValidUntil(simpleDateFormat.format(customerorder.getUntilDate()));
            } else {
                suborderForm.setValidUntil("");
            }

            suborderForm.setHide(customerorder.getHide());
        }

        // make sure, no soId still exists in session
        request.getSession().removeAttribute("soId");

        // forward to form jsp
        return mapping.findForward("success");
    }

}
