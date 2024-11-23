package org.tb.order.action;

import static org.tb.common.GlobalConstants.YESNO_NO;
import static org.tb.common.GlobalConstants.YESNO_UNDEFINED;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.SuborderViewDecorator;

/**
 * action class for storing a suborder permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreSuborderAction extends LoginRequiredAction<AddSuborderForm> {
    private static final Logger LOG = LoggerFactory.getLogger(StoreSuborderAction.class);

    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;
    private final SuborderService suborderService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        AddSuborderForm addSuborderForm, HttpServletRequest request,
        HttpServletResponse response) {

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? addSuborderForm.getValidUntil() : addSuborderForm.getValidFrom();

            LocalDate newValue;
            if (howMuch != 0) {
                ActionMessages errorMessages = valiDate(request, addSuborderForm, which);
                if (!errorMessages.isEmpty()) {
                    return mapping.getInputForward();
                }

                newValue = DateUtils.parseOrDefault(datum, today());
                newValue = addDays(newValue, howMuch);
            } else {
                newValue = today();
            }

            datum = format(newValue);

            request.getSession().setAttribute(which.equals("until") ? "validUntil" : "validFrom", datum);

            if (which.equals("until")) {
                addSuborderForm.setValidUntil(datum);
            } else {
                addSuborderForm.setValidFrom(datum);
            }

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("copy")) {

            long soId = Long.parseLong(request.getSession().getAttribute("soId").toString());
            Suborder so = suborderService.getSuborderById(soId);

            if (so != null) {
                Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
                Suborder copy = so.copy(true, loginEmployee.getSign());

                suborderService.save(copy);

                request.getSession().removeAttribute("soId");

                // store used customer order id for the next creation of a suborder
                request.getSession().setAttribute("lastCoId", so.getCustomerorder().getId());
            }

            refreshForOverview(request);

            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("fitDates")) {

            if (request.getSession().getAttribute("soId") != null) {
                long suborderId = Long.parseLong(request.getSession().getAttribute("soId").toString());
                try {
                    suborderService.fitValidityOfChildren(suborderId);
                } catch (BusinessRuleException e) {
                    addToErrors(request, e.getErrorCode());
                    return mapping.getInputForward();
                }

            }
            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("generateSign")) {
            //*** task for generating new suborder's sign
            Suborder tempSubOrder = suborderService.getSuborderById(addSuborderForm.getParentId());
            Customerorder tempOrder = customerorderService.getCustomerorderById(addSuborderForm.getParentId());
            List<Suborder> suborders = suborderService.getAllSuborders();
            LOG.debug("StoreSuborderAction.executeAuthenticated() - three Values: " + tempSubOrder + " / " + tempOrder + " / " + suborders);
            Long soId;
            try {
                soId = Long.valueOf(request.getSession().getAttribute("soId").toString());
            } catch (Throwable th) {
                soId = -1L;
            }
            if (suborders != null) {
                if (tempSubOrder != null && (tempOrder == null || Objects.equals(
                    tempSubOrder.getCustomerorder().getId(), tempOrder.getId()))) {
                    int version = 1;
                    DecimalFormat df = new DecimalFormat("00");
                    for (Suborder suborder : suborders) {
                        if (suborder.getParentorder() != null && Objects.equals(suborder.getParentorder().getId(),
                            tempSubOrder.getId())) {
                            if (suborder.getSign().equals(tempSubOrder.getSign() + "." + df.format(version)) && !soId.equals(suborder.getId())) {
                                version++;
                            }
                        }
                    }
                    addSuborderForm.setSign(tempSubOrder.getSign() + "." + df.format(version));
                } else if (tempOrder != null) {
                    int version = 1;
                    DecimalFormat df = new DecimalFormat("00");
                    for (Suborder suborder : suborders) {
                        if (suborder.getParentorder() == null && Objects.equals(suborder.getCustomerorder().getId(),
                            tempOrder.getId())) {
                            if (suborder.getSign().equals(tempOrder.getSign() + "." + df.format(version)) && !soId.equals(suborder.getId())) {
                                version++;
                            }
                        }
                    }
                    addSuborderForm.setSign(tempOrder.getSign() + "." + df.format(version));
                }
            }
            request.getSession().setAttribute("invoice", addSuborderForm.getInvoice());
            return mapping.getInputForward();
        }

        if ("changeCustomerorder".equals(request.getParameter("task"))) {
            Customerorder parentOrder = customerorderService.getCustomerorderById(addSuborderForm.getCustomerorderId());

            addSuborderForm.setParentDescriptionAndSign(parentOrder.getSignAndDescription());
            addSuborderForm.setParentId(addSuborderForm.getCustomerorderId());
            addSuborderForm.setValidFrom(format(parentOrder.getFromDate()));
            if(parentOrder.getUntilDate() != null) {
                addSuborderForm.setValidUntil(format(parentOrder.getUntilDate()));
            } else {
                addSuborderForm.setValidUntil(null);
            }

            request.getSession().setAttribute("currentOrder", parentOrder);
            request.getSession().setAttribute("currentOrderId", parentOrder.getId());
            request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            request.getSession().setAttribute("suborderParent", parentOrder);
            request.getSession().setAttribute("suborders", suborderService.getSubordersByCustomerorderId(addSuborderForm.getCustomerorderId(),true));

            return mapping.getInputForward();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshParentProject")) {

            Customerorder parentOrder = customerorderService.getCustomerorderById(addSuborderForm.getCustomerorderId());

            addSuborderForm.setParentDescriptionAndSign(parentOrder.getSignAndDescription());
            addSuborderForm.setParentId(addSuborderForm.getCustomerorderId());

            request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            request.getSession().setAttribute("suborderParent", parentOrder);

            if (request.getParameter("continue") != null) {
                addSuborderForm.setParentId(Long.parseLong(request.getParameter("continue")));
                Suborder tempSubOrder = suborderService.getSuborderById(addSuborderForm.getParentId());
                if (tempSubOrder != null && tempSubOrder.getCustomerorder().getId() != addSuborderForm.getCustomerorderId()) {
                    LOG.info("Suborder does not match customerorder. Reset to null");
                    tempSubOrder = null;
                }

                if (tempSubOrder != null) {
                    addSuborderForm.setParentDescriptionAndSign(tempSubOrder.getSignAndDescription());

                    String parentOrderFromDate = "";
                    String parentOrderUntilDate = "";
                    if (tempSubOrder.getFromDate() != null) {
                        parentOrderFromDate = tempSubOrder.getFromDate().toString();
                    }
                    if (tempSubOrder.getUntilDate() != null) {
                        parentOrderUntilDate = tempSubOrder.getUntilDate().toString();
                    }
                    addSuborderForm.setValidFrom(parentOrderFromDate);
                    addSuborderForm.setValidUntil(parentOrderUntilDate);

                    request.getSession().setAttribute("suborderParent", tempSubOrder);
                } else {
                    Customerorder tempOrder = customerorderService.getCustomerorderById(addSuborderForm.getParentId());
                    addSuborderForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
                    request.getSession().setAttribute("suborderParent", tempOrder);
                }
                request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            }

            return mapping.getInputForward();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("save") || request.getParameter("soId") != null) {
            //*** task for saving new suborder
            ActionMessages errorMessages = validateFormData(request, addSuborderForm);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            // 'main' task - prepare everything to store the suborder.
            // I.e., copy properties from the form into the suborder before
            // saving.
            Long soId = null;
            Customerorder customerorder = customerorderService.getCustomerorderById(addSuborderForm.getCustomerorderId());

            if(request.getSession().getAttribute("soId") != null) {
                soId = Long.parseLong(request.getSession().getAttribute("soId").toString());
            }
            Suborder so = suborderService.createOrUpdate(soId, addSuborderForm, customerorder);

            request.getSession().removeAttribute("soId");

            // store used customer order id for the next creation of a suborder
            request.getSession().setAttribute("lastCoId", so.getCustomerorder().getId());

            boolean addMoreSuborders = Boolean.parseBoolean(request.getParameter("continue"));

            if (!addMoreSuborders) {
                refreshForOverview(request);
                return mapping.findForward("success");
            } else {
                request.getSession().setAttribute("suborders", suborderService.getAllSuborders());
                // reuse form entries and show add-page
                addSuborderForm.setDescription("");
                addSuborderForm.setSign("");
                addSuborderForm.setSuborder_customer("");
                addSuborderForm.setInvoice(GlobalConstants.INVOICE_YES);
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("soId");
            addSuborderForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, addSuborderForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddSuborderForm soForm) {
        soForm.reset(mapping, request);
    }

    private ActionMessages valiDate(HttpServletRequest request, AddSuborderForm soForm, String which) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (which.equals("from")) {
            if(!DateUtils.validateDate(soForm.getValidFrom())) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        } else {
            if(!DateUtils.validateDate(soForm.getValidFrom())) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddSuborderForm addSuborderForm) {

        ActionMessages errors = getErrors(request);

        if (errors == null) {
            errors = new ActionMessages();
        }

        Long suborderId;
        if (request.getSession().getAttribute("soId") != null) {
            // edited suborder
            suborderId = (Long) request.getSession().getAttribute("soId");
        } else {
            // new suborder
            suborderId = 0L;
        }

        // for a new suborder, check if the sign already exists
        if (Objects.equals(suborderId, 0L)) {
            // Liste aller Children der übergeordneten Suborder
            // ggf. gibt es keine übergeordnete Suborder (=null?)
            // dann die untergeordneten Suboders der Customerorder.
            List<Suborder> suborders;
            if (addSuborderForm.getParentId() == null) {
                suborders = suborderService.getSubordersByCustomerorderId(addSuborderForm.getCustomerorderId(), false);
                for (Suborder suborder : suborders) {
                    if (suborder.getCurrentlyValid()
                            && suborder.getParentorder() == null // vergleiche nur Suborder direkt unter der Customerorder
                            && suborder.getSign().equalsIgnoreCase(addSuborderForm.getSign())) {
                        errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));
                        break;
                    }
                }
            } else {
                suborders = suborderService.getSuborderChildren(addSuborderForm.getParentId());
                for (Suborder suborder : suborders) {
                    if (suborder.getCurrentlyValid() && suborder.getSign().equalsIgnoreCase(addSuborderForm.getSign())) {
                        errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));
                        break;
                    }
                }
            }
        }

        // check length of text fields
        if (addSuborderForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
            errors.add("sign", new ActionMessage("form.suborder.error.sign.toolong"));
        }
        if (addSuborderForm.getSign().isEmpty()) {
            errors.add("sign", new ActionMessage("form.suborder.error.sign.required"));
        }
        if (addSuborderForm.getDescription().length() > GlobalConstants.SUBORDER_DESCRIPTION_MAX_LENGTH) {
            errors.add("description", new ActionMessage("form.suborder.error.description.toolong"));
        }
        if (addSuborderForm.getDescription().trim().isEmpty()) {
            errors.add("description", new ActionMessage("form.error.description.necessary"));
        }
        if (addSuborderForm.getShortdescription().length() > GlobalConstants.SUBORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
            errors.add("shortdescription", new ActionMessage("form.suborder.error.shortdescription.toolong"));
        }
        if (addSuborderForm.getSuborder_customer().length() > GlobalConstants.SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH) {
            errors.add("suborder_customer", new ActionMessage("form.suborder.error.suborder_customer.toolong"));
        }
        // check invoice character
        if (addSuborderForm.getInvoice() != YESNO_YES
                && addSuborderForm.getInvoice() != YESNO_NO
                && addSuborderForm.getInvoice() != YESNO_UNDEFINED) {

            errors.add("invoice", new ActionMessage("form.suborder.error.invoice.invalid"));
        }
        // check date formats
        LocalDate suborderFromDate = null;
        if(validateDate(addSuborderForm.getValidFrom())) {
            suborderFromDate = DateUtils.parse(addSuborderForm.getValidFrom());
        } else {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        LocalDate suborderUntilDate = null;
        if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().isEmpty()) {
            if(validateDate(addSuborderForm.getValidUntil())) {
                suborderUntilDate = DateUtils.parse(addSuborderForm.getValidUntil());
            } else {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }
        if (suborderFromDate != null && suborderUntilDate != null) {
            if (suborderUntilDate.isBefore(suborderFromDate)) {
                errors.add("validUntil", new ActionMessage("form.suborder.error.date.untilbeforefrom"));
            }
        }
        // check debit hours
        if (!DurationUtils.validateDuration(addSuborderForm.getDebithours())) {
            errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
        }

        if (addSuborderForm.getDebithours() != null
            && addSuborderForm.getDebithours().isEmpty()
            && !DurationUtils.parseDuration(addSuborderForm.getDebithours()).isZero()) {
            if (addSuborderForm.getDebithoursunit() == null ||
                    !(addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
                            addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR ||
                            addSuborderForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {

                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
            }
        }

        // check customer order
        Customerorder customerorder = customerorderService.getCustomerorderById(addSuborderForm.getCustomerorderId());
        if (customerorder == null) {
            errors.add("customerorder", new ActionMessage("form.suborder.error.customerorder.notfound"));
        } else {
            // check validity period
            LocalDate coFromDate = customerorder.getFromDate();
            LocalDate coUntilDate = customerorder.getUntilDate();
            if (suborderFromDate != null && coFromDate != null) {
                if (suborderFromDate.isBefore(coFromDate)) {
                    errors.add("validFrom", new ActionMessage("form.suborder.error.date.outofrange.order"));
                }
                if (!(coUntilDate == null || suborderUntilDate != null && !suborderUntilDate.isAfter(coUntilDate))) {
                    errors.add("validUntil", new ActionMessage("form.suborder.error.date.outofrange.order"));
                }
            }
        }

        // check time period for hierarchical higher suborders
        Suborder parentSuborder;
        if (addSuborderForm.getParentId() != null && addSuborderForm.getParentId() != 0 && addSuborderForm.getParentId() != -1) {
            parentSuborder = suborderService.getSuborderById(addSuborderForm.getParentId());
            if (parentSuborder != null && Objects.equals(parentSuborder.getCustomerorder().getId(), addSuborderForm.getCustomerorderId())) {
                // check validity period
                LocalDate parentFromDate = parentSuborder.getFromDate();
                LocalDate parentUntilDate = parentSuborder.getUntilDate();
                if (suborderFromDate != null && parentFromDate != null) {
                    if (suborderFromDate.isBefore(parentFromDate)) {
                        errors.add("validFrom", new ActionMessage("form.suborder.error.date.outofrange.suborder"));
                    }
                    if (!(parentUntilDate == null || suborderUntilDate != null && !suborderUntilDate.isAfter(parentUntilDate))) {
                        errors.add("validUntil", new ActionMessage("form.suborder.error.date.outofrange.suborder"));
                    }
                }
            }
        }

        // check, if dates fit to existing timereports
        request.getSession().removeAttribute("timereportsOutOfRange");
        if (suborderId != 0l) {
            var timereportsInvalidForDates = suborderService.getTimereportsNotMatchingNewSuborderOrderValidity(
                suborderId, suborderFromDate, suborderUntilDate
            );
            if (!timereportsInvalidForDates.isEmpty()) {
                request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
                errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    private void refreshForOverview(HttpServletRequest request) {
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

        boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
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
    }
}
