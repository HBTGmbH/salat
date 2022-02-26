package org.tb.employee;

import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DurationUtils.format;
import static org.tb.common.util.DurationUtils.parse;
import static org.tb.common.util.DurationUtils.validate;

import java.text.ParseException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.Timereport;
import org.tb.dailyreport.TimereportDAO;
import org.tb.dailyreport.Vacation;
import org.tb.dailyreport.VacationDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;

/**
 * action class for storing an employee contractpermanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreEmployeecontractAction extends LoginRequiredAction<AddEmployeeContractForm> {

    private final EmployeeDAO employeeDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final VacationDAO vacationDAO;
    private final OvertimeDAO overtimeDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeorderDAO employeeorderDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeContractForm ecForm, HttpServletRequest request, HttpServletResponse response) {
        //			 remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? ecForm.getValidUntil() : ecForm.getValidFrom();

            LocalDate newValue;
            if (howMuch != 0) {
                ActionMessages errorMessages = validateDate(request, ecForm, which);
                if (errorMessages.size() > 0) {
                    return mapping.getInputForward();
                }

                newValue = DateUtils.parseOrDefault(datum, today());
                newValue = addDays(newValue, 1);
            } else {
                newValue = today();
            }

            datum = DateUtils.format(newValue);

            request.getSession().setAttribute(which.equals("until") ? "validUntil" : "validFrom", datum);

            if (which.equals("until")) {
                ecForm.setValidUntil(datum);
            } else {
                ecForm.setValidFrom(datum);
            }

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("storeOvertime") ||
                request.getParameter("ecId") != null) {

            // check form entries
            ActionMessages errors = getErrors(request);
            if (errors == null) {
                errors = new ActionMessages();
            }

            // new overtime
            double overtimeDouble = 0.0;
            if (ecForm.getNewOvertime() != null) {
                String overtimeString = ecForm.getNewOvertime();

                // validate comment
                if (ecForm.getNewOvertimeComment().length() > GlobalConstants.EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH) {
                    errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.toolong"));
                } else if (ecForm.getNewOvertimeComment().trim().length() == 0) {
                    errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.missing"));
                }
                if(!validate(overtimeString)) {
                    errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
                } else {
                    Duration overtimeMinutes = parse(overtimeString);
                    if(overtimeMinutes.isZero()) {
                        errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
                    }
                }
                if (errors.size() > 0) {
                    saveErrors(request, errors);
                    //setFormEntries(request, ecForm, ec); // warum????
                    return mapping.getInputForward();
                }
            }

            // validation completed -> create overtime entity and store it
            long ecId = Long.parseLong(request.getSession().getAttribute("ecId").toString());
            Employeecontract ec = employeecontractDAO.getEmployeeContractById(ecId);

            Overtime overtime = new Overtime();
            overtime.setComment(ecForm.getNewOvertimeComment());
            overtime.setEmployeecontract(ec);
            overtime.setTimeMinutes(parse(ecForm.getNewOvertime()));

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            overtimeDAO.save(overtime, loginEmployee);

            // refresh list of overtime adjustments
            List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecId);
            Duration totalOvertime = Duration.ZERO;
            for (Overtime ot : overtimes) {
                totalOvertime = totalOvertime.plus(ot.getTimeMinutes());
            }

            request.getSession().setAttribute("overtimes", overtimes);
            request.getSession().setAttribute("totalovertime", format(totalOvertime));

            // reset form
            ecForm.setNewOvertime("0:00");
            ecForm.setNewOvertimeComment("");

            setFormEntries(request, ecForm, ec);

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("ecId") != null) {

            //	'main' task - prepare everything to store the employee contract.
            // I.e., copy properties from the form into the employee contract before saving.
            long ecId;
            Employeecontract ec = null;
            long employeeId = ecForm.getEmployee();
            if (request.getSession().getAttribute("ecId") != null) {
                // edited employeecontract
                ecId = Long.parseLong(request.getSession().getAttribute("ecId").toString());
                ec = employeecontractDAO.getEmployeeContractById(ecId);
                if (ec != null) {
                    employeeId = ec.getEmployee().getId();
                }
            }
            boolean newContract = false;
            if (ec == null) {
                // new employee contract
                ec = new Employeecontract();
                newContract = true;
            }

            Employee theEmployee = employeeDAO.getEmployeeById(employeeId);
            ec.setEmployee(theEmployee);

            ActionMessages errorMessages = validateFormData(request, ecForm, theEmployee, ec);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            if (ecForm.getValidUntil() != null && !ecForm.getValidUntil().trim().equals("")) {
                LocalDate untilDate = DateUtils.parseOrNull(ecForm.getValidUntil());
                ec.setValidUntil(untilDate);
            } else {
                ec.setValidUntil(null);
            }

            LocalDate fromDate = DateUtils.parseOrNull(ecForm.getValidFrom());
            ec.setValidFrom(fromDate);

            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

            // adjust employeeorders
            List<Employeeorder> employeeorders = employeeorderDAO.getEmployeeOrdersByEmployeeContractId(ec.getId());
            if (employeeorders != null && !employeeorders.isEmpty()) {
                for (Employeeorder employeeorder : employeeorders) {
                    boolean changed = false;
                    if (employeeorder.getFromDate().isBefore(fromDate)) {
                        employeeorder.setFromDate(fromDate);
                        changed = true;
                    }
                    if (employeeorder.getUntilDate() != null && employeeorder.getUntilDate().isBefore(fromDate)) {
                        employeeorder.setUntilDate(fromDate);
                        changed = true;
                    }
                    // if enddate of employeecontract is set, check dates of corresponding employeeorders and adjust as needed
                    if (ec.getValidUntil() != null) {
                        if (employeeorder.getFromDate().isAfter(ec.getValidUntil())) {
                            employeeorder.setFromDate(ec.getValidUntil());
                            changed = true;
                        }
                        if (employeeorder.getUntilDate() == null || employeeorder.getUntilDate().isAfter(ec.getValidUntil())) {
                            employeeorder.setUntilDate(ec.getValidUntil());
                            changed = true;
                        }
                        if (changed) {
                            employeeorderDAO.save(employeeorder, loginEmployee);
                        }
                    }
                }

                // remove all employeeorders with duplicate suborders 
                // (needed due to previous bug that contract duration extensions produced new automatic entries of standard employeeorders 
                // instead of extending the existing ones)
                Set<Long> suborderIDs = new HashSet<>();
                Iterator<Employeeorder> iterator = employeeorders.iterator();
                while (iterator.hasNext()) {
                    Employeeorder eo = iterator.next();
                    if (!suborderIDs.contains(eo.getSuborder().getId())) {
                        suborderIDs.add(eo.getSuborder().getId());
                    } else {
                        iterator.remove();
                    }
                }
                for (Employeeorder employeeorder : employeeorders) {
                    // cases where enddate employeeorder < enddate employeecontract (or employeeorder has enddate, employeecontract does not have enddate).
                    // if enddate of suborder is earlier than enddate of employeecontract 
                    // set enddate of employeeorder to enddate of suborder, else to enddate of employeecontract.
                    if (employeeorder.getSuborder().getUntilDate() != null && ec.getValidUntil() != null
                            && employeeorder.getSuborder().getUntilDate().isBefore(ec.getValidUntil())
                            || employeeorder.getSuborder().getUntilDate() != null && ec.getValidUntil() == null) {
                        if (!employeeorder.getSuborder().getUntilDate().equals(employeeorder.getUntilDate())) {
                            employeeorder.setUntilDate(employeeorder.getSuborder().getUntilDate());
                            employeeorderDAO.save(employeeorder, loginEmployee);
                        }
                    } else if (employeeorder.getUntilDate() != null && ec.getValidUntil() != null && employeeorder.getUntilDate().isBefore(ec.getValidUntil())) {
                        employeeorder.setUntilDate(ec.getValidUntil());
                        employeeorderDAO.save(employeeorder, loginEmployee);
                    } else if (employeeorder.getUntilDate() != null && ec.getValidUntil() == null) {
                        employeeorder.setUntilDate(null);
                        employeeorderDAO.save(employeeorder, loginEmployee);
                    }
                }
            }

            /*  Supervisor validation */
            if (ecForm.getSupervisorid() == employeeId) {
                ActionMessages errors = getErrors(request);
                if (errors == null) {
                    errors = new ActionMessages();
                }
                errors.add("invalidSupervisor", new ActionMessage("form.timereport.error.employeecontract.invalidsupervisor"));
                saveErrors(request, errors);
                return mapping.getInputForward();
            } else {
                ec.setSupervisor(employeeDAO.getEmployeeById(ecForm.getSupervisorid()));
            }

            ec.setTaskDescription(ecForm.getTaskdescription());
            ec.setFreelancer(ecForm.getFreelancer());
            ec.setHide(ecForm.getHide());
            ec.setDailyWorkingTime(ecForm.getDailyworkingtime());

            // if necessary, add new vacation for current year
            Vacation va = null;
            if (ec.getVacations() == null || ec.getVacations().size() <= 0) {
                List<Vacation> vaList = new ArrayList<>();
                va = vacationDAO.setNewVacation(ec, DateUtils.getCurrentYear());
                va.setEntitlement(ecForm.getYearlyvacation());
                vaList.add(va);
                ec.setVacations(vaList);
            } else {
                for (Object element : ec.getVacations()) {
                    va = (Vacation) element;
                    va.setEntitlement(ecForm.getYearlyvacation());
                }
            }

            employeecontractDAO.save(ec, loginEmployee);

            if (newContract) {
                Overtime overtime = new Overtime();
                overtime.setComment("initial overtime");
                overtime.setEmployeecontract(ec);
                // if no value is selected, set 0.0
                if (ecForm.getInitialOvertime() == null) {
                    ecForm.setInitialOvertime("0:00");
                }
                // the ecForm entry is checked before
                overtime.setTimeMinutes(parse(ecForm.getInitialOvertime()));
                overtimeDAO.save(overtime, loginEmployee);
            }

            request.getSession().setAttribute("currentEmployee", employeeDAO.getEmployeeById(ecForm.getEmployee()).getName());
            request.getSession().setAttribute("currentEmployeeId", ecForm.getEmployee());

            List<Employee> employeeOptionList = employeeDAO.getEmployees();
            request.getSession().setAttribute("employees", employeeOptionList);

            request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContracts());
            request.getSession().removeAttribute("ecId");

            boolean addMoreContracts = Boolean.parseBoolean(request.getParameter("continue"));
            if (!addMoreContracts) {

                String filter = null;
                Boolean show = null;
                Long filterEmployeeId = null;

                if (request.getSession().getAttribute("employeeContractFilter") != null) {
                    filter = (String) request.getSession().getAttribute("employeeContractFilter");
                }
                if (request.getSession().getAttribute("employeeContractShow") != null) {
                    show = (Boolean) request.getSession().getAttribute("employeeContractShow");
                }
                if (request.getSession().getAttribute("employeeContractEmployeeId") != null) {
                    filterEmployeeId = (Long) request.getSession().getAttribute("employeeContractEmployeeId");
                }

                request.getSession().setAttribute("employeecontracts", employeecontractDAO.getEmployeeContractsByFilters(show, filter, filterEmployeeId));

                return mapping.findForward("success");
            } else {
                // set context
                request.getSession().setAttribute("employeeContractContext", "create");
                // reuse current input of the form and show add-page
                return mapping.findForward("reset");
            }
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("back")) {
            // go back
            request.getSession().removeAttribute("ecId");
            ecForm.reset(mapping, request);
            return mapping.findForward("cancel");
        }
        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("reset")) {
            // reset form
            doResetActions(mapping, request, ecForm);
            return mapping.getInputForward();
        }

        return mapping.findForward("error");

    }

    /**
     * resets the 'add report' form to default values
     */
    private void doResetActions(ActionMapping mapping, HttpServletRequest request, AddEmployeeContractForm ecForm) {
        ecForm.reset(mapping, request);
    }

    private ActionMessages validateDate(HttpServletRequest request, AddEmployeeContractForm ecForm, String which) {
        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (which.equals("from")) {
            if(DateUtils.validateDate(ecForm.getValidFrom())) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        } else {
            if(DateUtils.validateDate(ecForm.getValidUntil())) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        }

        saveErrors(request, errors);
        return errors;
    }

    /**
     * validates the form data (syntax and logic)
     */
    private ActionMessages validateFormData(HttpServletRequest request, AddEmployeeContractForm ecForm,
                                            Employee theEmployee, Employeecontract employeecontract) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        // check date formats (must now be 'yyyy-MM-dd')
        String dateFromString = ecForm.getValidFrom().trim();
        boolean dateValid = DateUtils.validateDate(dateFromString);
        if (!dateValid) {
            errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        String dateUntilString = ecForm.getValidUntil().trim();
        if (!dateUntilString.equals("")) {
            dateValid = DateUtils.validateDate(dateUntilString);
            if (!dateValid) {
                errors.add("validUntil", new ActionMessage(
                        "form.timereport.error.date.wrongformat"));
            }
        }
        java.time.LocalDate newContractValidFrom;
        java.time.LocalDate newContractValidUntil = null;
        try {
            newContractValidFrom = DateUtils.parse(dateFromString);
            if (!dateUntilString.equals("")) {
                newContractValidUntil = DateUtils.parse(dateUntilString);
            }
        } catch (ParseException e) {
            // this is not expected...
            throw new RuntimeException("LocalDate cannot be parsed - fatal error!");
        }

        if (newContractValidUntil != null && newContractValidFrom.isAfter(newContractValidUntil)) {
            errors.add("validFrom", new ActionMessage("form.employeecontract.error.endbeforebegin"));
        }

        // for a new employeecontract, check if other contract for this employee already exists
        Long ecId = (Long) request.getSession().getAttribute("ecId");
        if (ecId == null) {
            List<Employeecontract> allEmployeecontracts = employeecontractDAO.getEmployeeContracts();
            for (Object element : allEmployeecontracts) {
                Employeecontract ec = (Employeecontract) element;
                if (Objects.equals(ec.getEmployee().getId(), theEmployee.getId()) && !Objects.equals(ec.getId(), employeecontract.getId())) {
                    // contract for the same employee found but not the same contract - check overleap
                    java.time.LocalDate existingContractValidFrom = ec.getValidFrom();
                    java.time.LocalDate existingContractValidUntil = ec.getValidUntil();

                    if (newContractValidUntil != null && existingContractValidUntil != null) {
                        if (!newContractValidFrom.isBefore(existingContractValidFrom)
                                && !newContractValidFrom.isAfter(existingContractValidUntil)) {
                            // validFrom overleaps!
                            errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                            break;
                        }
                        if (!newContractValidUntil.isBefore(existingContractValidFrom)
                                && !newContractValidUntil.isAfter(existingContractValidUntil)) {
                            // validUntil overleaps!
                            errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                            break;
                        }
                        if (newContractValidFrom.isBefore(existingContractValidFrom)
                                && newContractValidUntil.isAfter(existingContractValidUntil)) {
                            // new Employee contract enclosures an existing one
                            errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                            break;
                        }
                    } else if (newContractValidUntil == null && existingContractValidUntil != null) {
                        if (!newContractValidFrom.isAfter(existingContractValidUntil)) {
                            errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                            break;
                        }
                    } else if (newContractValidUntil != null) {
                        if (!newContractValidUntil.isBefore(existingContractValidFrom)) {
                            errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                            break;
                        }
                    } else {
                        // two employee contracts with open end MUST overleap
                        errors.add("validFrom", new ActionMessage("form.employeecontract.error.overleap"));
                        break;
                    }
                }
            }
        }

        // check length of text fields
        if (ecForm.getTaskdescription().length() > GlobalConstants.EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH) {
            errors.add("taskdescription", new ActionMessage("form.employeecontract.error.taskdescription.toolong"));
        }

        // check dailyworkingtime format		
        if (!GenericValidator.isDouble(ecForm.getDailyworkingtime().toString()) ||
                !GenericValidator.isInRange(ecForm.getDailyworkingtime(),
                        0.0, GlobalConstants.MAX_DEBITHOURS)) {
            errors.add("dailyworkingtime", new ActionMessage("form.employeecontract.error.dailyworkingtime.wrongformat"));
        }
        double time = ecForm.getDailyworkingtime() * 100000;
        time += 0.5;
        int time2 = (int) time;
        int modulo = time2 % 5000;
        ecForm.setDailyworkingtime(time2 / 100000.0);

        if (modulo != 0) {
            errors.add("dailyworkingtime", new ActionMessage("form.employeecontract.error.dailyworkingtime.wrongformat2"));
        }

        // check initial overtime
        if (ecForm.getInitialOvertime() != null) {
            if (!validate(ecForm.getInitialOvertime())) {
                errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
            }
        }

        // check yearlyvacation format	
        if (!GenericValidator.isInt(ecForm.getYearlyvacation().toString()) ||
                !GenericValidator.isInRange(ecForm.getYearlyvacation(),
                        0.0, GlobalConstants.MAX_VACATION_PER_YEAR)) {
            errors.add("yearlyvacation", new ActionMessage("form.employeecontract.error.yearlyvacation.wrongformat"));
        }

        // check, if dates fit to existing timereports
        LocalDate untilDate = null;
        if (newContractValidUntil != null) {
            untilDate = newContractValidUntil;
        }
        if (ecId == null) {
            ecId = 0L;
        }
        List<Timereport> timereportsInvalidForDates = timereportDAO.
                getTimereportsByEmployeeContractIdInvalidForDates(newContractValidFrom, untilDate, ecId);
        if (timereportsInvalidForDates != null && !timereportsInvalidForDates.isEmpty()) {
            request.getSession().setAttribute("timereportsOutOfRange", timereportsInvalidForDates);
            errors.add("timereportOutOfRange", new ActionMessage("form.general.error.timereportoutofrange"));

        }

        saveErrors(request, errors);

        return errors;
    }

    /**
     * fills employee contract form with properties of given employee contract
     */
    private void setFormEntries(HttpServletRequest request, AddEmployeeContractForm ecForm, Employeecontract ec) {
        Employee theEmployee = ec.getEmployee();
        ecForm.setEmployee(theEmployee.getId());
        //only when the supervisor exists		
        if (ec.getSupervisor() != null) {
            ecForm.setSupervisorid(ec.getSupervisor().getId());
        } else {
            ecForm.setSupervisorid(-1);
        }

        request.getSession().setAttribute("currentEmployee", theEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", theEmployee.getId());

        List<Employee> employees = employeeDAO.getEmployees();
        request.getSession().setAttribute("employees", employees);

        //		ecForm.setEmployeeId(theEmployee.getId());
        ecForm.setTaskdescription(ec.getTaskDescription());
        ecForm.setFreelancer(ec.getFreelancer());
        ecForm.setHide(ec.getHide());
        ecForm.setDailyworkingtime(ec.getDailyWorkingTime());
        if (ec.getVacations().size() > 0) {
            // actually, vacation entitlement is a constant value
            // for an employee (not year-dependent), so just take the
            // first vacation entry to set the form value
            Vacation va = ec.getVacations().get(0);
            ecForm.setYearlyvacation(va.getEntitlement());
        } else {
            ecForm.setYearlyvacation(GlobalConstants.VACATION_PER_YEAR);
        }

        ecForm.setValidFrom(DateUtils.format(ec.getValidFrom()));
        if (ec.getValidUntil() != null) {
            ecForm.setValidUntil(DateUtils.format(ec.getValidUntil()));
        }
    }
}
