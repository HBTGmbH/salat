package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Suborder;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.SuborderViewDecorator;

/**
 * action class for deleting a suborder
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteSuborderAction extends LoginRequiredAction<ShowSuborderForm> {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteSuborderAction.class);

    private final SuborderService suborderService;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowSuborderForm suborderForm, HttpServletRequest request, HttpServletResponse response) {

        if ((GenericValidator.isBlankOrNull(request.getParameter("soId"))) ||
                (!GenericValidator.isLong(request.getParameter("soId"))))
            return mapping.getInputForward();

        ActionMessages errors = new ActionMessages();
        long soId = Long.parseLong(request.getParameter("soId"));
        Suborder so = suborderService.getSuborderById(soId);
        if (so == null)
            return mapping.getInputForward();

        boolean deleted = suborderService.deleteSuborderById(soId);

        if (!deleted) {
            errors.add(null, new ActionMessage("form.suborder.error.hastimereports.or.employeeorders"));
        }

        saveErrors(request, errors);

        String filter = null;
        Boolean show = null;
        Long customerOrderId = null;
        if (request.getSession().getAttribute("suborderFilter") != null) {
            filter = (String) request.getSession().getAttribute("suborderFilter");
        }
        if (request.getSession().getAttribute("suborderShow") != null) {
            show = (Boolean) request.getSession().getAttribute("suborderShow");
        }
        if (request.getSession().getAttribute("suborderCustomerOrderId") != null) {
            customerOrderId = (Long) request.getSession().getAttribute("suborderCustomerOrderId");
        }

        suborderForm.setFilter(filter);
        suborderForm.setShow(show);
        suborderForm.setCustomerOrderId(customerOrderId);

        boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
        suborderForm.setShowActualHours(showActualHours);
        if (showActualHours) {
            /* show actual hours */
            List<Suborder> suborders = suborderService.getSubordersByFilters(show, filter, customerOrderId);
            List<SuborderViewDecorator> suborderViewDecorators = new LinkedList<>();
            for (Suborder suborder : suborders) {
                SuborderViewDecorator decorator = new SuborderViewDecorator(timereportService, suborder);
                suborderViewDecorators.add(decorator);
            }
            request.getSession().setAttribute("suborders", suborderViewDecorators);
        } else {
            request.getSession().setAttribute("suborders", suborderService.getSubordersByFilters(show, filter, customerOrderId));
        }

        LOG.debug("DeleteSuborderAction.executeAuthenticated - after deletion");

        // back to suborder display jsp
        return mapping.getInputForward();
    }

}
