package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.util.DateUtils.getDateAsStringArray;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
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

        request.getSession().setAttribute("years", getYearsToDisplay());
        request.getSession().setAttribute("days", getDaysToDisplay());

        List<Employeecontract> employeeContracts = employeecontractDAO
                .getViewableEmployeeContractsForAuthorizedUser();

        //get a list of all supervisors 
        List<Employee> supervisors = new LinkedList<>();
        for (Employeecontract ec : employeeContracts) {
            Employee supervisor = ec.getSupervisor();
            if (!supervisors.contains(supervisor)) {
                supervisors.add(supervisor);
            }
        }
        request.getSession().setAttribute("supervisors", supervisors);

        /* get team members */
        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Employeecontract> teamMemberContracts = employeecontractDAO.getTeamContracts(loginEmployee.getId());
        boolean supervisor = !teamMemberContracts.isEmpty();
        request.getSession().setAttribute("isSupervisor", supervisor);

        Employeecontract employeecontract = null;
        if (releaseForm.getEmployeeContractId() != null) {
            employeecontract = employeecontractDAO
                    .getEmployeeContractById(releaseForm
                            .getEmployeeContractId());
        }
        if (supervisor || authorizedUser.isManager()) {
            Employeecontract currentEmployeeContract;
            if (request.getParameter("task") != null
                    && request.getParameter("task").equals("updateEmployee")) {
                updateEmployee = true;
            } else {
                currentEmployeeContract = (Employeecontract) request
                        .getSession().getAttribute("currentEmployeeContract");
                employeecontract = currentEmployeeContract;
            }
        }
        if (employeecontract == null) {
            employeecontract = employeecontractDAO
                    .getEmployeeContractByEmployeeIdAndDate(loginEmployee
                            .getId(), today());
        }

        /* check if supervisor has been set before, if not use isSupervisor or employeeAuthorized for preselecting employeecontracts shown*/
        if (request.getSession().getAttribute("supervisorId") != null) {
            superId = (Long) request.getSession().getAttribute("supervisorId");
            if (superId == -1) {
                request.getSession().setAttribute("employeecontracts",
                        employeeContracts);
            } else {
                teamMemberContracts = employeecontractDAO.getTeamContracts(superId);
                //                teamMemberContracts.add(employeecontract);
                request.getSession().setAttribute("employeecontracts",
                        teamMemberContracts);
            }
        } else if (supervisor) {
            //            teamMemberContracts.add(employeecontract);
            request.getSession().setAttribute("employeecontracts",
                    teamMemberContracts);
            request.getSession().setAttribute("supervisorId", loginEmployee.getId());
        } else {
            request.getSession().setAttribute("employeecontracts",
                    employeeContracts);
            request.getSession().setAttribute("supervisorId", -1L);
        }

        releaseForm.setEmployeeContractId(employeecontract.getId());
        request.getSession().setAttribute("employeeContractId",
                employeecontract.getId());
        request.getSession().setAttribute("currentEmployeeId",
                employeecontract.getEmployee().getId());
        request.getSession().setAttribute("currentEmployeeContract",
                employeecontract);

        // date from contract
        LocalDate releaseDateFromContract = employeecontract.getReportReleaseDate();
        LocalDate acceptanceDateFromContract = employeecontract
                .getReportAcceptanceDate();

        if (releaseDateFromContract == null) {
            releaseDateFromContract = employeecontract.getValidFrom();
        }
        if (acceptanceDateFromContract == null) {
            acceptanceDateFromContract = employeecontract.getValidFrom();
        }

        // Release Action
        if (request.getParameter("task") != null
                && request.getParameter("task").equals("release")) {

            // validate form data
            ActionMessages errorMessages = validateFormDataForRelease(request,
                    releaseForm, employeecontract);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            // set selected date in session
            request.getSession().setAttribute("releaseDay",
                    releaseForm.getDay());
            request.getSession().setAttribute("releaseMonth",
                    releaseForm.getMonth());
            request.getSession().setAttribute("releaseYear",
                    releaseForm.getYear());

            LocalDate releaseDate = (LocalDate) request.getSession()
                    .getAttribute("releaseDate");

            timereportService.releaseTimereports(employeecontract.getId(), releaseDate);

            releaseDateFromContract = releaseDate;
            request.getSession().setAttribute("days",
                    getDayList(releaseDateFromContract));

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

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("sendreleasemail")) {

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

            // validate form data
            ActionMessages errorMessages = validateFormDataForAcceptance(request, releaseForm, employeecontract);
            if (errorMessages.size() > 0) {
                return mapping.getInputForward();
            }

            LocalDate acceptanceDate = (LocalDate) request.getSession().getAttribute("acceptanceDate");

            timereportService.acceptTimereports(employeecontract.getId(), acceptanceDate);

            acceptanceDateFromContract = acceptanceDate;
            request.getSession().setAttribute("acceptanceDays", getDayList(acceptanceDateFromContract));
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("reopen")) {
            LocalDate reopenDate;

            reopenDate = getDateFormStrings(releaseForm.getReopenDay(),
                    releaseForm.getReopenMonth(), releaseForm.getReopenYear(),
                    false);

            if (reopenDate == null) {
                reopenDate = DateUtils.today();
            }

            timereportService.reopenTimereports(employeecontract.getId(), reopenDate);

            // reload potenial updated data to feed the session and form
            employeecontract = employeecontractDAO.getEmployeeContractById(employeecontract.getId());
            releaseDateFromContract = employeecontract.getReportReleaseDate();
            String[] releaseDateArray = getDateAsStringArray(releaseDateFromContract);
            releaseForm.setDay(releaseDateArray[0]);
            releaseForm.setMonth(releaseDateArray[1]);
            releaseForm.setYear(releaseDateArray[2]);
            acceptanceDateFromContract = employeecontract.getReportAcceptanceDate();
            String[] acceptanceDateArray = getDateAsStringArray(acceptanceDateFromContract);
            releaseForm.setAcceptanceDay(acceptanceDateArray[0]);
            releaseForm.setAcceptanceMonth(acceptanceDateArray[1]);
            releaseForm.setAcceptanceYear(acceptanceDateArray[2]);

            request.getSession().setAttribute("reopenDays",
                    getDayList(reopenDate.minusDays(1)));
        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("updateSupervisor")) {
            superId = releaseForm.getSupervisorId();
            request.getSession().setAttribute("supervisorId", superId);
            if (superId == -1) {
                request.getSession().setAttribute("employeecontracts",
                        employeeContracts);

            } else {
                teamMemberContracts = employeecontractDAO.getTeamContracts(superId);
                request.getSession().setAttribute("employeecontracts",
                        teamMemberContracts);
            }
        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("refreshDate")) {
            // set selected date in session
            request.getSession().setAttribute("releaseDay",
                    releaseForm.getDay());
            request.getSession().setAttribute("releaseMonth",
                    releaseForm.getMonth());
            request.getSession().setAttribute("releaseYear",
                    releaseForm.getYear());

            int day = Integer.parseInt(releaseForm.getDay());

            LocalDate selectedDate = getDateFormStrings("01", releaseForm
                    .getMonth(), releaseForm.getYear(), false);

            List<OptionItem> days = getDayList(selectedDate);

            int lastDay = Integer
                    .parseInt(days.get(days.size() - 1).getValue());

            if (request.getParameter("refreshMonth") != null
                    && request.getParameter("refreshMonth").equals("true")) {
                releaseForm.setDay(lastDay + "");
            } else if (lastDay < day) {
                releaseForm.setDay(lastDay + "");
            }

            request.getSession().setAttribute("days", days);

        }

        if (request.getParameter("task") != null
                && request.getParameter("task")
                .equals("refreshAcceptanceDate")) {

            int day = Integer.parseInt(releaseForm.getAcceptanceDay());

            LocalDate selectedDate = getDateFormStrings("01", releaseForm
                            .getAcceptanceMonth(), releaseForm.getAcceptanceYear(),
                    false);

            List<OptionItem> days = getDayList(selectedDate);

            int lastDay = Integer
                    .parseInt(days.get(days.size() - 1).getValue());

            if (request.getParameter("refreshMonth") != null
                    && request.getParameter("refreshMonth").equals("true")) {
                releaseForm.setAcceptanceDay(lastDay + "");
            } else if (lastDay < day) {
                releaseForm.setAcceptanceDay(lastDay + "");
            }

            request.getSession().setAttribute("acceptanceDays", days);

        }

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("refreshReopenDate")) {

            int day = Integer.parseInt(releaseForm.getReopenDay());

            LocalDate selectedDate = getDateFormStrings("01", releaseForm
                    .getReopenMonth(), releaseForm.getReopenYear(), false);

            List<OptionItem> days = getDayList(selectedDate);

            int lastDay = Integer
                    .parseInt(days.get(days.size() - 1).getValue());

            if (request.getParameter("refreshMonth") != null
                    && request.getParameter("refreshMonth").equals("true")) {
                releaseForm.setReopenDay(lastDay + "");
            } else if (lastDay < day) {
                releaseForm.setReopenDay(lastDay + "");
            }

            request.getSession().setAttribute("reopenDays", days);

        }

        if (request.getParameter("task") == null || updateEmployee) {
            String[] releaseDateArray = getDateAsStringArray(releaseDateFromContract);
            String[] acceptanceDateArray = getDateAsStringArray(acceptanceDateFromContract);

            // set form entries
            releaseForm.setDay(releaseDateArray[0]);
            releaseForm.setMonth(releaseDateArray[1]);
            releaseForm.setYear(releaseDateArray[2]);
            releaseForm.setAcceptanceDay(acceptanceDateArray[0]);
            releaseForm.setAcceptanceMonth(acceptanceDateArray[1]);
            releaseForm.setAcceptanceYear(acceptanceDateArray[2]);
            releaseForm.setReopenDay(releaseDateArray[0]);
            releaseForm.setReopenMonth(releaseDateArray[1]);
            releaseForm.setReopenYear(releaseDateArray[2]);

            request.getSession()
                    .setAttribute("releaseDay", releaseDateArray[0]);
            request.getSession().setAttribute("releaseMonth",
                    releaseDateArray[1]);
            request.getSession().setAttribute("releaseYear",
                    releaseDateArray[2]);

            request.getSession().setAttribute("days",
                    getDayList(releaseDateFromContract));
            request.getSession().setAttribute("acceptanceDays",
                    getDayList(acceptanceDateFromContract));
            request.getSession().setAttribute("reopenDays",
                    getDayList(releaseDateFromContract));
        }

        String releasedUntil = DateUtils.format(releaseDateFromContract);
        request.getSession().setAttribute("releasedUntil", releasedUntil);

        String acceptedUntil = DateUtils.format(acceptanceDateFromContract);
        request.getSession().setAttribute("acceptedUntil", acceptedUntil);

        return mapping.findForward("success");
    }

    private ActionMessages validateFormDataForRelease(
            HttpServletRequest request, ShowReleaseForm releaseForm,
            Employeecontract selectedEmployeecontract) {

        ActionMessages errors = getErrors(request);
        if (errors == null) {
            errors = new ActionMessages();
        }

        LocalDate date = null;
        try {
            date = getDateFormStrings(releaseForm.getDay(), releaseForm
                    .getMonth(), releaseForm.getYear(), false);
        } catch (Exception e) {
            errors.add("releasedate", new ActionMessage(
                    "form.release.error.date.corrupted"));
        }

        if (date == null) {
            date = today();
        }
        request.getSession().setAttribute("releaseDate", date);

        if (date.isBefore(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null && date
                .isAfter(selectedEmployeecontract.getValidUntil())) {
            errors.add("releasedate", new ActionMessage(
                    "form.release.error.date.invalid.foremployeecontract"));
        }

        if (selectedEmployeecontract.getReportReleaseDate() != null && date.isBefore(selectedEmployeecontract.getReportReleaseDate())) {
            errors.add("releasedate", new ActionMessage(
                    "form.release.error.date.before.stored"));
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

        LocalDate date = null;
        try {
            date = getDateFormStrings(releaseForm.getAcceptanceDay(),
                    releaseForm.getAcceptanceMonth(), releaseForm
                            .getAcceptanceYear(), false);
        } catch (Exception e) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.corrupted"));
        }

        if (date == null) {
            date = today();
        }
        request.getSession().setAttribute("acceptanceDate", date);

        if (date.isBefore(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null && date
                .isAfter(selectedEmployeecontract.getValidUntil())) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.invalid.foremployeecontract"));
        }

        LocalDate releaseDate = selectedEmployeecontract.getReportReleaseDate();

        if (releaseDate == null || date.isAfter(releaseDate)) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.before.release"));
        }

        if (selectedEmployeecontract.getReportAcceptanceDate() != null && date.isBefore(selectedEmployeecontract.getReportAcceptanceDate())) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.before.stored"));
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute(
                "loginEmployee");
        if (selectedEmployeecontract.getEmployee().equals(loginEmployee)) {
            errors.add("acceptanceda)te", new ActionMessage(
                    "form.release.error.foureyesprinciple"));
        }

        saveErrors(request, errors);

        return errors;

    }

    /**
     * Returns a list of days as {@link OptionItem}s ("01", "02", "03",...)
     * fitting to the given date (month, year).
     */
    private List<OptionItem> getDayList(LocalDate date) {
        int maxDays = DateUtils.getMonthDays(date);
        List<OptionItem> days = new ArrayList<>();

        String dayValue;
        String dayLabel;
        for (int i = 1; i <= maxDays; i++) {
            if (i < 10) {
                dayLabel = "0" + i;
                dayValue = "0" + i;
            } else {
                dayLabel = "" + i;
                dayValue = "" + i;
            }
            days.add(new OptionItem(dayValue, dayLabel));
        }
        return days;
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
