package org.tb.order.action;

import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedList;
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
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.service.CustomerorderService;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;

/**
 * action class for storing a customer order permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreCustomerorderAction extends LoginRequiredAction<AddCustomerorderForm> {

    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddCustomerorderForm coForm, HttpServletRequest request, HttpServletResponse response) throws IOException {
        /* remove list with timereports out of range */
        request.getSession().removeAttribute("timereportsOutOfRange");

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? coForm.getValidUntil() : coForm.getValidFrom();

            LocalDate newValue;
            if (howMuch != 0) {
                ActionMessages errorMessages = valiDate(request, coForm, which);
                if (!errorMessages.isEmpty()) {
                    return mapping.getInputForward();
                }

                newValue = DateUtils.parseOrDefault(datum, today());
                newValue = addDays(newValue, howMuch);
            } else {
                newValue = today();
            }

            datum = DateUtils.format(newValue);

            request.getSession().setAttribute(which.equals("until") ? "validUntil" : "validFrom", datum);

            if (which.equals("until")) {
                coForm.setValidUntil(datum);
            } else {
                coForm.setValidFrom(datum);
            }

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("coId") != null) {

            ActionMessages errorMessages = validateFormData(request, coForm);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            // 'main' task - prepare everything to store the customer.
            // I.e., copy properties from the form into the customerorder before saving.
            Long coId = null;
            if (request.getSession().getAttribute("coId") != null) {
                // edited customerorder
                coId = Long.parseLong(request.getSession().getAttribute("coId").toString());
            }

            LocalDate untilDate;
            if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().isEmpty()) {
                untilDate = DateUtils.parseOrNull(coForm.getValidUntil());
            } else {
                untilDate = null;
            }
            LocalDate fromDate = DateUtils.parseOrNull(coForm.getValidFrom());

            OrderType orderType = coForm.getOrderType();
            customerorderService.createOrUpdateOrder(coId, coForm.getCustomerId(), fromDate, untilDate, coForm.getSign(),
                coForm.getDescription(), coForm.getShortdescription(), coForm.getOrderCustomer(), coForm.getResponsibleCustomerContractually(), coForm.getResponsibleCustomerTechnical(), coForm.getEmployeeId(), coForm.getRespContrEmployeeId(),
                coForm.getDebithours(), coForm.getDebithoursunit(), coForm.getStatusreport(), coForm.getHide(), orderType);

            request.getSession().setAttribute("customerorders", customerorderService.getAllCustomerorders());
            request.getSession().removeAttribute("coId");

            boolean addMoreOrders = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreOrders) {

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

                boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");
                if (showActualHours) {
                    /* show actual hours */
                    List<Customerorder> customerOrders = customerorderService.getCustomerordersByFilters(show, filter, customerId);
                    List<CustomerOrderViewDecorator> decorators = new LinkedList<>();
                    for (Customerorder customerorder : customerOrders) {
                        CustomerOrderViewDecorator decorator = new CustomerOrderViewDecorator(timereportService, customerorder);
                        decorators.add(decorator);
                    }
                    request.getSession().setAttribute("customerorders", decorators);
                } else {
                    request.getSession().setAttribute("customerorders", customerorderService.getCustomerordersByFilters(show, filter, customerId));

                }

                return mapping.findForward("success");
            } else {
                // reuse form entries and show add-page
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("coId");
            coForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, coForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddCustomerorderForm coForm) {
        coForm.reset(mapping, request);
    }

    private ActionMessages valiDate(HttpServletRequest request, AddCustomerorderForm coForm, String which) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (which.equals("from")) {
            if(!DateUtils.validateDate(coForm.getValidFrom())) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        } else {
            if(!DateUtils.validateDate(coForm.getValidUntil())) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddCustomerorderForm coForm) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        //	check date formats (must now be 'yyyy-MM-dd')
        String dateFromString = coForm.getValidFrom().trim();
        boolean dateError = !DateUtils.validateDate(dateFromString);
        if (dateError) {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        if (coForm.getValidUntil() != null && !coForm.getValidUntil().trim().isEmpty()) {
            String dateUntilString = coForm.getValidUntil().trim();
            dateError = !DateUtils.validateDate(dateUntilString);
            if (dateError) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        Long coId = (Long) request.getSession().getAttribute("coId");
        // for a new customerorder, check if the sign already exists
        if (coId == null) {
            coId = 0L;
            List<Customerorder> allCustomerorders = customerorderService.getAllCustomerorders();
            for (Object element : allCustomerorders) {
                Customerorder co = (Customerorder) element;
                if (co.getSign().equalsIgnoreCase(coForm.getSign())) {
                    errors.add("sign", new ActionMessage("form.customerorder.error.sign.alreadyexists"));
                    break;
                }
            }
        }

        // check length of text fields and if they are filled
        if (coForm.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
            errors.add("sign", new ActionMessage("form.customerorder.error.sign.toolong"));
        }
        if (coForm.getSign().isEmpty()) {
            errors.add("sign", new ActionMessage("form.customerorder.error.sign.required"));
        }
        if (coForm.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
            errors.add("description", new ActionMessage("form.customerorder.error.description.toolong"));
        }
        if (coForm.getDescription().trim().isEmpty()) {
            errors.add("description", new ActionMessage("form.error.description.necessary"));
        }
        if (coForm.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
            errors.add("shortdescription", new ActionMessage("form.customerorder.error.shortdescription.toolong"));
        }
        if (coForm.getOrderCustomer().length() > GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) {
            errors.add("orderCustomer", new ActionMessage("form.customerorder.error.ordercustomer.toolong"));
        }
        if (coForm.getOrderCustomer().isEmpty()) {
            coForm.setOrderCustomer("-");
        }
        if (coForm.getResponsibleCustomerContractually().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
            errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
        }
        if (coForm.getResponsibleCustomerContractually().isEmpty()) {
            errors.add("responsibleCustomerContractually", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
        }
        if (coForm.getResponsibleCustomerTechnical().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
            errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.toolong"));
        }
        if (coForm.getResponsibleCustomerTechnical().isEmpty()) {
            errors.add("responsibleCustomerTechnical", new ActionMessage("form.customerorder.error.responsiblecustomer.required"));
        }

        if (!DurationUtils.validateDuration(coForm.getDebithours())) {
            errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.wrongformat"));
        }

        if (coForm.getDebithours() != null
            && coForm.getDebithours().isEmpty()
            && !DurationUtils.parseDuration(coForm.getDebithours()).isZero()) {
            if (coForm.getDebithoursunit() == null || !(coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH ||
                    coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || coForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
            }
        }

        // check, if dates fit to existing timereports
        LocalDate fromDate = parse(coForm.getValidFrom(), e -> {
            throw new RuntimeException(e);
        });
        LocalDate untilDate = null;
        if(coForm.getValidUntil() != null && !coForm.getValidUntil().trim().isEmpty()) {
            untilDate = parse(coForm.getValidUntil(), e -> {
                throw new RuntimeException(e);
            });
        }

        List<TimereportDTO> timereportsInvalidForDates = timereportService.getTimereportsNotMatchingNewCustomerOrderValidity(coId, fromDate, untilDate);
        if (!timereportsInvalidForDates.isEmpty()) {
            request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
            errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
        }

        saveErrors(request, errors);

        return errors;
    }

}
