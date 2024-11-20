package org.tb.dailyreport.action;

import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static org.tb.common.DateTimeViewHelper.getShortstringFromMonthMM;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import java.util.Map;
import java.util.Map.Entry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.AuthorizedUser;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.rest.DailyWorkingReportCsvConverter;
import org.tb.dailyreport.service.DailyWorkingReportService;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.matrix.MatrixHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.viewhelper.EmployeeViewHelper;

@Component
@RequiredArgsConstructor
public class ShowMatrixAction extends DailyReportAction<ShowMatrixForm> {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MatrixHelper matrixHelper;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;
    private final AuthorizedUser authorizedUser;
    private final DailyWorkingReportCsvConverter dailyWorkingReportCsvConverter;
    private final DailyWorkingReportService dailyWorkingReportService;

    @Override
    @SneakyThrows
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowMatrixForm reportForm, HttpServletRequest request, HttpServletResponse response) {

        // check if special tasks initiated from the daily display need to be
        // carried out...
        String task = request.getParameter("task");
        boolean doRefreshEmployeeSummaryData = false;

        // call on MatrixView with parameter print
        if ("print".equals(task)) {
            return mapping.findForward("print");
        }

        if ("switchEmployee".equalsIgnoreCase(task)) {
            task = "refreshMatrix";
            doRefreshEmployeeSummaryData = true;
        }

        if ("setMonth".equals(task)) {
            var mode = request.getParameter("mode");
            var date = getDateFormStrings("1", reportForm.getFromMonth(), reportForm.getFromYear(), false);
            switch(mode) {
                case "0":
                    date = today();
                    break;
                default:
                    date = date.plusMonths(Long.parseLong(mode));
            }
            String fromMonth = getShortstringFromMonthMM(date.getMonthValue());
            String fromYear = String.valueOf(date.getYear());
            reportForm.setFromMonth(fromMonth);
            reportForm.setFromYear(fromYear);
            Map<String, Object> results = matrixHelper.refreshMatrix(reportForm, request, authorizedUser);
            return finishHandling(results, request, matrixHelper, mapping, doRefreshEmployeeSummaryData);
        }

        if("fillOpenWorkdaysNotWorked".equals(task)) {
            var employeecontract = getEmployeeContractFromRequest(request);
            if(employeecontract != null) {
                Long employeecontractId = employeecontract.getId();
                var first = getDateFormStrings("1", reportForm.getFromMonth(), reportForm.getFromYear(), false);
                var last = first.with(lastDayOfMonth());
                first.datesUntil(last.plusDays(1)).forEach(day -> {
                    var isRegularWorkingday = workingdayService.isRegularWorkingday(day);
                    if(isRegularWorkingday) {
                        var hasBookings = !timereportService.getTimereportsByDateAndEmployeeContractId(employeecontractId, day).isEmpty();
                        if(!hasBookings) {
                            var workingday = workingdayService.getWorkingday(employeecontractId, day);
                            if(workingday == null) {
                                workingday = new Workingday();
                                workingday.setEmployeecontract(employeecontract);
                                workingday.setRefday(day);
                                workingday.setType(WorkingDayType.NOT_WORKED);
                                workingdayService.upsertWorkingday(workingday);
                            }
                        }
                    }
                });
                task = "refreshMatrix";
                doRefreshEmployeeSummaryData = true;
            }
        }

        if("importCsv".equals(task) && reportForm.getImportFile() != null) {
            var reports = dailyWorkingReportCsvConverter.read(reportForm.getImportFile().getInputStream());
            if("replace".equals(reportForm.getImportMode())){
                dailyWorkingReportService.updateReports(reports);
            } else if("add".equals(reportForm.getImportMode())){
                dailyWorkingReportService.createReports(reports);
            }
        }

        // call on MatrixView with parameter refreshMatrix to update request
        if ("refreshMatrix".equals(task)) {
            Map<String, Object> results = matrixHelper.refreshMatrix(reportForm, request, authorizedUser);
            return finishHandling(results, request, matrixHelper, mapping, doRefreshEmployeeSummaryData);
        }

        // call on MatrixView with any parameter to forward or go back
        if (task != null && !"importCsv".equals(task)) {
            // just go back to main menu
            return mapping.findForward(task.equalsIgnoreCase("back") ? "backtomenu" : "success");
        }

        reportForm.setInvoice(true);
        reportForm.setNonInvoice(true);
        reportForm.setStartAndBreakTime(true);
        // call on MatrixView without a parameter

        // no special task - prepare everything to show reports
        EmployeeViewHelper eh = new EmployeeViewHelper();
        Employeecontract ec = eh.getAndInitCurrentEmployee(request, employeeService, employeecontractService);

        Map<String, Object> results = matrixHelper.handleNoArgs(
                reportForm,
                ec,
                (Employeecontract) request.getSession().getAttribute("currentEmployeeContract"),
                (Long) request.getSession().getAttribute("currentEmployeeId"),
                (String) request.getSession().getAttribute("currentMonth"));
        return finishHandling(results, request, matrixHelper, mapping, doRefreshEmployeeSummaryData);
    }

    private ActionForward finishHandling(Map<String, Object> results, HttpServletRequest request, MatrixHelper mh, ActionMapping mapping,
        boolean doRefreshEmployeeSummaryData) {
        String errorValue = null;
        for (Entry<String, Object> entry : results.entrySet()) {
            if (mh.isHandlingError(entry.getKey())) {
                errorValue = (String) entry.getValue();
            } else {
                request.getSession().setAttribute(entry.getKey(), entry.getValue());
            }
        }
        if (errorValue != null) {
            request.setAttribute("errorMessage", errorValue);
            return mapping.findForward("error");
        }

        // check if vacation and overtime should be recalculated - see https://github.com/HBTGmbH/salat/issues/292
        if(doRefreshEmployeeSummaryData) {
            Employeecontract currentEmployeeContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            if(currentEmployeeContract != null) {
                refreshEmployeeSummaryData(request, currentEmployeeContract);
            }
        }

        return mapping.findForward("success");
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }

}
