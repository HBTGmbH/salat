package org.tb.employee.action;

import static org.tb.common.GlobalConstants.VACATION_PER_YEAR;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.domain.Vacation;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.domain.Overtime;
import org.tb.employee.persistence.OvertimeDAO;

/**
 * action class for editing an employee contract
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class EditEmployeecontractAction extends LoginRequiredAction<AddEmployeeContractForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeDAO employeeDAO;
    private final OvertimeDAO overtimeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddEmployeeContractForm ecForm, HttpServletRequest request, HttpServletResponse response) {
        // remove list with timereports out of range
        request.getSession().removeAttribute("timereportsOutOfRange");

        long ecId = Long.parseLong(request.getParameter("ecId"));
        Employeecontract ec = employeecontractDAO.getEmployeeContractByIdInitializeEager(ecId);
        request.getSession().setAttribute("ecId", ec.getId());

        // fill the form with properties of employee contract to be edited
        setFormEntries(request, ecForm, ec);

        // set context
        request.getSession().setAttribute("employeeContractContext", "edit");

        // get overtime-entries
        List<Overtime> overtimes = overtimeDAO.getOvertimesByEmployeeContractId(ecId);
        Duration totalOvertime = Duration.ZERO;
        for (Overtime overtime : overtimes) {
            totalOvertime = totalOvertime.plus(overtime.getTimeMinutes());
        }
        request.getSession().setAttribute("overtimes", overtimes);
        request.getSession().setAttribute("totalovertime", DurationUtils.format(totalOvertime));

        // set day string for overime
        LocalDate now = today();
        request.getSession().setAttribute("dateString", DateUtils.format(now));

        // forward to employee contract add/edit form
        return mapping.findForward("success");
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
            ecForm.setYearlyvacation(String.valueOf(VACATION_PER_YEAR));
        }

        LocalDate fromDate = ec.getValidFrom();
        ecForm.setValidFrom(DateUtils.format(fromDate));
        if (ec.getValidUntil() != null) {
            LocalDate untilDate = ec.getValidUntil();
            ecForm.setValidUntil(DateUtils.format(untilDate));
        }

        request.getSession().setAttribute("currentEmployee", theEmployee.getName());
        request.getSession().setAttribute("currentEmployeeId", theEmployee.getId());

        List<Employee> employees = employeeDAO.getEmployees().stream()
                .filter(e -> !e.getLastname().startsWith("z_"))
                .toList();
        request.getSession().setAttribute("employees", employees);

        List<Employee> employeesWithContracts = employeeDAO.getEmployeesWithValidContracts();
        request.getSession().setAttribute("empWithCont", employeesWithContracts);
    }

}
