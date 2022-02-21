package org.tb.action.dailyreport;

import static org.tb.util.DateUtils.getDateFormStrings;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.springframework.beans.factory.annotation.Autowired;
import org.tb.GlobalConstants;
import org.tb.action.LoginRequiredAction;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.Workingday;
import org.tb.helper.AfterLogin;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.persistence.WorkingdayDAO;
import org.tb.util.OptionItem;

public abstract class DailyReportAction<F extends ActionForm> extends LoginRequiredAction<F> {

    @Autowired
    private AfterLogin afterLogin;

    protected void addErrorAtTheBottom(HttpServletRequest request, ActionMessages errors, ActionMessage message) {
        errors.add("status", message);
        request.getSession().setAttribute("errors", true);
        saveErrors(request, errors);
    }

    /**
     * @return Returns the date associated the request. If parsing fails, the current date is returned.
     */
    protected Date getSelectedDateFromRequest(HttpServletRequest request) {
        String dayString = (String) request.getSession().getAttribute("currentDay");
        String monthString = (String) request.getSession().getAttribute("currentMonth");
        String yearString = (String) request.getSession().getAttribute("currentYear");

        return getDateFormStrings(dayString, monthString, yearString, true);
    }

    /**
     * Calculates the overtime and vaction and sets the attributes in the session.
     */
    protected void refreshVacationAndOvertime(HttpServletRequest request, Employeecontract employeecontract) {
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
        Date beginDate;
        Date endDate;

        /* make sure that the form is set in the http session, it could be a newly created object */
        request.getSession().setAttribute("showDailyReportForm", reportForm);

        try {

            switch (selectedView) {
                case GlobalConstants.VIEW_DAILY:
                    request.getSession().setAttribute("view", GlobalConstants.VIEW_DAILY);

                    beginDate = getDateFormStrings(reportForm.getDay(), reportForm.getMonth(), reportForm.getYear(), true);
                    endDate = beginDate;
                    break;
                case GlobalConstants.VIEW_MONTHLY:
                    request.getSession().setAttribute("view", GlobalConstants.VIEW_MONTHLY);
                    beginDate = getDateFormStrings("1", reportForm.getMonth(), reportForm.getYear(), true);
                    GregorianCalendar gc = new GregorianCalendar();
                    gc.setTime(beginDate);
                    int maxday = gc.getActualMaximum(Calendar.DAY_OF_MONTH);
                    String maxDayString = "";
                    if (maxday < 10) {
                        maxDayString += "0";
                    }
                    maxDayString += maxday;
                    endDate = getDateFormStrings(maxDayString, reportForm.getMonth(), reportForm.getYear(), true);
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

        } catch (Exception e) {
            throw new RuntimeException("date cannot be parsed for form", e);
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


        List<Timereport> timereports;
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

            if (suborders.stream().noneMatch(suborder -> suborder.getId() == reportForm.getSuborderId())) {
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
            Comparator<Timereport> comparator = (Comparator<Timereport>) request.getSession().getAttribute("timereportComparator");
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
            request.getSession().setAttribute("visibleworkingday", true);

            reportForm.setSelectedWorkHourBegin(workingday.getStarttimehour());
            reportForm.setSelectedWorkMinuteBegin(workingday.getStarttimeminute());
            reportForm.setSelectedBreakHour(workingday.getBreakhours());
            reportForm.setSelectedBreakMinute(workingday.getBreakminutes());
        } else {

            //don�t show break time, quitting time and working day ends on the showdailyreport.jsp
            request.getSession().setAttribute("visibleworkingday", false);

            reportForm.setSelectedWorkHourBegin(0);
            reportForm.setSelectedWorkMinuteBegin(0);
            reportForm.setSelectedBreakHour(0);
            reportForm.setSelectedBreakMinute(0);
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

        Date refDate = getDateFormStrings(dayString, monthString, yearString, true);

        Workingday workingday = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(refDate, ec.getId());
        if (workingday == null && nullPruefung) {
            workingday = new Workingday();
            workingday.setRefday(refDate);
            workingday.setEmployeecontract(ec);
        }
        return workingday;
    }

    protected List<OptionItem> getSerialDayList() {
        int maxDays = GlobalConstants.MAX_SERIAL_BOOKING_DAYS;
        List<OptionItem> days = new ArrayList<>();
        days.add(new OptionItem("0", "--"));
        for (int i = 1; i <= maxDays; i++) {
            String dayLabel;
            if (i < 10) {
                dayLabel = "0" + i;
            } else {
                dayLabel = "" + i;
            }
            String dayValue = "" + i;
            days.add(new OptionItem(dayValue, dayLabel));
        }
        return days;
    }

}
