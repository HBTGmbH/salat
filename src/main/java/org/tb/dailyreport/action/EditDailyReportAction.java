package org.tb.dailyreport.action;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.util.DateTimeUtils.getHoursToDisplay;
import static org.tb.common.util.DateTimeUtils.getSerialDayList;
import static org.tb.common.util.DateTimeUtils.getTimeReportHoursOptions;
import static org.tb.common.util.DateTimeUtils.getTimeReportMinutesOptions;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.service.TimereportService;
import org.tb.dailyreport.service.WorkingdayService;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Action class for editing of a timereport
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EditDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final TimereportService timereportService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final EmployeecontractService employeecontractService;
    private final WorkingdayService workingdayService;
    private final TimereportHelper timereportHelper;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) {
        long trId = Long.parseLong(request.getParameter("trId"));
        TimereportDTO tr = timereportService.getTimereportById(trId);

        // set collections
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));

        // fill the form with properties of the timereport to be edited
        setFormEntries(mapping, request, reportForm, tr);

        request.getSession().setAttribute("timereport", tr);
        request.getSession().setAttribute("currentEmployeeContract", employeecontractService.getEmployeecontractById(tr.getEmployeecontractId()));

        // save the filter settings
        request.getSession().setAttribute("lastCurrentDay", request.getSession().getAttribute("currentDay"));
        request.getSession().setAttribute("lastCurrentMonth", request.getSession().getAttribute("currentMonth"));
        request.getSession().setAttribute("lastCurrentYear", request.getSession().getAttribute("currentYear"));
        request.getSession().setAttribute("lastLastDay", request.getSession().getAttribute("lastDay"));
        request.getSession().setAttribute("lastLastMonth", request.getSession().getAttribute("lastMonth"));
        request.getSession().setAttribute("lastLastYear", request.getSession().getAttribute("lastYear"));
        request.getSession().setAttribute("lastOrder", request.getSession().getAttribute("currentOrder"));
        request.getSession().setAttribute("lastSuborderId", request.getSession().getAttribute("suborderFilerId"));
        request.getSession().setAttribute("lastView", request.getSession().getAttribute("view"));
        request.getSession().setAttribute("lastEmployeeContractId", reportForm.getEmployeeContractId());
        return mapping.findForward("success");
    }

    /**
     * fills the AddDailyReportForm with properties of the timereport to be edited
     */
    private void setFormEntries(ActionMapping mapping, HttpServletRequest request,
                                AddDailyReportForm reportForm, TimereportDTO tr) {

        Employeecontract ec = employeecontractService.getEmployeecontractById(tr.getEmployeecontractId());
        LocalDate utilDate = tr.getReferenceday();

        List<Customerorder> orders = customerorderService.getCustomerordersWithValidEmployeeOrders(ec.getId(), utilDate);
        List<Suborder> theSuborders = new ArrayList<>();
        if (orders != null && !orders.isEmpty()) {
            reportForm.setOrder(orders.getFirst().getSign());
            reportForm.setOrderId(orders.getFirst().getId());
            theSuborders = suborderService.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), tr.getCustomerorderId(), utilDate);
            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator.");
                mapping.findForward("error");
            }
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }

        List<Employeecontract> employeecontracts = employeecontractService.getTimeReportableEmployeeContractsForAuthorizedUser();
        request.getSession().setAttribute("employeecontracts", employeecontracts);

        /* set hours list in session in case of that the dialog is triggered from the welcome page */
        request.getSession().setAttribute("hours", getHoursToDisplay());

        request.getSession().setAttribute("trId", tr.getId());
        request.getSession().setAttribute("orders", orders);
        request.getSession().setAttribute("suborders", theSuborders);
        request.getSession().setAttribute("currentSuborderId", tr.getSuborderId());
        request.getSession().setAttribute("serialBookings", getSerialDayList());

        reportForm.reset(mapping, request);
        reportForm.setEmployeeContractId(ec.getId());

        reportForm.setReferenceday(DateUtils.format(utilDate));
        LocalDate reportDate = tr.getReferenceday();
        Workingday workingday = workingdayService.getWorkingday(ec.getId(), reportDate);

        boolean workingDayIsAvailable = false;
        if (workingday != null) {
            workingDayIsAvailable = true;
        }

        // workingday should only be available for today
        LocalDate today = DateUtils.today();
        if (!utilDate.equals(today)) {
            workingDayIsAvailable = false;
        }

        request.getSession().setAttribute("workingDayIsAvailable", workingDayIsAvailable);
        long[] displayTime = timereportHelper.determineTimesToDisplay(ec.getId(), reportDate, workingday, tr);

        if (workingDayIsAvailable) {
            reportForm.setSelectedHourBegin(displayTime[0]);
            reportForm.setSelectedMinuteBegin(displayTime[1]);
            reportForm.setSelectedHourEnd(displayTime[2]);
            reportForm.setSelectedMinuteEnd(displayTime[3]);
            reportForm.setSelectedHourBeginDay(workingday.getStarttimehour());
            reportForm.setSelectedMinuteBeginDay(workingday.getStarttimeminute());
            timereportHelper.refreshHours(reportForm);
        } else {
            reportForm.setSelectedHourDuration(tr.getDuration().toHours());
            reportForm.setSelectedMinuteDuration(tr.getDuration().toMinutesPart());
            reportForm.setSelectedHourBeginDay(DEFAULT_WORK_DAY_START);
            reportForm.setSelectedMinuteBeginDay(0);
        }

        reportForm.setSuborder(tr.getCompleteOrderSign());
        reportForm.setSuborderSignId(tr.getSuborderId());
        reportForm.setSuborderDescriptionId(tr.getSuborderId());
        reportForm.setOrder(tr.getCustomerorderSign());
        reportForm.setOrderId(tr.getCustomerorderId());
        reportForm.setStatus(tr.getStatus());
        reportForm.setComment(tr.getTaskdescription());
        reportForm.setTraining(tr.isTraining());
        reportForm.setId(tr.getId());
    }

    @Override
    protected boolean isAllowedForRestrictedUsers() {
        return true;
    }
}
