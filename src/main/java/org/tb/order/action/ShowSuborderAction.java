package org.tb.order.action;

import static java.lang.Boolean.TRUE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.SuborderViewDecorator;

/**
 * action class for showing all suborders
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class ShowSuborderAction extends LoginRequiredAction<ShowSuborderForm> {

    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        ShowSuborderForm suborderForm, HttpServletRequest request,
        HttpServletResponse response) {

        List<Customerorder> visibleCustomerOrders = customerorderService.getVisibleCustomerorders();
        request.getSession().setAttribute("visibleCustomerOrders", visibleCustomerOrders);
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        String filter = null;
        Boolean show = null;
        Long customerOrderId = null;

        List<Suborder> suborders = (List<Suborder>) request.getSession().getAttribute("suborders");
        if(suborders == null) {
            suborders = List.of();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refresh")) {

            Boolean showStructure = suborderForm.getShowstructure();
            request.getSession().setAttribute("showStructure", showStructure);

            filter = suborderForm.getFilter();
            request.getSession().setAttribute("suborderFilter", filter);

            show = suborderForm.getShow();
            request.getSession().setAttribute("suborderShow", show);

            customerOrderId = suborderForm.getCustomerOrderId();
            request.getSession().setAttribute("suborderCustomerOrderId", customerOrderId);

            Customerorder co = customerorderService.getCustomerorderById(suborderForm.getCustomerOrderId());
            request.getSession().setAttribute("currentOrder", co);
            if (customerOrderId == -1) {
                request.getSession().setAttribute("showStructure", false);
                suborderForm.setShowstructure(false);
            }

        } else {
            if (request.getSession().getAttribute("suborderFilter") != null) {
                filter = (String) request.getSession().getAttribute("suborderFilter");
                suborderForm.setFilter(filter);
            }
            if (request.getSession().getAttribute("suborderShow") != null) {
                show = (Boolean) request.getSession().getAttribute("suborderShow");
                suborderForm.setShow(show);
            }
            if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
                customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
                suborderForm.setCustomerOrderId(customerOrderId);
                Customerorder co = customerorderService.getCustomerorderById(suborderForm.getCustomerOrderId());
                request.getSession().setAttribute("currentOrder", co);
            } else {
                request.getSession().setAttribute("suborderCustomerOrderId", -1L);
                suborderForm.setCustomerOrderId(customerOrderId);
            }
            if (request.getSession().getAttribute("showStructure") != null) {
                Boolean showStructure = (Boolean) request.getSession().getAttribute("showStructure");
                suborderForm.setShowstructure(showStructure);
            } else {
                request.getSession().setAttribute("showStructure", false);
                suborderForm.setShowstructure(false);
            }
        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("setflag")) {
            var today = DateUtils.today();
            var suborderIds = suborders.stream()
                .filter(so -> so.getHide() != TRUE)
                .filter(so -> so.getValidity().isBefore(today)) // outdated
                .map(Suborder::getId)
                .toList();
            suborderService.hideSuborders(suborderIds);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("multiplechange")) {
            ActionMessages errorMessages = validateFormData(request, suborderForm);
            if (errorMessages.isEmpty() && suborderForm.getSuborderIdArray() != null) {
                var suborderIds = Arrays.stream(suborderForm.getSuborderIdArray())
                    .mapToLong(Long::parseLong)
                    .boxed().toList();
                if (suborderForm.getSuborderOption().equals("delete")) {
                    for (long suborderId : suborderIds) {
                        suborderService.deleteSuborderById(suborderId);
                    }
                }
                if (suborderForm.getSuborderOption().equals("altersubordercustomer")) {
                    for (long suborderId : suborderIds) {
                        suborderService.changeSuborder_customer(suborderId, suborderForm.getSuborderOptionValue());
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
            suborders = suborderService.getSubordersByFilters(show, filter, customerOrderId);
            List<SuborderViewDecorator> suborderViewDecorators = new LinkedList<>();
            for (Suborder suborder : suborders) {
                SuborderViewDecorator decorator = new SuborderViewDecorator(suborderService, suborder);
                suborderViewDecorators.add(decorator);
            }
            request.getSession().setAttribute("suborders", suborderViewDecorators);
        } else {
            suborders = suborderService.getSubordersByFilters(show, filter, customerOrderId);
            request.getSession().setAttribute("suborders", suborders);
        }

        // check if loginEmployee has responsibility for some orders
        List<Customerorder> orders = customerorderService.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        boolean employeeIsResponsible = false;

        if (orders != null && !orders.isEmpty()) {
            employeeIsResponsible = true;
        }
        request.getSession().setAttribute("employeeIsResponsible", employeeIsResponsible);

        // check if there are visible customer orders
        orders = customerorderService.getVisibleCustomerorders();
        boolean visibleOrdersPresent = false;
        if (orders != null && !orders.isEmpty()) {
            visibleOrdersPresent = true;
        }
        request.getSession().setAttribute("visibleOrdersPresent", visibleOrdersPresent);

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

        return errors;
    }
}
