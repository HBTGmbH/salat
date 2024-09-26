package org.tb.dailyreport.action;

import static org.tb.common.DateTimeViewHelper.getTimeReportHoursOptions;
import static org.tb.common.DateTimeViewHelper.getHoursToDisplay;
import static org.tb.common.DateTimeViewHelper.getTimeReportMinutesOptions;
import static org.tb.common.DateTimeViewHelper.getSerialDayList;
import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.TimereportHelper;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

/**
 * Action class for editing of a timereport
 *
 * @author oda
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EditDailyReportAction extends DailyReportAction<AddDailyReportForm> {

    private final TimereportDAO timereportDAO;
    private final CustomerorderDAO customerorderDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final WorkingdayDAO workingdayDAO;
    private final TimereportHelper timereportHelper;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, AddDailyReportForm reportForm, HttpServletRequest request, HttpServletResponse response) {
        long trId = Long.parseLong(request.getParameter("trId"));
        TimereportDTO tr = timereportDAO.getTimereportById(trId);

        // set collections
        request.getSession().setAttribute("hoursDuration", getTimeReportHoursOptions());
        request.getSession().setAttribute("minutes", getTimeReportMinutesOptions(reportForm.isShowAllMinutes()));

        // make sure that overtimeCompensation is set in the session so that the duration-dropdown-menu will be disabled
        // if the current suborder is overtime compensation.
        if (request.getSession().getAttribute("overtimeCompensation") == null
            || !Objects.equals(request.getSession().getAttribute("overtimeCompensation"), GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION)) {
            request.getSession().setAttribute("overtimeCompensation", GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION);
        }

        // fill the form with properties of the timereport to be edited
        setFormEntries(mapping, request, reportForm, tr);

        request.getSession().setAttribute("timereport", tr);
        request.getSession().setAttribute("currentEmployeeContract", employeecontractDAO.getEmployeeContractById(tr.getEmployeecontractId()));

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

        Employeecontract ec = employeecontractDAO.getEmployeeContractById(tr.getEmployeecontractId());
        LocalDate utilDate = tr.getReferenceday();

        List<Customerorder> orders = customerorderDAO.getCustomerordersWithValidEmployeeOrders(ec.getId(), utilDate);
        List<Suborder> theSuborders = new ArrayList<>();
        if (orders != null && !orders.isEmpty()) {
            reportForm.setOrder(orders.getFirst().getSign());
            reportForm.setOrderId(orders.getFirst().getId());
            theSuborders = suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(ec.getId(), tr.getCustomerorderId(), utilDate);
            if (theSuborders == null || theSuborders.isEmpty()) {
                request.setAttribute("errorMessage", "Orders/suborders inconsistent for employee - please call system administrator.");
                mapping.findForward("error");
            }
        } else {
            request.setAttribute("errorMessage", "no orders found for employee - please call system administrator.");
            mapping.findForward("error");
        }

        Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
        List<Employeecontract> employeecontracts = employeecontractDAO.getTimeReportableEmployeeContractsForAuthorizedUser();
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
        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(reportDate, ec.getId());

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

        reportForm.setSuborder(tr.getSuborderSign());
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
