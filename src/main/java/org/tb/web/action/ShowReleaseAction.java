package org.tb.web.action;

import org.apache.struts.action.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tb.GlobalConstants;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Timereport;
import org.tb.helper.TimereportHelper;
import org.tb.persistence.*;
import org.tb.util.DateUtils;
import org.tb.util.OptionItem;
import org.tb.web.form.ShowReleaseForm;
import org.tb.web.util.MailSender;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.*;

public class ShowReleaseAction extends LoginRequiredAction {
    private static final Logger LOG = LoggerFactory.getLogger(ShowReleaseAction.class);

    private EmployeecontractDAO employeecontractDAO;
    private TimereportDAO timereportDAO;
    private EmployeeDAO employeeDAO;
    private PublicholidayDAO publicholidayDAO;
    private OvertimeDAO overtimeDAO;
    private EmployeeorderDAO employeeorderDAO;

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

    public void setTimereportDAO(TimereportDAO timereportDAO) {
        this.timereportDAO = timereportDAO;
    }

    public void setEmployeeDAO(EmployeeDAO employeeDAO) {
        this.employeeDAO = employeeDAO;
    }

    public void setPublicholidayDAO(PublicholidayDAO publicholidayDAO) {
        this.publicholidayDAO = publicholidayDAO;
    }

    public void setOvertimeDAO(OvertimeDAO overtimeDAO) {
        this.overtimeDAO = overtimeDAO;
    }

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
                                                 ActionForm form, HttpServletRequest request,
                                                 HttpServletResponse response) throws Exception {

        ShowReleaseForm releaseForm = (ShowReleaseForm) form;
        boolean updateEmployee = false;
        long superId;

        request.getSession().setAttribute("years",
                DateUtils.getYearsToDisplay());
        request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());

        List<Employeecontract> employeeContracts = employeecontractDAO
                .getVisibleEmployeeContractsOrderedByEmployeeSign();

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
        if (supervisor || (Boolean) request.getSession().getAttribute("employeeAuthorized")) {
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
                            .getId(), new Date());
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
        Date releaseDateFromContract = employeecontract.getReportReleaseDate();
        Date acceptanceDateFromContract = employeecontract
                .getReportAcceptanceDate();

        if (releaseDateFromContract == null) {
            releaseDateFromContract = employeecontract.getValidFrom();
        }
        if (acceptanceDateFromContract == null) {
            acceptanceDateFromContract = employeecontract.getValidFrom();
        }

        TimereportHelper th = new TimereportHelper();
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

            java.util.Date releaseDate = (java.util.Date) request.getSession()
                    .getAttribute("releaseDate");
            java.sql.Date sqlReleaseDate = new java.sql.Date(releaseDate
                    .getTime());

            // set status in timereports
            List<Timereport> timereports = timereportDAO
                    .getOpenTimereportsByEmployeeContractIdBeforeDate(
                            employeecontract.getId(), sqlReleaseDate);
            for (Timereport timereport : timereports) {
                timereport
                        .setStatus(GlobalConstants.TIMEREPORT_STATUS_COMMITED);
                timereport.setReleasedby(loginEmployee.getSign());
                timereport.setReleased(new java.util.Date());
                timereportDAO.save(timereport, loginEmployee, false);
            }
            releaseDateFromContract = releaseDate;

            request.getSession().setAttribute("days",
                    getDayList(releaseDateFromContract));

            // store new release date in employee contract
            employeecontract.setReportReleaseDate(sqlReleaseDate);
            employeecontractDAO.save(employeecontract, loginEmployee);
            // contract was saved after RELEASE
            // build recipient for releasemail for BL

            if (employeecontract.getSupervisor() != null) {
                Employee recipient = employeecontract.getSupervisor();
                Employee from = employeecontract.getEmployee();
                try {
                    MailSender.sendSalatBuchungenReleasedMail(recipient, from);
                } catch (Exception e) {
                    LOG.error("sending release mail failed!!!");
                }
            }
        }
        // End Release Action

        if (request.getParameter("task") != null
                && request.getParameter("task").equals("sendreleasemail")) {

            // build recipient for releasemail
            Employee recipient = employeeDAO.getEmployeeBySign(request.getParameter("sign"));

            // * revipient = Empfaenger, loginEmployee = Absender
            MailSender.sendSalatBuchungenToReleaseMail(recipient, loginEmployee);

            request.setAttribute("actionInfo", getResources(request).getMessage(getLocale(request), "main.release.actioninfo.mailsent.text"/* "statusreport.actioninfo.released.text" */));
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("sendacceptancemail")) {

            // build recipient for acceptancemail
            // Contract from Employee
            Employee contEmployee = employeeDAO.getEmployeeBySign(request.getParameter("sign"));
            Employeecontract currentEmployeeContract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(contEmployee.getId(), new Date());

            // BL
            Employee recipient = currentEmployeeContract.getSupervisor();
            // sender of the mail
            if (recipient != null) {
                MailSender.sendSalatBuchungenToAcceptanceMail(recipient, contEmployee, loginEmployee);
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

            java.util.Date acceptanceDate = (java.util.Date) request.getSession().getAttribute("acceptanceDate");
            java.sql.Date sqlAcceptanceDate = new java.sql.Date(acceptanceDate.getTime());

            // set status in timereports
            List<Timereport> timereports = timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(employeecontract.getId(), sqlAcceptanceDate);
            for (Timereport timereport : timereports) {
                timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_CLOSED);
                timereport.setAcceptedby(loginEmployee.getSign());
                timereport.setAccepted(new java.util.Date());
                timereportDAO.save(timereport, loginEmployee, false);
            }
            acceptanceDateFromContract = acceptanceDate;

            request.getSession().setAttribute("acceptanceDays", getDayList(acceptanceDateFromContract));

            // set new acceptance date in employee contract
            employeecontract.setReportAcceptanceDate(sqlAcceptanceDate);
            //compute overtimeStatic and set it in employee contract
            double otStatic = th.calculateOvertime(employeecontract.getValidFrom(), employeecontract.getReportAcceptanceDate(),
                    employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
            employeecontract.setOvertimeStatic(otStatic / 60.0);

            //only used the first time a release is accepted after SALAT-Release 1.83:
            if (employeecontract.getUseOvertimeOld() == null || employeecontract.getUseOvertimeOld()) {
                employeecontract.setUseOvertimeOld(false);
            }

            employeecontractDAO.save(employeecontract, loginEmployee);
        }

        if (request.getParameter("task") != null && request.getParameter("task").equals("reopen")) {
            Date reopenDate;

            reopenDate = th.getDateFormStrings(releaseForm.getReopenDay(),
                    releaseForm.getReopenMonth(), releaseForm.getReopenYear(),
                    false);

            if (reopenDate == null) {
                reopenDate = new Date();
            }
            java.sql.Date sqlReopenDate = new java.sql.Date(reopenDate.getTime());

            // set status in timereports
            List<Timereport> timereports = timereportDAO.getTimereportsByEmployeeContractIdAfterDate(employeecontract.getId(), sqlReopenDate);
            for (Timereport timereport : timereports) {
                timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
                timereportDAO.save(timereport, loginEmployee, false);
            }

            long timeMillis = sqlReopenDate.getTime();
            timeMillis -= 12 * 60 * 60 * 1000;
            sqlReopenDate.setTime(timeMillis);
            // String newReopenDateString = format.format(sqlReopenDate);

            if (sqlReopenDate.before(releaseDateFromContract)) {
                employeecontract.setReportReleaseDate(sqlReopenDate);
                releaseDateFromContract = sqlReopenDate;
                String[] releaseDateArray = th
                        .getDateAsStringArray(releaseDateFromContract);
                releaseForm.setDay(releaseDateArray[0]);
                releaseForm.setMonth(releaseDateArray[1]);
                releaseForm.setYear(releaseDateArray[2]);
            }
            if (sqlReopenDate.before(acceptanceDateFromContract)) {
                employeecontract.setReportAcceptanceDate(sqlReopenDate);
                acceptanceDateFromContract = sqlReopenDate;
                String[] acceptanceDateArray = th
                        .getDateAsStringArray(acceptanceDateFromContract);
                releaseForm.setAcceptanceDay(acceptanceDateArray[0]);
                releaseForm.setAcceptanceMonth(acceptanceDateArray[1]);
                releaseForm.setAcceptanceYear(acceptanceDateArray[2]);

                // recompute overtimeStatic and set it in employeecontract
                double otStatic = th.calculateOvertime(employeecontract.getValidFrom(), employeecontract.getReportAcceptanceDate(),
                        employeecontract, employeeorderDAO, publicholidayDAO, timereportDAO, overtimeDAO, true);
                employeecontract.setOvertimeStatic(otStatic / 60.0);
            }

            request.getSession().setAttribute("reopenDays",
                    getDayList(reopenDate));

            // store changed employee contract
            employeecontractDAO.save(employeecontract, loginEmployee);
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

            Date selectedDate = th.getDateFormStrings("01", releaseForm
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

            Date selectedDate = th.getDateFormStrings("01", releaseForm
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

            Date selectedDate = th.getDateFormStrings("01", releaseForm
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
            String[] releaseDateArray = th
                    .getDateAsStringArray(releaseDateFromContract);
            String[] acceptanceDateArray = th
                    .getDateAsStringArray(acceptanceDateFromContract);

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

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);

        String releasedUntil = simpleDateFormat.format(releaseDateFromContract);
        request.getSession().setAttribute("releasedUntil", releasedUntil);

        String acceptedUntil = simpleDateFormat
                .format(acceptanceDateFromContract);
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

        TimereportHelper th = new TimereportHelper();
        Date date = null;
        try {
            date = th.getDateFormStrings(releaseForm.getDay(), releaseForm
                    .getMonth(), releaseForm.getYear(), false);
        } catch (Exception e) {
            errors.add("releasedate", new ActionMessage(
                    "form.release.error.date.corrupted"));
        }

        if (date == null) {
            date = new Date();
        }
        request.getSession().setAttribute("releaseDate", date);

        if (date.before(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null && date
                .after(selectedEmployeecontract.getValidUntil())) {
            errors.add("releasedate", new ActionMessage(
                    "form.release.error.date.invalid.foremployeecontract"));
        }

        if (date.before(selectedEmployeecontract.getReportReleaseDate())) {
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

        TimereportHelper th = new TimereportHelper();
        Date date = null;
        try {
            date = th.getDateFormStrings(releaseForm.getAcceptanceDay(),
                    releaseForm.getAcceptanceMonth(), releaseForm
                            .getAcceptanceYear(), false);
        } catch (Exception e) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.corrupted"));
        }

        if (date == null) {
            date = new Date();
        }
        request.getSession().setAttribute("acceptanceDate", date);

        if (date.before(selectedEmployeecontract.getValidFrom())
                || selectedEmployeecontract.getValidUntil() != null && date
                .after(selectedEmployeecontract.getValidUntil())) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.invalid.foremployeecontract"));
        }

        Date releaseDate = selectedEmployeecontract.getReportReleaseDate();
        if (releaseDate == null) {
            releaseDate = selectedEmployeecontract.getValidFrom();
        }

        if (date.after(releaseDate)) {
            errors.add("acceptancedate", new ActionMessage(
                    "form.release.error.date.before.release"));
        }

        if (date.before(selectedEmployeecontract.getReportAcceptanceDate())) {
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
    private List<OptionItem> getDayList(Date date) {
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(date);
        int maxDays = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
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
