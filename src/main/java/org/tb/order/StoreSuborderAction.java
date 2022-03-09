package org.tb.order;

import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.Timereport;
import org.tb.dailyreport.TimereportDAO;
import org.tb.employee.domain.Employee;

/**
 * action class for storing a suborder permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreSuborderAction extends LoginRequiredAction<AddSuborderForm> {
    private static final Logger LOG = LoggerFactory.getLogger(StoreSuborderAction.class);

    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;

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
                newValue = addDays(newValue, 1);
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
            Suborder so = suborderDAO.getSuborderById(soId);

            if (so != null) {
                Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
                Suborder copy = so.copy(true, loginEmployee.getSign());

                suborderDAO.save(copy, loginEmployee);

                request.getSession().removeAttribute("soId");

                // store used customer order id for the next creation of a suborder
                request.getSession().setAttribute("lastCoId", so.getCustomerorder().getId());
            }

            refreshForOverview(request);

            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("fitDates")) {

            if (request.getSession().getAttribute("soId") != null) {

                long soId = Long.parseLong(request.getSession().getAttribute("soId").toString());
                Suborder suborder = suborderDAO.getSuborderById(soId);

                if (suborder != null) {

                    Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

                    for (Suborder tempSuborder : suborder.getAllChildren()) {
                        tempSuborder.setFromDate(suborder.getFromDate());
                        tempSuborder.setUntilDate(suborder.getUntilDate());
                    }
                    suborderDAO.save(suborder, loginEmployee);
                }
            }
            return mapping.findForward("success");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("generateSign")) {
            //*** task for generating new suborder's sign
            Suborder tempSubOrder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
            Customerorder tempOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getParentId());
            List<Suborder> suborders = suborderDAO.getSuborders(false);
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

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshParentProject")) {

            Customerorder parentOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());

            addSuborderForm.setParentDescriptionAndSign(parentOrder.getSignAndDescription());
            addSuborderForm.setParentId(addSuborderForm.getCustomerorderId());

            request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
            request.getSession().setAttribute("suborderParent", parentOrder);

            if (request.getParameter("continue") != null) {
                try {
                    addSuborderForm.setParentId(Long.parseLong(request.getParameter("continue")));
                    Suborder tempSubOrder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
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
                        Customerorder tempOrder = customerorderDAO.getCustomerorderById(addSuborderForm.getParentId());
                        addSuborderForm.setParentDescriptionAndSign(tempOrder.getSignAndDescription());
                        request.getSession().setAttribute("suborderParent", tempOrder);
                    }
                    request.getSession().setAttribute("parentDescriptionAndSign", addSuborderForm.getParentDescriptionAndSign());
                } catch (Throwable th) {
                    return mapping.findForward("error");
                }
            }

            return mapping.getInputForward();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("save") || request.getParameter("soId") != null) {
            //*** task for saving new suborder
            ActionMessages errorMessages = validateFormData(request, addSuborderForm);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            // 'main' task - prepare everything to store the suborder.
            // I.e., copy properties from the form into the suborder before
            // saving.
            long soId;
            Suborder so;
            Customerorder customerorder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());

            if (request.getSession().getAttribute("soId") != null) {
                // edited suborder
                soId = Long.parseLong(request.getSession().getAttribute("soId").toString());
                so = suborderDAO.getSuborderById(soId);

                if (so.getSuborders() != null
                    && !so.getSuborders().isEmpty()
                    && !Objects.equals(so.getCustomerorder().getId(), customerorder.getId())) {
                    // set customerorder in all descendants					
                    so.setCustomerOrderForAllDescendants(customerorder, suborderDAO, loginEmployee, so);
                }
                so = suborderDAO.getSuborderById(soId);
            } else {
                // new report
                so = new Suborder();
            }
            so.setCustomerorder(customerorder);
            so.setSign(addSuborderForm.getSign());
            so.setSuborder_customer(addSuborderForm.getSuborder_customer());
            so.setDescription(addSuborderForm.getDescription());
            so.setShortdescription(addSuborderForm.getShortdescription());
            so.setInvoice(addSuborderForm.getInvoice());
            so.setStandard(addSuborderForm.getStandard());
            so.setCommentnecessary(addSuborderForm.getCommentnecessary());
            so.setFixedPrice(addSuborderForm.getFixedPrice());
            so.setTrainingFlag(addSuborderForm.getTrainingFlag());

            if (addSuborderForm.getValidFrom() != null && !addSuborderForm.getValidFrom().trim().equals("")) {
                LocalDate fromDate = DateUtils.parseOrNull(addSuborderForm.getValidFrom());
                so.setFromDate(fromDate);
            } else {
                so.setFromDate(so.getCustomerorder().getFromDate());
            }
            if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().equals("")) {
                LocalDate untilDate = DateUtils.parseOrNull(addSuborderForm.getValidUntil());
                so.setUntilDate(untilDate);
            } else {
                so.setUntilDate(null);
            }

            // adjust employeeorders
            if(!so.isNew()) {
                List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersBySuborderId(so.getId());
                if (employeeorders != null && !employeeorders.isEmpty()) {
                    for (Employeeorder employeeorder : employeeorders) {
                        boolean changed = false;
                        if (employeeorder.getFromDate().isBefore(so.getFromDate())) {
                            employeeorder.setFromDate(so.getFromDate());
                            changed = true;
                        }
                        if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().isBefore(so.getFromDate())) {
                            employeeorder.setUntilDate(so.getFromDate());
                            changed = true;
                        }
                        if (so.getUntilDate() != null) {
                            if (employeeorder.getFromDate().isAfter(so.getUntilDate())) {
                                employeeorder.setFromDate(so.getUntilDate());
                                changed = true;
                            }
                            if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().isAfter(so.getUntilDate())) {
                                employeeorder.setUntilDate(so.getUntilDate());
                                changed = true;
                            }
                        }
                        if (changed) {
                            employeeorderDAO.save(employeeorder, loginEmployee);
                        }
                    }
                }
            }

            if (addSuborderForm.getDebithours() == null
                || addSuborderForm.getDebithours().isEmpty()
                || DurationUtils.parseDuration(addSuborderForm.getDebithours()).isZero()) {
                so.setDebithours(null);
                so.setDebithoursunit(null);
            } else {
                so.setDebithours(DurationUtils.parseDuration(addSuborderForm.getDebithours()));
                so.setDebithoursunit(addSuborderForm.getDebithoursunit());
            }

            so.setHide(addSuborderForm.getHide());
            Suborder parentOrderCandidate = suborderDAO.getSuborderById(addSuborderForm.getParentId());
            // Falls die Suborder nicht zum Customerorder passt (Kollision der IDs), ist sie kein geeigneter Kandidat (HACK, da UI die ID manchmal auch mit CustomerOrderID besetzt)
            if (parentOrderCandidate != null && parentOrderCandidate.getCustomerorder().getId() != addSuborderForm.getCustomerorderId()) {
                if (!addSuborderForm.getParentId().equals(addSuborderForm.getCustomerorderId())) {
                    throw new IllegalStateException("parentId is neither a valid suborderId nor the customerorderId, but: " + addSuborderForm.getParentId());
                }
                parentOrderCandidate = null;
            }
            so.setParentorder(parentOrderCandidate);

            suborderDAO.save(so, loginEmployee);

            request.getSession().removeAttribute("soId");

            // store used customer order id for the next creation of a suborder
            request.getSession().setAttribute("lastCoId",
                    so.getCustomerorder().getId());

            boolean addMoreSuborders = Boolean.parseBoolean(request.getParameter("continue"));

            if (!addMoreSuborders) {
                refreshForOverview(request);
                return mapping.findForward("success");
            } else {
                request.getSession().setAttribute("suborders", suborderDAO.getSuborders(false));
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
                suborders = suborderDAO.getSubordersByCustomerorderId(addSuborderForm.getCustomerorderId(), false);
                for (Suborder suborder : suborders) {
                    if (suborder.getCurrentlyValid()
                            && suborder.getParentorder() == null // vergleiche nur Suborder direkt unter der Customerorder
                            && suborder.getSign().equalsIgnoreCase(addSuborderForm.getSign())) {
                        errors.add("sign", new ActionMessage("form.suborder.error.sign.alreadyexists"));
                        break;
                    }
                }
            } else {
                suborders = suborderDAO.getSuborderChildren(addSuborderForm.getParentId());
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
        if (addSuborderForm.getSign().length() <= 0) {
            errors.add("sign", new ActionMessage("form.suborder.error.sign.required"));
        }
        if (addSuborderForm.getDescription().length() > GlobalConstants.SUBORDER_DESCRIPTION_MAX_LENGTH) {
            errors.add("description", new ActionMessage("form.suborder.error.description.toolong"));
        }
        if ("".equals(addSuborderForm.getDescription().trim())) {
            errors.add("description", new ActionMessage("form.error.description.necessary"));
        }
        if (addSuborderForm.getShortdescription().length() > GlobalConstants.SUBORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
            errors.add("shortdescription", new ActionMessage("form.suborder.error.shortdescription.toolong"));
        }
        if (addSuborderForm.getSuborder_customer().length() > GlobalConstants.SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH) {
            errors.add("suborder_customer", new ActionMessage("form.suborder.error.suborder_customer.toolong"));
        }
        // check invoice character
        if (addSuborderForm.getInvoice() != GlobalConstants.SUBORDER_INVOICE_YES
                && addSuborderForm.getInvoice() != GlobalConstants.SUBORDER_INVOICE_NO
                && addSuborderForm.getInvoice() != GlobalConstants.SUBORDER_INVOICE_UNDEFINED) {

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
        if (addSuborderForm.getValidUntil() != null && !addSuborderForm.getValidUntil().trim().equals("")) {
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
        Customerorder customerorder = customerorderDAO.getCustomerorderById(addSuborderForm.getCustomerorderId());
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
        Suborder parentSuborder = null;
        if (addSuborderForm.getParentId() != null && addSuborderForm.getParentId() != 0 && addSuborderForm.getParentId() != -1) {
            parentSuborder = suborderDAO.getSuborderById(addSuborderForm.getParentId());
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
        List<Timereport> timereportsInvalidForDates;
        if (suborderId != 0l) {
            Suborder suborder = suborderDAO.getSuborderById(suborderId);
            timereportsInvalidForDates = suborder.getAllTimeReportsInvalidForDates(suborderFromDate, suborderUntilDate, timereportDAO);
        } else {
            timereportsInvalidForDates = timereportDAO.getTimereportsBySuborderIdInvalidForDates(suborderFromDate, suborderUntilDate, suborderId);
        }
        if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
            request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
            errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
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
            List<Suborder> suborders = suborderDAO.getSubordersByFilters(show, filter, customerOrderId);
            List<SuborderViewDecorator> suborderViewDecorators = new LinkedList<>();
            for (Suborder suborder : suborders) {
                SuborderViewDecorator decorator = new SuborderViewDecorator(timereportDAO, suborder);
                suborderViewDecorators.add(decorator);
            }
            request.getSession().setAttribute("suborders", suborderViewDecorators);
        } else {
            request.getSession().setAttribute("suborders", suborderDAO.getSubordersByFilters(show, filter, customerOrderId));
        }
    }
}
