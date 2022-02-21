package org.tb.action.invoice;

import static org.tb.util.DateUtils.getDateFormStrings;
import static org.tb.util.TimeFormatUtils.decimalFormatMinutes;
import static org.tb.util.TimeFormatUtils.timeFormatMinutes;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.springframework.stereotype.Component;
import org.tb.GlobalConstants;
import org.tb.action.dailyreport.DailyReportAction;
import org.tb.bdom.Customerorder;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.bdom.Timereport;
import org.tb.bdom.comparators.SubOrderComparator;
import org.tb.helper.EmployeeHelper;
import org.tb.helper.InvoiceSuborderHelper;
import org.tb.helper.InvoiceTimereportHelper;
import org.tb.persistence.CustomerorderDAO;
import org.tb.persistence.EmployeeDAO;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.persistence.TimereportDAO;
import org.tb.util.DateUtils;
import org.tb.util.ExcelArchivierer;

@Component
@RequiredArgsConstructor
public class ShowInvoiceAction extends DailyReportAction<ShowInvoiceForm> {

    private final CustomerorderDAO customerorderDAO;
    private final TimereportDAO timereportDAO;
    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowInvoiceForm showInvoiceForm, HttpServletRequest request, HttpServletResponse response) {

        // check if special tasks initiated from the daily display need to be
        // carried out...

        Map<String, String> monthMap = new HashMap<>();
        monthMap.put("0", "main.timereport.select.month.jan.text");
        monthMap.put("1", "main.timereport.select.month.feb.text");
        monthMap.put("2", "main.timereport.select.month.mar.text");
        monthMap.put("3", "main.timereport.select.month.apr.text");
        monthMap.put("4", "main.timereport.select.month.may.text");
        monthMap.put("5", "main.timereport.select.month.jun.text");
        monthMap.put("6", "main.timereport.select.month.jul.text");
        monthMap.put("7", "main.timereport.select.month.aug.text");
        monthMap.put("8", "main.timereport.select.month.sep.text");
        monthMap.put("9", "main.timereport.select.month.oct.text");
        monthMap.put("10", "main.timereport.select.month.nov.text");
        monthMap.put("11", "main.timereport.select.month.dec.text");

        // call on InvoiceView with parameter refreshInvoiceForm to update
        // request
        if (request.getParameter("task") != null && request.getParameter("task").equals("generateMaximumView")) {
            String selectedView = showInvoiceForm.getInvoiceview();
            List<InvoiceSuborderHelper> invoiceSuborderViewHelperList = new LinkedList<>();
            List<Suborder> suborderList;
            Customerorder customerOrder;
            List<Suborder> suborderListTemp = new LinkedList<>();
            Date dateFirst;
            Date dateLast;
            if (!showInvoiceForm.getOrder().equals("CHOOSE ORDER")) {
                if (selectedView.equals(GlobalConstants.VIEW_MONTHLY) || selectedView.equals(GlobalConstants.VIEW_WEEKLY)) {
                    // generate dates for monthly view mode
                    try {
                        if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
                            dateFirst = getDateFormStrings("1", showInvoiceForm.getFromMonth(), showInvoiceForm.getFromYear(), false);
                            dateLast = DateUtils.getEndOfMonth(dateFirst);
                        } else {
                            int week = showInvoiceForm.getFromWeek();
                            int year = Integer.parseInt(showInvoiceForm.getFromYear());
                            dateFirst = DateUtils.getBeginOfWeek(year, week);
                            dateLast = DateUtils.addDays(dateFirst, 6);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("date cannot be parsed from form");
                    }

                    customerOrder = customerorderDAO.getCustomerorderBySign(showInvoiceForm.getOrder());
                    if (showInvoiceForm.getSuborder().equals("ALL SUBORDERS")) {
                        suborderList = suborderDAO.getSubordersByCustomerorderId(customerOrder.getId(), false);
                    } else {
                        suborderList = suborderDAO.getSuborderById(Long.parseLong(showInvoiceForm.getSuborder())).getAllChildren();
                    }
                    suborderList.sort(SubOrderComparator.INSTANCE);
                    // remove suborders that are not valid sometime between dateFirst and dateLast
                    suborderList.removeIf(so -> so.getFromDate().after(dateLast) || so.getUntilDate() != null && so.getUntilDate().before(dateFirst));
                } else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
                    // generate dates for a period of time in custom view mode
                    try {
                        dateFirst = getDateFormStrings(showInvoiceForm.getFromDay(), showInvoiceForm.getFromMonth(), showInvoiceForm.getFromYear(), false);
                        if (showInvoiceForm.getUntilDay() == null || showInvoiceForm.getUntilMonth() == null || showInvoiceForm.getUntilYear() == null) {
                            int maxday = DateUtils.getMonthDays(dateFirst);
                            String maxDayString = "";
                            if (maxday < 10) {
                                maxDayString += "0";
                            }
                            maxDayString += maxday;
                            showInvoiceForm.setUntilDay(maxDayString);
                            showInvoiceForm.setUntilMonth(showInvoiceForm.getFromMonth());
                            showInvoiceForm.setUntilYear(showInvoiceForm.getFromYear());
                        }
                        dateLast = getDateFormStrings(showInvoiceForm.getUntilDay(), showInvoiceForm.getUntilMonth(), showInvoiceForm.getUntilYear(), false);
                    } catch (Exception e) {
                        throw new RuntimeException("date cannot be parsed from form");
                    }
                    customerOrder = customerorderDAO.getCustomerorderBySign(showInvoiceForm.getOrder());
                    if (showInvoiceForm.getSuborder().equals("ALL SUBORDERS")) {
                        suborderList = suborderDAO.getSubordersByCustomerorderId(customerOrder.getId(), false);
                    } else {
                        suborderList = suborderDAO.getSuborderById(Long.parseLong(showInvoiceForm.getSuborder())).getAllChildren();
                    }
                    // remove suborders that are not valid sometime between dateFirst and dateLast
                    suborderList.removeIf(so -> so.getFromDate().after(dateLast) || so.getUntilDate() != null && so.getUntilDate().before(dateFirst));
                    suborderList.sort(SubOrderComparator.INSTANCE);
                } else {
                    throw new RuntimeException("no view type selected");
                }
                // include suborders according to selection (nicht fakturierbar oder Festpreis mit einbeziehen oder nicht) for calculating targethoursum
                if (showInvoiceForm.isInvoicebox() && showInvoiceForm.isFixedpricebox()) {
                    request.getSession().setAttribute("targethourssum", fillViewHelper(suborderList, invoiceSuborderViewHelperList, dateFirst, dateLast, showInvoiceForm));
                } else if (showInvoiceForm.isFixedpricebox()) {
                    for (Suborder suborder : suborderList) {
                        if (suborder.getInvoice() == 'Y' || suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }
                    request.getSession().setAttribute("targethourssum", fillViewHelper(suborderListTemp, invoiceSuborderViewHelperList, dateFirst, dateLast, showInvoiceForm));
                } else if (showInvoiceForm.isInvoicebox()) {
                    for (Suborder suborder : suborderList) {
                        if (!suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }
                    request.getSession().setAttribute("targethourssum", fillViewHelper(suborderListTemp, invoiceSuborderViewHelperList, dateFirst, dateLast, showInvoiceForm));
                } else {
                    for (Suborder suborder : suborderList) {
                        if (suborder.getInvoice() == 'Y' && !suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }
                    request.getSession().setAttribute("targethourssum", fillViewHelper(suborderListTemp, invoiceSuborderViewHelperList, dateFirst, dateLast, showInvoiceForm));
                }
                request.getSession().setAttribute("viewhelpers", invoiceSuborderViewHelperList);
                request.getSession().setAttribute("customername", customerOrder.getCustomer().getName());
                request.getSession().setAttribute("customeraddress", customerOrder.getCustomer().getAddress());
                YearMonth yearMonth = DateUtils.getYearMonth(dateFirst);
                request.getSession().setAttribute("dateMonth", monthMap.get(String.valueOf(yearMonth.getMonthValue() - 1)));
                request.getSession().setAttribute("dateYear", yearMonth.getYear());
                request.getSession().setAttribute("dateFirst", DateUtils.format(dateFirst));
                request.getSession().setAttribute("dateLast", DateUtils.format(dateLast));
                request.getSession().setAttribute("currentOrderObject", customerOrder);
            } else {
                request.setAttribute("errorMessage", "No customer order selected. Please choose.");
            }
            return mapping.findForward("success");
        } else if (request.getParameter("task") != null && request.getParameter("task").equals("refreshInvoiceForm")) {
            // call on InvoiceView with parameter refreshInvoceForm to update
            // request
            if (showInvoiceForm.getOrder() == null || showInvoiceForm.getOrder().equals("CHOOSE ORDER")) {
                request.getSession().setAttribute("currentOrder", "main.invoice.choose.text");
            } else {
                request.getSession().setAttribute("currentOrder", showInvoiceForm.getOrder());
                request.getSession().setAttribute("currentSuborder", showInvoiceForm.getSuborder());
                List<Suborder> suborders = suborderDAO.getSubordersByCustomerorderId(customerorderDAO.getCustomerorderBySign(showInvoiceForm.getOrder()).getId(), showInvoiceForm.getShowOnlyValid());
                suborders.sort(SubOrderComparator.INSTANCE);

                request.getSession().setAttribute("suborders", suborders);
            }

            /*
             * Delete resultset if the customerorder of the invoice form has
             * changed if(request.getSession().getAttribute("viewhelpers") !=
             * null){ List<InvoiceSuborderViewHelper>
             * invoiceSuborderViewHelperList = (List<InvoiceSuborderViewHelper>)
             * request.getSession().getAttribute("viewhelpers");
             * invoiceSuborderViewHelperList.get(0).getParentorder().equals(customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder())); }
             */

            // activate subcheckboxes for timereport-attributes
            if (showInvoiceForm.isTimereportsbox()) {
                request.getSession().setAttribute("timereportsubboxes", true);
            } else {
                request.getSession().setAttribute("timereportsubboxes", false);
                showInvoiceForm.setTimereportdescriptionbox(false);
                showInvoiceForm.setEmployeesignbox(false);
            }

            // selected view
            String selectedView = showInvoiceForm.getInvoiceview();
            if (selectedView.equals(GlobalConstants.VIEW_MONTHLY)) {
                request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_MONTHLY);
            } else if (selectedView.equals(GlobalConstants.VIEW_CUSTOM)) {
                request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_CUSTOM);
            } else if (selectedView.equals(GlobalConstants.VIEW_WEEKLY)) {
                request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_WEEKLY);
            } else {
                throw new RuntimeException("no view type selected");
            }
            request.getSession().setAttribute("customeridbox", showInvoiceForm.isCustomeridbox());
            request.getSession().setAttribute("targethoursbox", showInvoiceForm.isTargethoursbox());
            request.getSession().setAttribute("actualhoursbox", showInvoiceForm.isActualhoursbox());
            request.getSession().setAttribute("employeesignbox", showInvoiceForm.isEmployeesignbox());
            request.getSession().setAttribute("timereportdescriptionbox", showInvoiceForm.isTimereportdescriptionbox());
            request.getSession().setAttribute("timereportsbox", showInvoiceForm.isTimereportsbox());
            request.getSession().setAttribute("currentDay", showInvoiceForm.getFromDay());
            request.getSession().setAttribute("currentMonth", showInvoiceForm.getFromMonth());
            request.getSession().setAttribute("currentYear", showInvoiceForm.getFromYear());
            request.getSession().setAttribute("currentWeek", showInvoiceForm.getFromWeek());
            request.getSession().setAttribute("weeks", DateUtils.getWeeksToDisplay(showInvoiceForm.getFromYear()));
            request.getSession().setAttribute("lastDay", showInvoiceForm.getUntilDay());
            request.getSession().setAttribute("lastMonth", showInvoiceForm.getUntilMonth());
            request.getSession().setAttribute("lastYear", showInvoiceForm.getUntilYear());
            request.getSession().setAttribute("optionmwst", showInvoiceForm.getMwst());
            request.getSession().setAttribute("optionsuborderdescription", showInvoiceForm.getSuborderdescription());
            request.getSession().setAttribute("layerlimit", showInvoiceForm.getLayerlimit());
            request.getSession().setAttribute("customername", showInvoiceForm.getCustomername());
            String customeraddress = showInvoiceForm.getCustomeraddress();
            request.getSession().setAttribute("customeraddress", customeraddress);
            return mapping.findForward("success");
        } else if (request.getParameter("task") != null
                && (request.getParameter("task").equals("print") || request.getParameter("task").equals("export") || request.getParameter("task").equals("exportNew"))) {
            // call on InvoiceView with parameter print
            List<InvoiceSuborderHelper> suborderViewhelperList = (List<InvoiceSuborderHelper>) request.getSession().getAttribute("viewhelpers");
            // reset visibility to false
            for (InvoiceSuborderHelper invoiceSuborderViewHelper : suborderViewhelperList) {
                invoiceSuborderViewHelper.setVisible(false);
                for (InvoiceTimereportHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList()) {
                    invoiceTimereportViewHelper.setVisible(false);
                }
            }
            // set visibility to true if found in arrays
            String[] suborderIds = showInvoiceForm.getSuborderIdArray();
            String[] timereportIds = showInvoiceForm.getTimereportIdArray();
            for (InvoiceSuborderHelper invoiceSuborderViewHelper : suborderViewhelperList) {
                for (String suborderId : suborderIds) {
                    if (Long.parseLong(suborderId) == invoiceSuborderViewHelper.getId()) {
                        invoiceSuborderViewHelper.setVisible(true);
                        break;
                    }
                }
                for (InvoiceTimereportHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList()) {
                    for (String timereportId : timereportIds) {
                        if (Long.parseLong(timereportId) == invoiceTimereportViewHelper.getId()) {
                            invoiceTimereportViewHelper.setVisible(true);
                            break;
                        }
                    }
                }
            }
            long actualMinutesSum = 0;
            int layerlimit = Integer.parseInt(showInvoiceForm.getLayerlimit());
            for (InvoiceSuborderHelper invoiceSuborderViewHelper : suborderViewhelperList) {
                if (invoiceSuborderViewHelper.getLayer() <= layerlimit
                        || showInvoiceForm.getLayerlimit().equals("-1")) {
                    if (invoiceSuborderViewHelper.isVisible()) {
                        if (invoiceSuborderViewHelper.getLayer() < layerlimit
                                || showInvoiceForm.getLayerlimit().equals("-1")) {
                            actualMinutesSum += invoiceSuborderViewHelper.getTotalActualminutesPrint();
                        } else {
                            actualMinutesSum += invoiceSuborderViewHelper.getDurationInMinutes();
                        }
                    }
                }
            }
            request.getSession().setAttribute("actualminutessum", (double) actualMinutesSum);
            request.getSession().setAttribute("printactualhourssum", timeFormatMinutes(actualMinutesSum) + " (" + decimalFormatMinutes(actualMinutesSum) + ")");
            request.getSession().setAttribute("titleactualhourstext", showInvoiceForm.getTitleactualhourstext());
            request.getSession().setAttribute("titlecustomersigntext", showInvoiceForm.getTitlecustomersigntext());
            request.getSession().setAttribute("titleinvoiceattachment", showInvoiceForm.getTitleinvoiceattachment());
            request.getSession().setAttribute("titledatetext", showInvoiceForm.getTitledatetext());
            request.getSession().setAttribute("titledescriptiontext", showInvoiceForm.getTitledescriptiontext());
            request.getSession().setAttribute("titleemployeesigntext", showInvoiceForm.getTitleemployeesigntext());
            request.getSession().setAttribute("titlesubordertext", showInvoiceForm.getTitlesubordertext());
            request.getSession().setAttribute("titletargethourstext", showInvoiceForm.getTitletargethourstext());
            request.getSession().setAttribute("suborderdescription", showInvoiceForm.getSuborderdescription());
            request.getSession().setAttribute("customername", showInvoiceForm.getCustomername());
            String customeraddress = showInvoiceForm.getCustomeraddress();
            customeraddress = customeraddress.replace("\r\n", "<br/>");
            customeraddress = customeraddress.replace("\n", "<br/>");
            customeraddress = customeraddress.replace("\r", "<br/>");
            request.getSession().setAttribute("customeraddress", customeraddress);
            String task = request.getParameter("task");
            if (task.equals("print")) {
                return mapping.findForward("print");
            } else if (task.equals("export")) {
                MessageResources messageResources = getResources(request);
                request.getSession().setAttribute("overall", messageResources.getMessage("main.invoice.overall.text"));
                ExcelArchivierer.exportInvoice(showInvoiceForm, request, response, ExcelArchivierer.getHSSFFactory());
                request.getSession().removeAttribute("overall");
                return null;
            } else if (task.equals("exportNew")) {
                MessageResources messageResources = getResources(request);
                request.getSession().setAttribute("overall", messageResources.getMessage("main.invoice.overall.text"));
                ExcelArchivierer.exportInvoice(showInvoiceForm, request, response, ExcelArchivierer.getXSSFFactory());
                request.getSession().removeAttribute("overall");
                return null;
            }
        } else if (request.getParameter("task") != null) {
            // END
            // call on InvoiceView with any parameter to forward or go back
            // just go back to main menu
            if (request.getParameter("task").equalsIgnoreCase("back")) {
                return mapping.findForward("backtomenu");
            } else {
                return mapping.findForward("success");
            }
        } else if (request.getParameter("task") == null) {
            // call on invoiceView without a parameter
            // no special task - prepare everything to show invoice
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            EmployeeHelper eh = new EmployeeHelper();
            Employeecontract ec = eh.setCurrentEmployee(loginEmployee, request, employeeDAO, employeecontractDAO);
            if (ec == null) {
                request.setAttribute("errorMessage", "No employee contract found for employee - please call system administrator.");
                return mapping.findForward("error");
            }
            request.getSession().setAttribute("days", DateUtils.getDaysToDisplay());
            request.getSession().setAttribute("years", DateUtils.getYearsToDisplay());
            request.getSession().setAttribute("weeks", DateUtils.getWeeksToDisplay(showInvoiceForm.getFromYear()));
            request.getSession().setAttribute("orders", customerorderDAO.getCustomerorders());
            request.getSession().setAttribute("suborders", new LinkedList<Suborder>());
            request.getSession().setAttribute("optionmwst", "19");
            request.getSession().setAttribute("layerlimit", "-1");
            // selected view and selected dates
            if (showInvoiceForm.getFromDay() == null || showInvoiceForm.getFromMonth() == null || showInvoiceForm.getFromYear() == null) {
                // set standard dates and view
                Date today = DateUtils.today();
                showInvoiceForm.setFromDay("01");
                showInvoiceForm.setFromMonth(DateUtils.getMonthShortString(today));
                showInvoiceForm.setFromYear(DateUtils.getYearString(today));
                showInvoiceForm.setUntilDay(Integer.toString(DateUtils.getLastDayOfMonth(today)));
                showInvoiceForm.setUntilMonth(DateUtils.getMonthShortString(today));
                showInvoiceForm.setUntilYear(DateUtils.getYearString(today));
                request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_MONTHLY);
                showInvoiceForm.setInvoiceview(GlobalConstants.VIEW_MONTHLY);
            }
            MessageResources messageResources = getResources(request);
            showInvoiceForm.setTitleactualhourstext(messageResources.getMessage("main.invoice.title.actualhours.text"));
            showInvoiceForm.setTitlecustomersigntext(messageResources.getMessage("main.invoice.title.customersign.text"));
            showInvoiceForm.setTitledatetext(messageResources.getMessage("main.invoice.title.date.text"));
            showInvoiceForm.setTitledescriptiontext(messageResources.getMessage("main.invoice.title.description.text"));
            showInvoiceForm.setTitleemployeesigntext(messageResources.getMessage("main.invoice.title.employeesign.text"));
            showInvoiceForm.setTitlesubordertext(messageResources.getMessage("main.invoice.title.suborder.text"));
            showInvoiceForm.setTitletargethourstext(messageResources.getMessage("main.invoice.title.targethours.text"));
            showInvoiceForm.setTitleinvoiceattachment(messageResources.getMessage("main.invoice.addresshead.text"));
            request.getSession().setAttribute("currentDay", showInvoiceForm.getFromDay());
            request.getSession().setAttribute("currentMonth", showInvoiceForm.getFromMonth());
            request.getSession().setAttribute("currentYear", showInvoiceForm.getFromYear());
            request.getSession().setAttribute("currentWeek", showInvoiceForm.getFromWeek());
            request.getSession().setAttribute("lastDay", showInvoiceForm.getUntilDay());
            request.getSession().setAttribute("lastMonth", showInvoiceForm.getUntilMonth());
            request.getSession().setAttribute("lastYear", showInvoiceForm.getUntilYear());
            request.getSession().removeAttribute("viewhelpers");
            showInvoiceForm.setShowOnlyValid(true);
        }
        return mapping.findForward("success");
    }

    private String fillViewHelper(List<Suborder> suborderList, List<InvoiceSuborderHelper> invoiceSuborderViewHelperList, Date dateFirst, Date dateLast,
                                  ShowInvoiceForm invoiceForm) {
        List<String> suborderIdList = new ArrayList<>(suborderList.size());
        List<String> timereportIdList = new ArrayList<>();
        for (Suborder suborder : suborderList) {
            List<InvoiceTimereportHelper> invoiceTimereportViewHelperList = new LinkedList<>();
            List<Timereport> timereportList = timereportDAO.getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(dateFirst, dateLast, suborder.getId());
            for (Timereport timereport : timereportList) {
                InvoiceTimereportHelper invoiceTimereportViewHelper = new InvoiceTimereportHelper(timereport);
                invoiceTimereportViewHelperList.add(invoiceTimereportViewHelper);
                timereportIdList.add(String.valueOf(invoiceTimereportViewHelper.getId()));
            }
            InvoiceSuborderHelper newInvoiceSuborderViewHelper = new InvoiceSuborderHelper(suborder, timereportDAO, dateFirst, dateLast, invoiceForm.isInvoicebox());
            newInvoiceSuborderViewHelper.setInvoiceTimereportViewHelperList(invoiceTimereportViewHelperList);
            Pattern p = Pattern.compile("\\.");
            Matcher m = p.matcher(suborder.getSign());
            int counter = 0;
            while (m.find()) {
                counter++;
            }
            newInvoiceSuborderViewHelper.setLayer(counter);
            invoiceSuborderViewHelperList.add(newInvoiceSuborderViewHelper);
            suborderIdList.add(String.valueOf(newInvoiceSuborderViewHelper.getId()));
        }
        invoiceForm.setSuborderIdArray(suborderIdList.toArray(new String[0]));
        invoiceForm.setTimereportIdArray(timereportIdList.toArray(new String[0]));
        long totalActualminutes = 0;
        for (InvoiceSuborderHelper invoiceSuborderViewHelper : invoiceSuborderViewHelperList) {
            totalActualminutes += invoiceSuborderViewHelper.getTotalActualminutes();
        }
        return timeFormatMinutes(totalActualminutes) + " (" + decimalFormatMinutes(totalActualminutes) + ")";
    }

}
