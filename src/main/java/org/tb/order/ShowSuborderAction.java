package org.tb.order;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.TimereportDAO;
import org.tb.employee.Employee;

/**
 * action class for showing all suborders
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowSuborderAction extends LoginRequiredAction<ShowSuborderForm> {
    private static final Logger LOG = LoggerFactory.getLogger(ShowSuborderAction.class);

    private final SuborderDAO suborderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowSuborderForm suborderForm, HttpServletRequest request,
        HttpServletResponse response) {

        List<Customerorder> visibleCustomerOrders = customerorderDAO.getVisibleCustomerorders();
        request.getSession().setAttribute("visibleCustomerOrders", visibleCustomerOrders);
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        String filter = null;
        Boolean show = null;
        Long customerOrderId = null;

        LOG.debug("suborderForm.getShowStructure()" + suborderForm.getShowstructure());
        LOG.debug("suborderForm.getShow();" + suborderForm.getShow());
        LOG.debug("suborderForm.getFilter-()" + suborderForm.getFilter());
        LOG.debug("suborderForm.getCustomerOrderId-();" + suborderForm.getCustomerOrderId());
        LOG.debug("suborderFilter" + request.getSession().getAttribute("suborderFilter"));
        LOG.debug("suborderShow;" + request.getSession().getAttribute("suborderShow"));
        LOG.debug("suborderCustomerOrderId" + request.getSession().getAttribute("suborderCustomerOrderId"));
        LOG.debug("showStructure" + request.getSession().getAttribute("showStructure"));

        if (request.getParameter("task") != null && request.getParameter("task").equals("refresh")) {

            Boolean showStructure = suborderForm.getShowstructure();
            request.getSession().setAttribute("showStructure", showStructure);

            filter = suborderForm.getFilter();
            request.getSession().setAttribute("suborderFilter", filter);

            show = suborderForm.getShow();
            request.getSession().setAttribute("suborderShow", show);

            customerOrderId = suborderForm.getCustomerOrderId();
            request.getSession().setAttribute("suborderCustomerOrderId",
                    customerOrderId);

            Customerorder co = customerorderDAO.getCustomerorderById(suborderForm.getCustomerOrderId());
            LOG.debug("ShowSuborderAction.executeAuthenticated - suborderForm.getCustomerOrderId()" + suborderForm.getCustomerOrderId());
            request.getSession().setAttribute("currentOrder", co);
            if (customerOrderId == -1) {
                request.getSession().setAttribute("showStructure", false);
                suborderForm.setShowstructure(false);
            }

        } else {
            if (request.getSession().getAttribute("suborderFilter") != null) {
                filter = (String) request.getSession().getAttribute(
                        "suborderFilter");
                suborderForm.setFilter(filter);
            }
            if (request.getSession().getAttribute("suborderShow") != null) {
                show = (Boolean) request.getSession().getAttribute(
                        "suborderShow");
                suborderForm.setShow(show);
            }
            if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
                customerOrderId = (Long) request.getSession().getAttribute(
                        "suborderCustomerOrderId");
                suborderForm.setCustomerOrderId(customerOrderId);
                Customerorder co = customerorderDAO
                        .getCustomerorderById(suborderForm.getCustomerOrderId());
                LOG.debug("ShowSuborderAction.executeAuthenticated - suborderForm.getCustomerOrderId()" + suborderForm.getCustomerOrderId());
                request.getSession().setAttribute("currentOrder", co);
            } else {
                request.getSession().setAttribute("suborderCustomerOrderId", -1L);
                suborderForm.setCustomerOrderId(customerOrderId);
            }
            if (request.getSession().getAttribute("showStructure") != null) {
                Boolean showStructure = (Boolean) request.getSession()
                        .getAttribute("showStructure");
                suborderForm.setShowstructure(showStructure);
            } else {
                request.getSession().setAttribute("showStructure", false);
                suborderForm.setShowstructure(false);
            }
        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("setflag")) {
            Long coID = suborderForm.getCustomerOrderId();

            if (coID != -1) {
                Customerorder co = customerorderDAO.getCustomerorderById(coID);
                for (Suborder so : co.getSuborders()) {
                    if (!so.getCurrentlyValid()) {
                        so.setHide(true);
                        suborderDAO.save(so, loginEmployee);
                    }
                }
            } else {
                for (Customerorder co : customerorderDAO.getCustomerorders()) {
                    for (Suborder so : co.getSuborders()) {
                        if (!so.getCurrentlyValid()) {
                            so.setHide(true);
                            suborderDAO.save(so, loginEmployee);
                        }
                    }
                }
            }
        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("multiplechange")) {
            ActionMessages errorMessages = validateFormData(request, suborderForm);
            if (errorMessages.size() == 0) {
                String[] suborderIdArray = suborderForm.getSuborderIdArray();
                if (suborderIdArray != null) {
                    if (suborderForm.getSuborderOption().equals("delete")) {
                        List<String> soIDList = new ArrayList<>();
                        for (String soID : suborderIdArray) {
                            if (!suborderDAO.deleteSuborderById(Long.parseLong(soID))) {
                                soIDList.add(soID);
                            }
                        }
                        if (soIDList.size() > 0) {
                            errorMessages.add("suborderOption", new ActionMessage(
                                    "form.suborder.error.delete", soIDList));
                        }
                    }
                    if (suborderForm.getSuborderOption().equals("altersubordercustomer")) {
                        for (String soID : suborderIdArray) {
                            Suborder so = suborderDAO.getSuborderById(Long.parseLong(soID));
                            so.setSuborder_customer(suborderForm.getSuborderOptionValue());
                            suborderDAO.save(so, loginEmployee);
                        }
                    }
                    if (suborderForm.getSuborderOption().equals("alterhourlyrate")) {
                        for (String soID : suborderIdArray) {
                            Suborder so = suborderDAO.getSuborderById(Long.parseLong(soID));
                            so.setHourly_rate(Double.parseDouble(suborderForm.getSuborderOptionValue()));
                            suborderDAO.save(so, loginEmployee);
                        }
                    }
                }
            }
            suborderForm.setSuborderOption("");
            saveErrors(request, errorMessages);
            if (!suborderForm.getNoResetChoice()) {
                suborderForm.setSuborderIdArray(null);
            }
        }

        boolean showActualHours = suborderForm.getShowActualHours();
        request.getSession().setAttribute("showActualHours", showActualHours);

        if (showActualHours) {
            /* show actual hours */
            List<Suborder> suborders = suborderDAO.getSubordersByFilters(show, filter, customerOrderId);
            List<SuborderViewDecorator> suborderViewDecorators = new LinkedList<>();
            for (Suborder suborder : suborders) {
                SuborderViewDecorator decorator = new SuborderViewDecorator(
                        timereportDAO, suborder);
                suborderViewDecorators.add(decorator);
            }
            request.getSession().setAttribute("suborders",
                    suborderViewDecorators);
        } else {
            request.getSession().setAttribute(
                    "suborders",
                    suborderDAO.getSubordersByFilters(show, filter,
                            customerOrderId));
        }

        // check if loginEmployee has responsibility for some orders
        List<Customerorder> orders = customerorderDAO
                .getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee
                        .getId());
        boolean employeeIsResponsible = false;

        if (orders != null && orders.size() > 0) {
            employeeIsResponsible = true;
        }
        request.getSession().setAttribute("employeeIsResponsible",
                employeeIsResponsible);

        // check if there are visible customer orders
        orders = customerorderDAO.getVisibleCustomerorders();
        boolean visibleOrdersPresent = false;
        if (orders != null && !orders.isEmpty()) {
            visibleOrdersPresent = true;
        }
        request.getSession().setAttribute("visibleOrdersPresent",
                visibleOrdersPresent);

        if (request.getParameter("task") != null) {
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                // back to main menu
                return mapping.findForward("backtomenu");
            } else {
                // forward to show suborders jsp
                return mapping.findForward("success");
            }
        } else {
            // forward to show suborders jsp
            return mapping.findForward("success");
        }
    }

    private ActionMessages validateFormData(HttpServletRequest request, ShowSuborderForm suborderForm) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (suborderForm.getSuborderOption().equals("altersubordercustomer")) {
            if (suborderForm.getSuborderOptionValue().length() > GlobalConstants.SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH) {
                errors.add("suborderOption", new ActionMessage(
                        "form.suborder.error.suborder_customer.toolong"));
            }
        }

        if (suborderForm.getSuborderOption().equals("alterhourlyrate")) {
            if (suborderForm.getSuborderOptionValue().length() > GlobalConstants.CUSTOMERORDER_CURRENCY_MAX_LENGTH) {
                errors.add("suborderOption", new ActionMessage(
                        "form.suborder.error.currency.toolong"));
            }
            if (suborderForm.getSuborderOptionValue().length() <= 0) {
                errors.add("suborderOption", new ActionMessage(
                        "form.suborder.error.currency.required"));
            }

            // check hourly rate format
            if (!GenericValidator.isDouble(suborderForm.getSuborderOptionValue())
                    || !GenericValidator.isInRange(Double.parseDouble(suborderForm.getSuborderOptionValue()), 0.0,
                    GlobalConstants.MAX_HOURLY_RATE)) {
                errors.add("suborderOption", new ActionMessage(
                        "form.suborder.error.hourlyrate.wrongformat"));
            }
        }

        return errors;
    }
}
