package org.tb.dailyreport.action;

import static java.lang.Boolean.TRUE;
import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.util.DateUtils.getDateFormStrings;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.auth.AfterLogin;
import org.tb.common.GlobalConstants;
import org.tb.common.struts.LoginRequiredAction;
import org.tb.common.util.DateUtils;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday.WorkingDayType;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.dailyreport.viewhelper.DailyReportViewHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.domain.Employeeorder;
import org.tb.order.persistence.EmployeeorderDAO;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;

public abstract class DailyReportAction<F extends ActionForm> extends LoginRequiredAction<F> {

    @Autowired
    private AfterLogin afterLogin;

    @Override
    public ActionForward execute(ActionMapping mapping, ActionForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {
        var actionForward = super.execute(mapping, form, request, response);
        initDailyReportViewHelper(request);
        return actionForward;
    }

    private void initDailyReportViewHelper(HttpServletRequest request) {
        boolean createTimereports = false;
        boolean editTimereports = false;
        boolean displayWorkingDay = false;
        boolean displayEmployeeInfo = false;
        boolean useFavorites = false;
        var currentContract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if(currentContract != null) {
            var loginContract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
            if(currentContract.getId().equals(loginContract.getId())) {
                createTimereports = true;
                editTimereports = true;
                displayWorkingDay = true;
                displayEmployeeInfo = true;
                useFavorites = true;
            } else if(authorizedUser.isManager()) {
                createTimereports = true;
                editTimereports = true;
                displayWorkingDay = true;
                displayEmployeeInfo = true;
            }
            if(TRUE.equals(currentContract.getFreelancer())) {
                displayWorkingDay = false;
                displayEmployeeInfo = false;
            }
        }

        var helper = new DailyReportViewHelper(createTimereports, editTimereports, displayWorkingDay, displayEmployeeInfo, useFavorites);
        request.getSession().setAttribute("dailyReportViewHelper", helper);
    }

    /**
     * @return Returns the date associated the request. If parsing fails, the current date is returned.
     */
    protected LocalDate getSelectedDateFromRequest(HttpServletRequest request) {
        String dayString = (String) request.getSession().getAttribute("currentDay");
        String monthString = (String) request.getSession().getAttribute("currentMonth");
        String yearString = (String) request.getSession().getAttribute("currentYear");

        return getDateFormStrings(dayString, monthString, yearString, true);
    }

    /**
     * Calculates the overtime and vaction and sets the attributes in the session.
     */
    protected void refreshEmployeeSummaryData(HttpServletRequest request, Employeecontract employeecontract) {
        afterLogin.handleOvertime(employeecontract, request.getSession());

        // release
        request.getSession().setAttribute("releaseWarning", employeecontract.getReleaseWarning());
        request.getSession().setAttribute("acceptanceWarning", employeecontract.getAcceptanceWarning());

        String releaseDate = employeecontract.getReportReleaseDateString();
        String acceptanceDate = employeecontract.getReportAcceptanceDateString();

        request.getSession().setAttribute("releasedUntil", releaseDate);
        request.getSession().setAttribute("acceptedUntil", acceptanceDate);
    }

    /**
     * Refreshes the list of timereports and all session attributes, that depend on the list of timereports.
     *
     * @return Returns true, if refreshing was succesful.
     */
    protected boolean refreshTimereports(HttpServletRequest request, ShowDailyReportForm reportForm, CustomerorderDAO customerorderDAO,
                                         TimereportDAO timereportDAO, EmployeecontractDAO employeecontractDAO, SuborderDAO suborderDAO,
                                         EmployeeorderDAO employeeorderDAO) {

        //selected view and selected dates
        String selectedView = reportForm.getView();
        if (selectedView == null) {
            selectedView = GlobalConstants.VIEW_DAILY;
        }
        LocalDate beginDate;
        LocalDate endDate;

        /* make sure that the form is set in the http session, it could be a newly created object */
        request.getSession().setAttribute("showDailyReportForm", reportForm);

        switch (selectedView) {
            case GlobalConstants.VIEW_DAILY:
                request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);

                beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                endDate = beginDate;
                break;
            case GlobalConstants.VIEW_MONTHLY:
                request.getSession().setAttribute("view", GlobalConstants.VIEW_MONTHLY);
                beginDate = getDateFormStrings("1", reportForm.getMonth(), reportForm.getYear(), true);
                endDate = DateUtils.getEndOfMonth(beginDate);
                break;
            case GlobalConstants.VIEW_CUSTOM:
                request.getSession().setAttribute("view", GlobalConstants.VIEW_CUSTOM);
                beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                if (reportForm.getLastday() == null || reportForm.getLastmonth() == null || reportForm.getLastyear() == null) {
                    reportForm.setLastday(reportForm.getDay());
                    reportForm.setLastmonth(reportForm.getMonth());
                    reportForm.setLastyear(reportForm.getYear());
                }
                endDate = getDateFormStrings(reportForm.getLastday(), reportForm.getLastmonth(), reportForm.getLastyear(), true);
                break;
            default:
                throw new RuntimeException("no view type selected");
        }

        // test, if an order is select, the selected employee is not associated with
        long employeeContractId = reportForm.getEmployeeContractId();
        if (employeeContractId != 0 && employeeContractId != -1) {
            String selectedOrder = reportForm.getOrder();
            Customerorder order = customerorderDAO.getCustomerorderBySign(selectedOrder);
            List<Employeeorder> employeeOrders = null;
            if (order != null) {
                employeeOrders = employeeorderDAO.getEmployeeordersByOrderIdAndEmployeeContractId(order.getId(), employeeContractId);
            }
            if (employeeOrders == null || employeeOrders.isEmpty()) {
                reportForm.setOrder(GlobalConstants.ALL_ORDERS);
            }
        }

        Employeecontract ec;
        if (reportForm.getEmployeeContractId() != -1) {
            // consider timereports for specific employee
            ec = employeecontractDAO.getEmployeeContractById(employeeContractId);
            if (ec == null) {
                request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
                return false;
            }
            request.getSession().setAttribute("overtimeDisabled", false);
        } else {
            // consider timereports for all employees
            ec = null;
            request.getSession().setAttribute("overtimeDisabled", true);
        }


        List<TimereportDTO> timereports;
        List<Customerorder> orders = ec == null ? customerorderDAO.getCustomerorders() : customerorderDAO.getCustomerordersByEmployeeContractId(ec.getId());
        request.getSession().setAttribute("orders", orders);

        if (reportForm.getOrder() == null || reportForm.getOrder().equals(GlobalConstants.ALL_ORDERS)) {
            // get the timereports for specific date, all orders
            timereports = ec == null
                    ? timereportDAO.getTimereportsByDates(beginDate, endDate)  // all employees
                    : timereportDAO.getTimereportsByDatesAndEmployeeContractId(ec.getId(), beginDate, endDate); // specific employee

        } else {
            Customerorder co = customerorderDAO.getCustomerorderBySign(reportForm.getOrder());
            long orderId = co.getId();
            List<Suborder> suborders = ec == null
                    ? suborderDAO.getSubordersByCustomerorderId(orderId, reportForm.getShowOnlyValid())
                    : suborderDAO.getSubordersByEmployeeContractIdAndCustomerorderId(ec.getId(), orderId, reportForm.getShowOnlyValid());
            request.getSession().setAttribute("suborders", suborders);

            if (suborders.stream().noneMatch(suborder -> Objects.equals(suborder.getId(), reportForm.getSuborderId()))) {
                reportForm.setSuborderId(-1);
            }

            if (reportForm.getSuborderId() == 0 || reportForm.getSuborderId() == -1) {
                // get the timereports for specific date, specific order
                timereports = ec == null
                        ? timereportDAO.getTimereportsByDatesAndCustomerOrderId(beginDate, endDate, orderId) //all employees
                        : timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(ec.getId(), beginDate, endDate, orderId); // specific employee
            } else {
                timereports = ec == null
                        ? timereportDAO.getTimereportsByDatesAndSuborderId(beginDate, endDate, reportForm.getSuborderId()) //all employees
                        : timereportDAO.getTimereportsByDatesAndEmployeeContractIdAndSuborderId(ec.getId(), beginDate, endDate, reportForm.getSuborderId()); // specific employee
            }
        }

        // set timereports in session
        if (request.getSession().getAttribute("timereportComparator") != null) {
            @SuppressWarnings("unchecked")
            Comparator<TimereportDTO> comparator = (Comparator<TimereportDTO>) request.getSession().getAttribute("timereportComparator");
            timereports.sort(comparator);
        }
        request.getSession().setAttribute("timereports", timereports);
        request.getSession().setAttribute("currentSuborderId", reportForm.getSuborderId());

        // refresh all relevant attributes
        if (reportForm.getEmployeeContractId() == -1) {
            request.getSession().setAttribute("currentEmployee", GlobalConstants.ALL_EMPLOYEES);
            request.getSession().setAttribute("currentEmployeeContract", null);
            request.getSession().setAttribute("currentEmployeeId", -1L);
        } else {
            Employeecontract employeecontract = employeecontractDAO.getEmployeeContractById(reportForm.getEmployeeContractId());
            request.getSession().setAttribute("currentEmployee", employeecontract.getEmployee().getName());
            request.getSession().setAttribute("currentEmployeeContract", employeecontract);
            request.getSession().setAttribute("currentEmployeeId", employeecontract.getEmployee().getId());
        }

        if (reportForm.getOrder() == null || reportForm.getOrder().equals("ALL ORDERS")) {
            request.getSession().setAttribute("currentOrder", GlobalConstants.ALL_ORDERS);
        } else {
            request.getSession().setAttribute("currentOrder", reportForm.getOrder());
        }
        request.getSession().setAttribute("currentDay", reportForm.getDay());
        request.getSession().setAttribute("currentMonth", reportForm.getMonth());
        request.getSession().setAttribute("currentYear", reportForm.getYear());
        request.getSession().setAttribute("startdate", reportForm.getStartdate());

        request.getSession().setAttribute("lastDay", reportForm.getLastday());
        request.getSession().setAttribute("lastMonth", reportForm.getLastmonth());
        request.getSession().setAttribute("lastYear", reportForm.getLastyear());
        request.getSession().setAttribute("enddate", reportForm.getEnddate());

        request.getSession().setAttribute("reportForm", reportForm);

        return true;
    }

    /**
     * Refreshes the workingday.
     */
    protected Workingday refreshWorkingday(ShowDailyReportForm reportForm, HttpServletRequest request, WorkingdayDAO workingdayDAO)
            throws Exception {

        Employeecontract employeecontract = getEmployeeContractFromRequest(request);
        if (employeecontract == null) {
            request.setAttribute("errorMessage",
                    "No employee contract found for employee - please call system administrator.");
            throw new Exception("No employee contract found for employee");
        }
        //new parameter
        Workingday workingday = getWorkingdayForReportformAndEmployeeContract(reportForm, employeecontract, workingdayDAO, false);

        // save values from the data base into form-bean, when working day != null
        if (workingday != null) {

            //show break time, quitting time and working day ends on the showdailyreport.jsp
            request.getSession().setAttribute("visibleworkingday", workingday.getType() != WorkingDayType.NOT_WORKED);

            reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
            reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
            reportForm.setSelectedBreakHour(workingday.getBreakhours());
            reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
            reportForm.setWorkingDayTypeTyped(workingday.getType());
        } else {

            //don't show break time, quitting time and working day ends on the showdailyreport.jsp
            request.getSession().setAttribute("visibleworkingday", false);

            reportForm.setSelectedWorkHourBegin(DEFAULT_WORK_DAY_START);
            reportForm.setSelectedWorkMinuteBegin(0);
            reportForm.setSelectedBreakHour(0);
            reportForm.setSelectedBreakMinute(0);
            reportForm.setWorkingDayTypeTyped(WorkingDayType.WORKED);
        }
        return workingday;
    }

    protected Employeecontract getEmployeeContractFromRequest(HttpServletRequest request) {
        Employeecontract ec = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (ec == null) {
            ec = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        }
        return ec;
    }

    /**
     * @return Returns the adequate {@link Workingday} for the selected date in the reportForm and the given
     * {@link Employeecontract}. If this workingday does not exist in the database so far, a new one is created.
     */
    // getWorkingdayForReportformAndEmployeeContract have a new parameter, boolean
    protected Workingday getWorkingdayForReportformAndEmployeeContract(ShowDailyReportForm reportForm, Employeecontract ec, WorkingdayDAO workingdayDAO, boolean nullPruefung) {
        String dayString = reportForm.getDay();
        String monthString = reportForm.getMonth();
        String yearString = reportForm.getYear();

        LocalDate refDate = getDateFormStrings(dayString, monthString, yearString, true);

        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
        if (workingday == null && nullPruefung) {
            workingday = new Workingday();
            workingday.setRefday(refDate);
            workingday.setEmployeecontract(ec);
        }
        return workingday;
    }

}
