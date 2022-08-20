package org.tb.order.action;

import static org.apache.struts.action.ActionMessages.GLOBAL_MESSAGE;
import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.viewhelper.EmployeeOrderViewDecorator;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

/**
 * action class for storing an employee order permanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreEmployeeorderAction extends EmployeeOrderAction<AddEmployeeOrderForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final TimereportDAO timereportDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping,
        AddEmployeeOrderForm eoForm, HttpServletRequest request,
        HttpServletResponse response) {

        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshEmployee")) {

            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
            if (employeecontract == null) {
                return mapping.findForward("error");
            } else {
                request.getSession().setAttribute("currentEmployeeContract", employeecontract);
                setFormDates(request, eoForm);
                return mapping.getInputForward();
            }
        }

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? eoForm.getValidUntil() : eoForm.getValidFrom();

            LocalDate newValue;
            if (howMuch != 0) {
                ActionMessages errorMessages = valiDate(request, eoForm, which);
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
                eoForm.setValidUntil(datum);
            } else {
                eoForm.setValidFrom(datum);
            }

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshSuborders")) {
            // refresh suborders to be displayed in the select menu:
            // get suborders related to selected customer order...
            // remove selection - displayed info would be false, if an error occurs
            request.getSession().removeAttribute("selectedcustomerorder");
            request.getSession().removeAttribute("selectedsuborder");
            long coId = eoForm.getOrderId();
            Customerorder co = customerorderDAO.getCustomerorderById(coId);
            if (co == null) {
                return mapping.findForward("error");
            } else {
                List<Suborder> suborders = co.getSuborders();
                // remove hidden suborders (and invalid suborders, if a flag is set)
                Iterator<Suborder> suborderIterator = suborders.iterator();
                while (suborderIterator.hasNext()) {
                    Suborder suborder = suborderIterator.next();
                    if (suborder.isHide()) {
                        suborderIterator.remove();
                    } else if (eoForm.getShowOnlyValid() && !suborder.getCurrentlyValid()) {
                        suborderIterator.remove();
                    }
                }
                request.getSession().setAttribute("suborders", suborders);

                /* suggest value */
                eoForm.setDebithoursunit((byte) -1); // default: no unit set
                Optional<Suborder> so = co.getSuborders().stream().findFirst();
                if (so.isPresent()) {
                    Suborder suborder = so.get();
                    eoForm.setSuborderId(suborder.getId());
                    request.getSession().setAttribute("selectedsuborder", suborder);
                    if (suborder.getDebithours() != null && suborder.getDebithours().toMinutes() > 0) {
                        eoForm.setDebithours(DurationUtils.format(suborder.getDebithours()));
                        /* set unit if applicable */
                        eoForm.setDebithoursunit(suborder.getDebithoursunit());
                    } else {
                        eoForm.setDebithours(null);
                        eoForm.setDebithoursunit(null);
                    }
                }

                request.getSession().setAttribute("selectedcustomerorder", co);
                eoForm.useDatesFromCustomerOrder(co);
                eoForm.setOrderId(co.getId());
                request.getSession().setAttribute("currentOrderId", co.getId());
                setFormDates(request, eoForm);

                return mapping.getInputForward();
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("refreshSuborderDescription")) {
            // remove selection - displayed info would be false, if an error
            // occurs
            request.getSession().removeAttribute("selectedsuborder");
            long soId = eoForm.getSuborderId();
            Suborder so = suborderDAO.getSuborderById(soId);
            if (so != null) {
                request.getSession().setAttribute("selectedsuborder", so);
                eoForm.setSuborderId(so.getId());

                if (so.getDebithours() != null && so.getDebithours().toMinutes() > 0) {
                    eoForm.setDebithours(DurationUtils.format(so.getDebithours()));
                    /* set unit if applicable */
                    eoForm.setDebithoursunit(so.getDebithoursunit());
                } else {
                    eoForm.setDebithours(null);
                    eoForm.setDebithoursunit(null);
                }
            }
            // checkDatabaseForEmployeeOrder(request, eoForm,
            // employeecontractDAO, employeeorderDAO);
            setFormDates(request, eoForm);

            return mapping.getInputForward();
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("save") || request.getParameter("eoId") != null) {
            // 'main' task - prepare everything to store the employee order.
            // I.e., copy properties from the form into the employee order
            // before saving.
            long eoId;

            Employeeorder eo;
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
            long employeeContractId;

            if (request.getSession().getAttribute("eoId") != null) {
                // edited employeeorder
                eoId = Long.parseLong(request.getSession().getAttribute("eoId").toString());
                eo = employeeorderDAO.getEmployeeorderById(eoId);
            } else {
                eo = new Employeeorder();
            }

            ActionMessages errorMessages = validateFormData(request, eoForm, employeeorderDAO, employeecontractDAO, suborderDAO, eo);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            request.getSession().setAttribute("currentEmployeeContract", employeecontract);

            eo.setEmployeecontract(employeecontract);
            eo.setSuborder(suborderDAO.getSuborderById(eoForm.getSuborderId()));

            LocalDate fromDate = DateUtils.parseOrNull(eoForm.getValidFrom());

            if (eoForm.getValidUntil() == null || eoForm.getValidUntil().trim().isEmpty()) {
                eo.setUntilDate(null);
            } else {
                LocalDate untilDate = DateUtils.parseOrNull(eoForm.getValidUntil());
                eo.setUntilDate(untilDate);
            }
            eo.setFromDate(fromDate);
            eo.setSign(eoForm.getSign());

            if (eo.getSuborder().getCustomerorder().getSign().equals(
                    GlobalConstants.CUSTOMERORDER_SIGN_VACATION)
                    && !eo.getSuborder().getSign().equalsIgnoreCase(GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {

                if (authorizedUser.isAdmin()) {
                    if (eoForm.getDebithours() == null
                        || eoForm.getDebithours().isEmpty()
                        || DurationUtils.parseDuration(eoForm.getDebithours()).isZero()) {
                        eo.setDebithours(Duration.ZERO);
                        eo.setDebithoursunit(null);
                    } else {
                        eo.setDebithours(DurationUtils.parseDuration(eoForm.getDebithours()));
                        eo.setDebithoursunit(eoForm.getDebithoursunit());
                    }
                } else {
                    ActionMessages errors = new ActionMessages();
                    errors.add(GLOBAL_MESSAGE, new ActionMessage("common.error.admin.required"));
                    saveErrors(request, errors);
                    return mapping.getInputForward();
                }

            } else if (eo.getSuborder().getCustomerorder().getSign().equals(
                    GlobalConstants.CUSTOMERORDER_SIGN_ILL)) {
                eo.setDebithours(Duration.ZERO);
                eo.setDebithoursunit(null);
            } else {
                if (eoForm.getDebithours() == null
                    || eoForm.getDebithours().isEmpty()
                    || DurationUtils.parseDuration(eoForm.getDebithours()).isZero()) {
                    eo.setDebithours(Duration.ZERO);
                    eo.setDebithoursunit(null);
                } else {
                    eo.setDebithours(DurationUtils.parseDuration(eoForm.getDebithours()));
                    eo.setDebithoursunit(eoForm.getDebithoursunit());
                }
            }

            employeeorderDAO.save(eo);

            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            long orderId = (Long) request.getSession().getAttribute("currentOrderId");

            if (employeecontract == null) {
                employeeContractId = -1;
            } else {
                employeeContractId = employeecontract.getId();
            }

            String filter = null;
            Boolean show = null;

            if (request.getSession().getAttribute("employeeOrderFilter") != null) {
                filter = (String) request.getSession().getAttribute("employeeOrderFilter");
            }
            if (request.getSession().getAttribute("employeeOrderShow") != null) {
                show = (Boolean) request.getSession().getAttribute("employeeOrderShow");
            }

            boolean showActualHours = (Boolean) request.getSession().getAttribute("showActualHours");

            if (showActualHours) {
                /* show actual hours */
                List<Employeeorder> employeeOrders = employeeorderDAO
                        .getEmployeeordersByFilters(show, filter, employeeContractId, orderId);
                List<EmployeeOrderViewDecorator> decorators = new LinkedList<>();

                for (Employeeorder employeeorder : employeeOrders) {
                    EmployeeOrderViewDecorator decorator = new EmployeeOrderViewDecorator(timereportDAO, employeeorder);
                    decorators.add(decorator);
                }
                request.getSession().setAttribute("employeeorders", decorators);
            } else {
                request.getSession().setAttribute("employeeorders",
                        employeeorderDAO.getEmployeeordersByFilters(show, filter, employeeContractId, orderId));
            }

            request.getSession().removeAttribute("eoId");

            boolean addMoreOrders = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreOrders) {
                return mapping.findForward("success");
            } else {
                // reuse current input of the form and show add-page
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("eoId");
            doResetActions(mapping, request, eoForm);
            // eoForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null && request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, eoForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping,
                                HttpServletRequest request, AddEmployeeOrderForm eoForm) {
        eoForm.reset(mapping, request, true);
        long coId = eoForm.getOrderId();
        Customerorder co = customerorderDAO.getCustomerorderById(coId);
        eoForm.useDatesFromCustomerOrder(co);
    }

    private ActionMessages valiDate(HttpServletRequest request, AddEmployeeOrderForm eoForm, String which) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        String dateString;
        if (which.equals("from")) {
            dateString = eoForm.getValidFrom().trim();
        } else {
            dateString = eoForm.getValidUntil().trim();
        }

        int minus = 0;
        for (int i = 0; i < dateString.length(); i++) {
            if (dateString.charAt(i) == '-') {
                minus++;
            }
        }
        if (dateString.length() != 10 || minus != 2) {
            if (which.equals("from")) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            } else {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request,
                                            AddEmployeeOrderForm eoForm, EmployeeorderDAO employeeorderDAO,
                                            EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
                                            Employeeorder employeeorder) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        LocalDate validFromDate = null;
        if(validateDate(eoForm.getValidFrom())) {
            validFromDate = parse(eoForm.getValidFrom());
        } else {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }
        LocalDate validUntilDate = null;
        if (eoForm.getValidUntil() != null && !eoForm.getValidUntil().equals("".trim())) {
            if(validateDate(eoForm.getValidUntil())) {
                validUntilDate = parse(eoForm.getValidUntil());
            } else {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }
        if(!errors.isEmpty()) {
            return errors; // further validation not possible
        }

        // check if begin is before end
        if (validFromDate != null && validUntilDate != null) {
            if (validUntilDate.isBefore(validFromDate)) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.endbeforebegin"));
            }
        }

        // check if valid suborder exists - otherwise, no save possible
        if (eoForm.getSuborderId() <= 0) {
            errors.add("suborderId", new ActionMessage("form.employeeorder.suborder.invalid"));
        }

        // check debit hours format

        // taking customerorder from request instead of database
        // fast and not as secure as databaseaccess, but should do it

        Customerorder co = (Customerorder) request.getSession().getAttribute("selectedcustomerorder");

        if (co != null && !co.getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_VACATION) && !co.getSign().equals(GlobalConstants.CUSTOMERORDER_SIGN_ILL)) {
            if (!DurationUtils.validateDuration(eoForm.getDebithours())) {
                errors.add("debithours", new ActionMessage("form.employeeorder.error.debithours.wrongformat"));
            }
        }

        if (eoForm.getDebithours() != null
            && eoForm.getDebithours().isEmpty()
            && !DurationUtils.parseDuration(eoForm.getDebithours()).isZero()) {
            if (eoForm.getDebithoursunit() == null
                    || !(eoForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_MONTH
                    || eoForm.getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_YEAR || eoForm
                    .getDebithoursunit() == GlobalConstants.DEBITHOURS_UNIT_TOTALTIME)) {
                errors.add("debithours", new ActionMessage("form.customerorder.error.debithours.nounit"));
            }
        }

        // set dates to given empoyee contract to allow validation
        employeeorder.setFromDate(validFromDate);
        employeeorder.setUntilDate(validUntilDate);

        // check for overleap with another employee order for the same employee
        // contract and suborder
        List<Employeeorder> employeeOrders = employeeorderDAO
                .getEmployeeOrdersByEmployeeContractIdAndSuborderId(eoForm
                        .getEmployeeContractId(), eoForm.getSuborderId());

        if (employeeOrders != null && !employeeOrders.isEmpty()) {
            if (validFromDate != null) {
                for (Employeeorder compareEmployeeorder : employeeOrders) {
                    if (!Objects.equals(employeeorder.getId(), compareEmployeeorder.getId())) {
                        if(employeeorder.overlaps(compareEmployeeorder)) {
                            errors.add("overleap", new ActionMessage("form.employeeorder.error.overleap"));
                            break;
                        }
                    }
                }
            }
        }
        // check if dates fit to employee contract and suborder
        // TODO
        if (validFromDate != null) {
            Employeecontract ec = employeecontractDAO.getEmployeeContractById(eoForm.getEmployeeContractId());
            Suborder suborder = suborderDAO.getSuborderById(eoForm.getSuborderId());
            if (validFromDate.isBefore(ec.getValidFrom())) {
                errors.add("validFrom", new ActionMessage("form.employeeorder.error.date.outofrange.employeecontract"));
            }
            if (validFromDate.isBefore(suborder.getFromDate())) {
                errors.add("validFrom", new ActionMessage("form.employeeorder.error.date.outofrange.suborder"));
            }
            if (validUntilDate == null && ec.getValidUntil() != null || validUntilDate != null
                    && ec.getValidUntil() != null && validUntilDate.isAfter(ec.getValidUntil())) {
                errors.add("validUntil", new ActionMessage("form.employeeorder.error.date.outofrange.employeecontract"));
            }
            if (validUntilDate == null && suborder.getUntilDate() != null || validUntilDate != null
                    && suborder.getUntilDate() != null && validUntilDate.isAfter(suborder.getUntilDate())) {
                errors.add("validUntil", new ActionMessage("form.employeeorder.error.date.outofrange.suborder"));
            }
        }

        if (!employeeorder.isNew() && validFromDate != null) {
            // check, if dates fit to existing timereports
            List<TimereportDTO> timereportsInvalidForDates = timereportDAO
                    .getTimereportsByEmployeeorderIdInvalidForDates(validFromDate, validUntilDate, employeeorder.getId());
            if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
                request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
                errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));
            }
        }
        saveErrors(request, errors);

        return errors;
    }

}
