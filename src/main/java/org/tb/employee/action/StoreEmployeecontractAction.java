package org.tb.employee.action;

import static java.lang.Boolean.TRUE;
import static org.tb.common.GlobalConstants.DEFAULT_VACATION_PER_YEAR;
import static org.tb.common.util.DateUtils.addDays;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.domain.Vacation;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.EmployeecontractService.ContractStoredInfo;

/**
 * action class for storing an employee contractpermanently
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class StoreEmployeecontractAction extends LoginRequiredAction<AddEmployeeContractForm> {

    private final EmployeeService employeeService;
    private final EmployeecontractService employeecontractService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeContractForm ecForm, HttpServletRequest request, HttpServletResponse response) {
        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        // Task for setting the date, previous, next and to-day for both, until and from date
        if (request.getParameter("task") != null && request.getParameter("task").equals("setDate")) {
            String which = request.getParameter("which").toLowerCase();
            int howMuch = Integer.parseInt(request.getParameter("howMuch"));

            String datum = which.equals("until") ? ecForm.getValidUntil() : ecForm.getValidFrom();

            LocalDate newValue;
            if (howMuch != 0) {
                ActionMessages errorMessages = validateDate(request, ecForm, which);
                if (!errorMessages.isEmpty()) {
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

            // create overtime entity and store it
            Long ecId = (Long) request.getSession().getAttribute("ecId");
            Employeecontract ec = employeecontractService.getEmployeecontractById(ecId);

            Overtime overtime = new Overtime();
            overtime.setComment(ecForm.getNewOvertimeComment());
            overtime.setEmployeecontract(ec);
            overtime.setEffective(ecForm.getNewOvertimeEffectiveTyped());
            overtime.setTimeMinutes(ecForm.getNewOvertimeTyped());

            employeecontractService.create(overtime);

            // refresh list of overtime adjustments
            List<Overtime> overtimes = employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(ecId);
            Duration totalOvertime = Duration.ZERO;
            for (Overtime ot : overtimes) {
                totalOvertime = totalOvertime.plus(ot.getTimeMinutes());
            }

            request.getSession().setAttribute("overtimes", overtimes);
            request.getSession().setAttribute("totalovertime", DurationUtils.format(totalOvertime));

            // reset form
            ecForm.reset(mapping, request);
            setFormEntries(request, ecForm, ec);

            return mapping.findForward("reset");
        }

        if (request.getParameter("task") != null &&
                request.getParameter("task").equals("save") ||
                request.getParameter("ecId") != null) {

            LocalDate validFrom = ecForm.getValidFromTyped();
            LocalDate validUntil = ecForm.getValidUntilTyped();
            Duration dailyworkingtime = ecForm.getDailyworkingtimeTyped();
            Duration initialOvertime = ecForm.getInitialOvertimeTyped();
            int yearlyvacation = ecForm.getYearlyvacationTyped();
            var resolveConflicts = TRUE.equals(ecForm.getResolveConflicts());

            Long existingEmployeecontractId = (Long) request.getSession().getAttribute("ecId");
            ContractStoredInfo info;
            if(existingEmployeecontractId != null) {
                info = employeecontractService.updateEmployeecontract(
                    existingEmployeecontractId,
                    validFrom,
                    validUntil,
                    ecForm.getSupervisorid(),
                    ecForm.getTaskdescription(),
                    ecForm.getFreelancer(),
                    ecForm.getHide(),
                    dailyworkingtime,
                    yearlyvacation,
                    resolveConflicts
                );
            } else {
                info = employeecontractService.createEmployeecontract(
                    ecForm.getEmployee(),
                    validFrom,
                    validUntil,
                    ecForm.getSupervisorid(),
                    ecForm.getTaskdescription(),
                    ecForm.getFreelancer(),
                    ecForm.getHide(),
                    dailyworkingtime,
                    yearlyvacation,
                    initialOvertime, // TODO move to OvertimeService - this has nothing to do with the contract, same for the "staticOvertime" in Employeecontract, should be somewhere else
                    resolveConflicts
                );
            }

            List<Employee> employeeOptionList = employeeService.getAllEmployees();
            request.getSession().setAttribute("employees", employeeOptionList);
            request.setAttribute("logs", info.getLog());

            request.getSession().setAttribute("employeecontracts", employeecontractService.getAllEmployeeContracts());
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

                request.getSession().setAttribute("employeecontracts", employeecontractService.getEmployeeContractsByFilters(show, filter, filterEmployeeId));

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
            if(!DateUtils.validateDate(ecForm.getValidFrom())) {
                errors.add("validFrom", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
        } else {
            if(!DateUtils.validateDate(ecForm.getValidUntil())) {
                errors.add("validUntil", new ActionMessage("form.timereport.error.date.wrongformat"));
            }
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

        // only when the supervisor exists
        if (ec.getSupervisor() != null) {
            ecForm.setSupervisorid(ec.getSupervisor().getId());
        } else {
            ecForm.setSupervisorid(-1);
        }

        ecForm.setTaskdescription(ec.getTaskDescription());
        ecForm.setFreelancer(ec.getFreelancer());
        ecForm.setHide(ec.getHide());
        ecForm.setDailyworkingtime(DurationUtils.format(ec.getDailyWorkingTime()));
        if (!ec.getVacations().isEmpty()) {
            // actually, vacation entitlement is a constant value
            // for an employee (not year-dependent), so just take the
            // first vacation entry to set the form value
            Vacation va = ec.getVacations().getFirst();
            ecForm.setYearlyvacation(va.getEntitlement().toString());
        } else {
            ecForm.setYearlyvacation(String.valueOf(DEFAULT_VACATION_PER_YEAR));
        }

        LocalDate fromDate = ec.getValidFrom();
        ecForm.setValidFrom(DateUtils.format(fromDate));
        if (ec.getValidUntil() != null) {
            LocalDate untilDate = ec.getValidUntil();
            ecForm.setValidUntil(DateUtils.format(untilDate));
        }

        request.getSession().setAttribute("currentEmployee", theEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", theEmployee.getId());

        List<Employee> employees = employeeService.getAllEmployees();
        request.getSession().setAttribute("employees", employees);

        List<Employee> employeesWithContracts = employeeService.getEmployeesWithValidContracts();
        request.getSession().setAttribute("empWithCont", employeesWithContracts);
    }
}
