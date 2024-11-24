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

        boolean updateEmployee = false;
        long superId;

        List<Employeecontract> viewableEmployeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());

        //get a list of all supervisors 
        List<Employee> supervisors = new LinkedList<>();
        for (Employeecontract ec : viewableEmployeeContracts) {
            Employee supervisor = ec.getSupervisor();
            if (supervisor != null && !supervisors.contains(supervisor)) {
                supervisors.add(supervisor);
            }
        }
        supervisors.sort(Comparator.comparing(Employee::getName));
        request.getSession().setAttribute("supervisors", supervisors);

        /* get team members */
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Employeecontract> teamMemberContracts = employeecontractService.getTeamContracts(loginEmployee.getId());
        boolean supervisor = !teamMemberContracts.isEmpty();
        request.getSession().setAttribute("isSupervisor", supervisor);

        Employeecontract employeecontract = null;
        if (releaseForm.getEmployeeContractId() != null) {
            employeecontract = employeecontractService.getEmployeecontractById(releaseForm.getEmployeeContractId());
        }
        if (supervisor || authorizedUser.isManager()) {
            Employeecontract currentEmployeeContract;
            if (request.getParameter("task") != null && request.getParameter("task").equals("updateEmployee")) {
                updateEmployee = true;
            } else {
                currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
                employeecontract = currentEmployeeContract;
            }
        }
        if (employeecontract == null) {
            employeecontract = employeecontractService.getEmployeeContractValidAt(loginEmployee.getId(), today());
            updateEmployee = true;
        }

        List<Employeecontract> employeeContracts;
        /* check if supervisor has been set before, if not use isSupervisor or employeeAuthorized for preselecting employeecontracts shown*/
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

        // Release Action
        if (request.getParameter("task") != null && request.getParameter("task").equals("release")) {

            // validate form data
            ActionMessages errorMessages = validateFormDataForRelease(request, releaseForm, employeecontract);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            LocalDate releaseDate = releaseForm.getReleaseDate();
            var errors = releaseService.releaseTimereports(employeecontract.getId(), releaseDate);
            if(!errors.isEmpty()) {
                for(var error : errors) {
                    addToErrors(request, error);
                };
                return mapping.getInputForward();
            }

            updateEmployee = true;
        }
        // End Release Action

        if (request.getParameter("task") != null && request.getParameter("task").equals("accept")) {
            // validate form data
            ActionMessages errorMessages = validateFormDataForAcceptance(request, releaseForm, employeecontract);
            if (!errorMessages.isEmpty()) {
                return mapping.getInputForward();
            }

            LocalDate acceptanceDate = releaseForm.getAcceptanceDate();
            releaseService.acceptTimereports(employeecontract.getId(), acceptanceDate);
            updateEmployee = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("reopen")) {
            LocalDate reopenDate = releaseForm.getReopenDate();
            releaseService.reopenTimereports(employeecontract.getId(), reopenDate);
            updateEmployee = true;
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("updateSupervisor")) {
            superId = releaseForm.getSupervisorId();
            request.getSession().setAttribute("supervisorId", superId);
            if (superId == -1) {
                request.getSession().setAttribute("employeecontracts",
                        viewableEmployeeContracts);

            } else {
                teamMemberContracts = employeecontractService.getTeamContracts(superId);
                request.getSession().setAttribute("employeecontracts",
                        teamMemberContracts);
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("sendreleasemail")) {
            Employee recipient = employeeService.getEmployeeBySign(request.getParameter("sign"));
            releaseService.sendReleaseReminderMail(recipient.getId());
            request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"));
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("sendacceptancemail")) {
            Employee contEmployee = employeeService.getEmployeeBySign(request.getParameter("sign"));
            Employeecontract currentEmployeeContract = employeecontractService.getEmployeeContractValidAt(contEmployee.getId(), today());
            releaseService.sendAcceptanceReminderMail(currentEmployeeContract.getId());
            request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"));
        }

        if (request.getParameter("task") == null || updateEmployee) {
            // reload potenial updated data to feed the session and form
            employeecontract = employeecontractService.getEmployeecontractById(employeecontract.getId());
            var releaseDateFromContract = employeecontract.getReportReleaseDate();
            var acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();

            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
            String releasedUntil = DateUtils.format(releaseDateFromContract);
            request.getSession().setAttribute("releasedUntil", releasedUntil);
            String acceptedUntil = DateUtils.format(acceptanceDateFromContract);
            request.getSession().setAttribute("acceptedUntil", acceptedUntil);
            request.getSession().setAttribute("employeeContractId", employeecontract.getId());
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);

            // ensure meaningful values are set to support the user experience - null values do not help here
            if (releaseDateFromContract == null) {
                releaseDateFromContract = employeecontract.getValidFrom();
            } else {
                // always show next month to help employee release next month
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

        return mapping.findForward("success");
    }

    private ActionMessages validateFormDataForRelease(
        HttpServletRequest request, ShowReleaseForm releaseForm,
        Employeecontract selectedEmployeecontract) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        LocalDate date = releaseForm.getReleaseDate();

        if (date.isBefore(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null
                && date.isAfter(selectedEmployeecontract.getValidUntil())) {
            errors.add("validation", new ActionMessage("form.release.error.date.invalid.foremployeecontract"));
        }

        if (selectedEmployeecontract.getReportAcceptanceDate() != null && date.isBefore(selectedEmployeecontract.getReportAcceptanceDate())) {
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
