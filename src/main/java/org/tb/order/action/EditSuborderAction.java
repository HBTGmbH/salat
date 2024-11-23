package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * action class for editing a suborder
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditSuborderAction extends LoginRequiredAction<AddSuborderForm> {

    private final SuborderService suborderService;
    private final CustomerorderService customerorderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddSuborderForm soForm, HttpServletRequest request, HttpServletResponse response) {
        //		 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long soId = Long.parseLong(request.getParameter("soId"));
        Suborder so = suborderService.getSuborderById(soId);
        request.getSession().setAttribute("soId", so.getId());

        // fill the form with properties of suborder to be edited
        setFormEntries(request, soForm, so);

        // make sure all customer orders are available in form
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Customerorder> customerorders;
        if (loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_BL) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_PV) ||
                loginEmployee.getStatus().equals(GlobalConstants.EMPLOYEE_STATUS_ADM)) {
            customerorders = customerorderService.getVisibleCustomerorders();
        } else {
            customerorders = customerorderService.getVisibleCustomerOrdersByResponsibleEmployeeId(loginEmployee.getId());
        }
        request.getSession().setAttribute("customerorders", customerorders);

        request.getSession().setAttribute("orderTypes", OrderType.values());

        // forward to suborder add/edit form
        return mapping.findForward("success");
    }

    /**
     * fills suborder form with properties of given suborder
     */
    private void setFormEntries(HttpServletRequest request, AddSuborderForm soForm, Suborder so) {
        soForm.setCustomerorderId(so.getCustomerorder().getId());
        soForm.setSign(so.getSign());
        soForm.setDescription(so.getDescription());
        soForm.setShortdescription(so.getShortdescription());
        soForm.setInvoice(so.getInvoice());
        soForm.setStandard(so.getStandard());
        soForm.setCommentnecessary(so.getCommentnecessary());
        soForm.setTrainingFlag(so.getTrainingFlag());
        soForm.setFixedPrice(so.getFixedPrice());
        soForm.setSuborder_customer(so.getSuborder_customer());
        if (so.getParentorder() != null) {
            soForm.setParentId(so.getParentorder().getId());
        } else {
            soForm.setParentId(so.getCustomerorder().getId());
        }
        Suborder tempSubOrder = suborderService.getSuborderById(soForm.getParentId());
        if (tempSubOrder != null && Objects.equals(tempSubOrder.getCustomerorder().getId(), so.getCustomerorder().getId())) {
            soForm.setParentDescriptionAndSign(tempSubOrder.getSignAndDescription());
            request.getSession().setAttribute("suborderParent", tempSubOrder);
        } else {
            Customerorder tempOrder = customerorderService.getCustomerorderById(soForm.getParentId());
            soForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
            request.getSession().setAttribute("suborderParent", tempOrder);
        }
        request.getSession().setAttribute("parentDescriptionAndSign", soForm.getParentDescriptionAndSign());

        soForm.setValidFrom(DateUtils.format(so.getFromDate()));
        if (so.getUntilDate() != null) {
            soForm.setValidUntil(DateUtils.format(so.getUntilDate()));
        } else {
            soForm.setValidUntil("");
        }

        if (so.getDebithours() != null && !so.getDebithours().isZero()) {
            soForm.setDebithours(DurationUtils.format(so.getDebithours()));
            soForm.setDebithoursunit(so.getDebithoursunit());
        } else {
            soForm.setDebithours(null);
            soForm.setDebithoursunit(null);
        }
        soForm.setHide(so.isHide());
        soForm.setOrderType(so.getOrderType());

        //request.getSession().setAttribute("currentSuborderID", new Long(so.getId()));
        request.getSession().setAttribute("currentOrderId", so.getCustomerorder().getId());
        request.getSession().setAttribute("currentOrder", so.getCustomerorder());
        request.getSession().setAttribute("invoice", Character.toString(so.getInvoice()));
    }

}
