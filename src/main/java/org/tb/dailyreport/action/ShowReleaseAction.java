package org.tb.dailyreport.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.service.ReleaseService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShowReleaseAction extends LoginRequiredAction<ShowReleaseForm> {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final ReleaseService releaseService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowReleaseForm releaseForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        String task = request.getParameter("task");
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");

        // ── Section 1: own contract (visible to everyone) ──────────────────
        boolean updateSelf = false;

        var loginEmployeeContract = employeecontractService.getCurrentContract(loginEmployee.getId()).orElse(null);

        if ("releaseSelf".equals(task) && loginEmployeeContract != null) {
            ActionMessages errors = validateFormDataForRelease(request, releaseForm.getSelfReleaseDate(), loginEmployeeContract);
            if (!errors.isEmpty()) {
                return mapping.getInputForward();
            }
            releaseService.releaseTimereports(loginEmployeeContract.getId(), releaseForm.getSelfReleaseDate());
            updateSelf = true;
        }

        if (task == null || updateSelf) {
            if (loginEmployeeContract != null) {
                loginEmployeeContract = employeecontractService.getEmployeecontractById(loginEmployeeContract.getId());
            }
            var selfReleaseDate = loginEmployeeContract != null ? loginEmployeeContract.getReportReleaseDate() : null;
            request.getSession().setAttribute("loginEmployeeContract", loginEmployeeContract);
            request.getSession().setAttribute("loginEmployeeReleasedUntil", DateUtils.format(selfReleaseDate));
            request.getSession().setAttribute("loginEmployeeAcceptedUntil",
                DateUtils.format(loginEmployeeContract != null ? loginEmployeeContract.getReportAcceptanceDate() : null));

            if (selfReleaseDate == null && loginEmployeeContract != null) {
                selfReleaseDate = loginEmployeeContract.getValidFrom();
            } else if (selfReleaseDate != null) {
                selfReleaseDate = DateUtils.addMonths(selfReleaseDate, 1);
            }
            releaseForm.setSelfReleaseDate(selfReleaseDate);
        }

        // ── Section 2: team contracts (supervisors and managers only) ───────
        List<Employeecontract> viewableEmployeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());

        List<Employee> supervisors = new LinkedList<>();
        for (Employeecontract ec : viewableEmployeeContracts) {
            Employee supervisor = ec.getSupervisor();
            if (supervisor != null && !supervisors.contains(supervisor)) {
                supervisors.add(supervisor);
            }
        }
        supervisors.sort(Comparator.comparing(Employee::getName));
        request.getSession().setAttribute("supervisors", supervisors);

        List<Employeecontract> teamMemberContracts = employeecontractService.getTeamContracts(loginEmployee.getId());
        boolean supervisor = !teamMemberContracts.isEmpty();
        request.getSession().setAttribute("isSupervisor", supervisor);

        if (supervisor || authorizedUser.isManager()) {
            long superId;

            Employeecontract employeecontract = null;
            if (releaseForm.getEmployeeContractId() != null) {
                employeecontract = employeecontractService.getEmployeecontractById(releaseForm.getEmployeeContractId());
            }
            boolean updateEmployee = false;
            if ("updateEmployee".equals(task)) {
                updateEmployee = true;
            } else {
                employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            }
            if (employeecontract == null) {
                employeecontract = teamMemberContracts.get(0);
                updateEmployee = true;
            }

            List<Employeecontract> employeeContracts;
            if (request.getSession().getAttribute("supervisorId") != null) {
                superId = (Long) request.getSession().getAttribute("supervisorId");
                if (superId == -1) {
                    employeeContracts = viewableEmployeeContracts;
                } else {
                    teamMemberContracts = employeecontractService.getTeamContracts(superId);
                    employeeContracts = teamMemberContracts;
                }
            } else if (supervisor) {
                employeeContracts = teamMemberContracts;
                request.getSession().setAttribute("supervisorId", loginEmployee.getId());
            } else {
                employeeContracts = viewableEmployeeContracts;
                request.getSession().setAttribute("supervisorId", -1L);
            }

            employeeContracts.sort(Comparator.comparing(ec -> ec.getEmployee().getName()));
            request.getSession().setAttribute("employeecontracts", employeeContracts);

            if ("release".equals(task)) {
                ActionMessages errors = validateFormDataForRelease(request, releaseForm.getReleaseDate(), employeecontract);
                if (!errors.isEmpty()) {
                    return mapping.getInputForward();
                }
                releaseService.releaseTimereports(employeecontract.getId(), releaseForm.getReleaseDate());
                updateEmployee = true;
            }

            if ("accept".equals(task)) {
                ActionMessages errors = validateFormDataForAcceptance(request, releaseForm, employeecontract);
                if (!errors.isEmpty()) {
                    return mapping.getInputForward();
                }
                releaseService.acceptTimereports(employeecontract.getId(), releaseForm.getAcceptanceDate());
                updateEmployee = true;
            }

            if ("reopen".equals(task)) {
                releaseService.reopenTimereports(employeecontract.getId(), releaseForm.getReopenDate());
                updateEmployee = true;
            }

            if (authorizedUser.isManager() && "updateSupervisor".equals(task)) {
                superId = releaseForm.getSupervisorId();
                request.getSession().setAttribute("supervisorId", superId);
                if (superId == -1) {
                    request.getSession().setAttribute("employeecontracts", viewableEmployeeContracts);
                } else {
                    teamMemberContracts = employeecontractService.getTeamContracts(superId);
                    request.getSession().setAttribute("employeecontracts", teamMemberContracts);
                }
            }

            if ("sendreleasemail".equals(task)) {
                Employee recipient = employeeService.getEmployeeBySign(request.getParameter("sign"));
                releaseService.sendReleaseReminderMail(recipient.getId());
                request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"));
            }

            if ("sendacceptancemail".equals(task)) {
                Employee contEmployee = employeeService.getEmployeeBySign(request.getParameter("sign"));
                Employeecontract currentEmployeeContract = employeecontractService.getEmployeeContractValidAt(contEmployee.getId(), today());
                releaseService.sendAcceptanceReminderMail(currentEmployeeContract.getId());
                request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"));
            }

            if (task == null || updateEmployee) {
                employeecontract = employeecontractService.getEmployeecontractById(employeecontract.getId());
                var releaseDateFromContract = employeecontract.getReportReleaseDate();
                var acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();

                request.getSession().setAttribute("currentEmployeeContract", employeecontract);
                request.getSession().setAttribute("releasedUntil", DateUtils.format(releaseDateFromContract));
                request.getSession().setAttribute("acceptedUntil", DateUtils.format(acceptanceDateFromContract));
                request.getSession().setAttribute("employeeContractId", employeecontract.getId());
                request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());

                if (releaseDateFromContract == null) {
                    releaseDateFromContract = employeecontract.getValidFrom();
                } else {
                    releaseDateFromContract = DateUtils.addMonths(releaseDateFromContract, 1);
                }
                if (acceptanceDateFromContract == null) {
                    acceptanceDateFromContract = employeecontract.getValidFrom();
                }

                releaseForm.setEmployeeContractId(employeecontract.getId());
                releaseForm.setReleaseDate(releaseDateFromContract);
                releaseForm.setAcceptanceDate(acceptanceDateFromContract);
                releaseForm.setReopenDate(releaseForm.getReleaseDate());
            }
        }

        return mapping.findForward("success");
    }

    private ActionMessages validateFormDataForRelease(
        HttpServletRequest request, LocalDate date,
        Employeecontract selectedEmployeecontract) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        if (date == null || date.isBefore(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null
                && date.isAfter(selectedEmployeecontract.getValidUntil())) {
            errors.add("validation", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
        }

        if (selectedEmployeecontract.getReportAcceptanceDate() != null && date != null
                && date.isBefore(selectedEmployeecontract.getReportAcceptanceDate())) {
            errors.add("validation", new ActionMessage("form.release.error.date.before.acceptance"));
        }

        saveErrors(request, errors);
        return errors;
    }

    private ActionMessages validateFormDataForAcceptance(
        HttpServletRequest request, ShowReleaseForm releaseForm,
        Employeecontract selectedEmployeecontract) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        LocalDate acceptanceDate = releaseForm.getAcceptanceDate();

        if (acceptanceDate.isBefore(selectedEmployeecontract.getValidFrom())
            || selectedEmployeecontract.getValidUntil() != null && acceptanceDate.isAfter(selectedEmployeecontract.getValidUntil())) {
            errors.add("acceptancedate", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
        }

        if (selectedEmployeecontract.getReportReleaseDate() != null && acceptanceDate.isAfter(selectedEmployeecontract.getReportReleaseDate())) {
            errors.add("acceptancedate", new ActionMessage("form.release.error.date.after.release"));
        }

        if (selectedEmployeecontract.getReportAcceptanceDate() != null && acceptanceDate.isBefore(selectedEmployeecontract.getReportAcceptanceDate())) {
            errors.add("acceptancedate", new ActionMessage("form.release.error.date.before.stored"));
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        if (selectedEmployeecontract.getEmployee().equals(loginEmployee)) {
            errors.add("acceptancedate", new ActionMessage("form.release.error.foureyesprinciple"));
        }

        saveErrors(request, errors);
        return errors;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
