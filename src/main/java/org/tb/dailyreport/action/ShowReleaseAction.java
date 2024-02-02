package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.util.DateUtils.getDateAsStringArray;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.stereotype.Component;
import org.tb.common.OptionItem;
import org.tb.common.SimpleMailService;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShowReleaseAction extends LoginRequiredAction<ShowReleaseForm> {

    private final EmployeecontractDAO employeecontractDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeeDAO employeeDAO;
    private final SimpleMailService simpleMailService;
    private final TimereportService timereportService;
    private final OvertimeService overtimeService;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        ShowReleaseForm releaseForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        boolean updateEmployee = false;
        long superId;

        List<Employeecontract> viewableEmployeeContracts = employeecontractDAO.getViewableEmployeeContractsForAuthorizedUser();

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
        List<Employeecontract> teamMemberContracts = employeecontractDAO.getTeamContracts(loginEmployee.getId());
        boolean supervisor = !teamMemberContracts.isEmpty();
        request.getSession().setAttribute("isSupervisor", supervisor);

        Employeecontract employeecontract = null;
        if (releaseForm.getEmployeeContractId() != null) {
            employeecontract = employeecontractDAO.getEmployeeContractById(releaseForm.getEmployeeContractId());
        }
        if (supervisor || authorizedUser.isManager()) {
            Employeecontract currentEmployeeContract;
            if (request.getParameter("task") != null
                    && request.getParameter("task").equals("updateEmployee")) {
                updateEmployee = true;
            } else {
                currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
                employeecontract = currentEmployeeContract;
            }
        }
        if (employeecontract == null) {
            employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(loginEmployee.getId(), today());
        }

        List<Employeecontract> employeeContracts;
        /* check if supervisor has been set before, if not use isSupervisor or employeeAuthorized for preselecting employeecontracts shown*/
        if (request.getSession().getAttribute("supervisorId") != null) {
            superId = (Long) request.getSession().getAttribute("supervisorId");
            if (superId == -1) {
                employeeContracts = viewableEmployeeContracts;
            } else {
                teamMemberContracts = employeecontractDAO.getTeamContracts(superId);
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

        releaseForm.setEmployeeContractId(employeecontract.getId());
        request.getSession().setAttribute("employeeContractId", employeecontract.getId());
        request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract", employeecontract);

        // date from contract
        LocalDate releaseDateFromContract = employeecontract.getReportReleaseDate();
        LocalDate acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();

        if (releaseDateFromContract == null) {
            releaseDateFromContract = employeecontract.getValidFrom();
        }
        if (acceptanceDateFromContract == null) {
            acceptanceDateFromContract = employeecontract.getValidFrom();
        }

        // Release Action
        if (request.getParameter("task") != null && request.getParameter("task").equals("release")) {

            LocalDate releaseDate = LocalDate.parse(releaseForm.getReleaseDate());
            timereportService.releaseTimereports(employeecontract.getId(), releaseDate);

            Employeecontract employeeContract = employeecontractDAO.getEmployeeContractById(employeecontract.getId());
            releaseDateFromContract = employeeContract.getReportReleaseDate();
            releaseForm.setReleaseDate(releaseDateFromContract.toString());
            request.getSession().setAttribute("currentEmployeeContract", employeeContract);

            // build recipient for releasemail for BL

            if (employeecontract.getSupervisor() != null) {
                Employee recipient = employeecontract.getSupervisor();
                Employee from = employeecontract.getEmployee();
                try {
                    simpleMailService.sendSalatBuchungenReleasedMail(recipient, from);
                } catch (Exception e) {
                    log.error("sending release mail failed!!!");
                }
            }
        }
        // End Release Action

        if (request.getParameter("task") != null && request.getParameter("task").equals("sendreleasemail")) {
            // build recipient for releasemail
            Employee recipient = employeeDAO.getEmployeeBySign(request.getParameter("sign"));

            // * revipient = Empfaenger, loginEmployee = Absender
            simpleMailService.sendSalatBuchungenToReleaseMail(recipient, loginEmployee);

            request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"/* "statusreport.actioninfo.released.text" */));
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("sendacceptancemail")) {
            // build recipient for acceptancemail
            // Contract from Employee
            Employee contEmployee = employeeDAO.getEmployeeBySign(request.getParameter("sign"));
            Employeecontract currentEmployeeContract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(contEmployee.getId(), today());

            // BL
            Employee recipient = currentEmployeeContract.getSupervisor();
            // sender of the mail
            if (recipient != null) {
                simpleMailService.sendSalatBuchungenToAcceptanceMail(recipient, contEmployee, loginEmployee);
                request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"/* "statusreport.actioninfo.released.text" */));
            } else {
                // do nothing, Supervisor must not be null
            }
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("accept")) {
            LocalDate acceptanceDate = LocalDate.parse(releaseForm.getAcceptanceDate());
            timereportService.acceptTimereports(employeecontract.getId(), acceptanceDate);

            Employeecontract employeeContract = employeecontractDAO.getEmployeeContractById(employeecontract.getId());
            acceptanceDateFromContract = employeeContract.getReportAcceptanceDate();
            releaseForm.setAcceptanceDate(acceptanceDateFromContract.toString());
            request.getSession().setAttribute("currentEmployeeContract", employeeContract);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("reopen")) {
            LocalDate reopenDate = LocalDate.parse(releaseForm.getReopenDate());
            timereportService.reopenTimereports(employeecontract.getId(), reopenDate);

            // reload potenial updated data to feed the session and form
            employeecontract = employeecontractDAO.getEmployeeContractById(employeecontract.getId());
            releaseDateFromContract = employeecontract.getReportReleaseDate();
            releaseForm.setReleaseDate(releaseDateFromContract.toString());
            acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();
            releaseForm.setAcceptanceDate(acceptanceDateFromContract.toString());
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("updateSupervisor")) {
            superId = releaseForm.getSupervisorId();
            request.getSession().setAttribute("supervisorId", superId);
            if (superId == -1) {
                request.getSession().setAttribute("employeecontracts",
                        viewableEmployeeContracts);

            } else {
                teamMemberContracts = employeecontractDAO.getTeamContracts(superId);
                request.getSession().setAttribute("employeecontracts",
                        teamMemberContracts);
            }
        }

        if (request.getParameter("task") == null || updateEmployee) {
            releaseForm.setReleaseDate(releaseDateFromContract.toString());
            releaseForm.setAcceptanceDate(acceptanceDateFromContract.toString());
            releaseForm.setReopenDate(releaseForm.getReleaseDate());
        }

        String releasedUntil = DateUtils.format(releaseDateFromContract);
        request.getSession().setAttribute("releasedUntil", releasedUntil);

        String acceptedUntil = DateUtils.format(acceptanceDateFromContract);
        request.getSession().setAttribute("acceptedUntil", acceptedUntil);

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
