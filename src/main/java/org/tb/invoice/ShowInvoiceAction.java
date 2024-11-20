package org.tb.invoice;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static org.tb.common.DateTimeViewHelper.getDaysToDisplay;
import static org.tb.common.DateTimeViewHelper.getWeeksToDisplay;
import static org.tb.common.DateTimeViewHelper.getYearsToDisplay;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.TimeFormatUtils.decimalFormatMinutes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.action.DailyReportAction;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.viewhelper.EmployeeViewHelper;
import org.tb.invoice.domain.InvoiceSettings;
import org.tb.invoice.domain.InvoiceSettings.ImageUrl;
import org.tb.invoice.service.InvoiceSettingsService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Component
@RequiredArgsConstructor
public class ShowInvoiceAction extends DailyReportAction<ShowInvoiceForm> {

    private final CustomerorderService customerorderService;
    private final TimereportService timereportService;
    private final EmployeecontractService employeecontractService;
    private final SuborderService suborderService;
    private final EmployeeService employeeService;
    private final InvoiceSettingsService invoiceSettingsService;

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
            LocalDate dateFirst;
            LocalDate dateLast;
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

                    customerOrder = customerorderService.getCustomerorderBySign(showInvoiceForm.getOrder());
                    if (showInvoiceForm.getSuborder().equals("ALL SUBORDERS")) {
                        suborderList = suborderService.getSubordersByCustomerorderId(customerOrder.getId(), false);
                    } else {
                        suborderList = suborderService.getSuborderById(Long.parseLong(showInvoiceForm.getSuborder())).getAllChildren();
                    }
                    suborderList.sort(SubOrderComparator.INSTANCE);
                    // remove suborders that are not valid sometime between dateFirst and dateLast
                    suborderList.removeIf(so -> so.getFromDate().isAfter(dateLast) || so.getUntilDate() != null && so.getUntilDate().isBefore(dateFirst));
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
                    customerOrder = customerorderService.getCustomerorderBySign(showInvoiceForm.getOrder());
                    if (showInvoiceForm.getSuborder().equals("ALL SUBORDERS")) {
                        suborderList = suborderService.getSubordersByCustomerorderId(customerOrder.getId(), false);
                    } else {
                        suborderList = suborderService.getSuborderById(Long.parseLong(showInvoiceForm.getSuborder())).getAllChildren();
                    }
                    // remove suborders that are not valid sometime between dateFirst and dateLast
                    suborderList.removeIf(so -> so.getFromDate().isAfter(dateLast) || so.getUntilDate() != null && so.getUntilDate().isBefore(dateFirst));
                    suborderList.sort(SubOrderComparator.INSTANCE);
                } else {
                    throw new RuntimeException("no view type selected");
                }

                List<Suborder> suborderListTemp = new LinkedList<>();
                // include suborders according to selection (nicht fakturierbar oder Festpreis mit einbeziehen oder nicht) for calculating targethoursum
                if (showInvoiceForm.isInvoicebox() && showInvoiceForm.isFixedpricebox()) {
                    suborderListTemp.addAll(suborderList);
                } else if (showInvoiceForm.isFixedpricebox()) {
                    for (Suborder suborder : suborderList) {
                        if (suborder.getInvoice() == YESNO_YES || suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }
                } else if (showInvoiceForm.isInvoicebox()) {
                    for (Suborder suborder : suborderList) {
                        if (!suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }
                } else {
                    for (Suborder suborder : suborderList) {
                        if (suborder.getInvoice() == YESNO_YES && !suborder.getFixedPrice()) {
                            suborderListTemp.add(suborder);
                        }
                    }

                }
                var totaldurationsum = fillViewHelper(
                    suborderListTemp,
                    invoiceSuborderViewHelperList,
                    dateFirst,
                    dateLast,
                    showInvoiceForm
                );
                request.getSession().setAttribute("totaldurationsum", DurationUtils.format(totaldurationsum));
                request.getSession().setAttribute("totalhourssum", decimalFormatMinutes(totaldurationsum.toMinutes()));

                request.getSession().setAttribute("viewhelpers", invoiceSuborderViewHelperList);
                request.getSession().setAttribute("customername", customerOrder.getCustomer().getName());
                request.getSession().setAttribute("customeraddress", customerOrder.getCustomer().getAddress());
                YearMonth yearMonth = DateUtils.getYearMonth(dateFirst);
                request.getSession().setAttribute("dateMonth", monthMap.get(String.valueOf(yearMonth.getMonthValue() - 1)));
                request.getSession().setAttribute("dateYear", yearMonth.getYear());
                request.getSession().setAttribute("dateFirst", format(dateFirst, "dd.MM.yyyy"));
                request.getSession().setAttribute("dateLast", format(dateLast, "dd.MM.yyyy"));
                request.getSession().setAttribute("currentOrderObject", customerOrder);
                request.getSession().setAttribute("customeridbox", showInvoiceForm.isCustomeridbox());
                request.getSession().setAttribute("targethoursbox", showInvoiceForm.isTargethoursbox());
                request.getSession().setAttribute("actualhoursbox", showInvoiceForm.isActualhoursbox());
                request.getSession().setAttribute("employeesignbox", showInvoiceForm.isEmployeesignbox());
                request.getSession().setAttribute("timereportdescriptionbox", showInvoiceForm.isTimereportdescriptionbox());
                request.getSession().setAttribute("timereportsbox", showInvoiceForm.isTimereportsbox());
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
                List<Suborder> suborders = suborderService.getSubordersByCustomerorderId(
                    customerorderService.getCustomerorderBySign(showInvoiceForm.getOrder()).getId(), showInvoiceForm.getShowOnlyValid());
                suborders.sort(SubOrderComparator.INSTANCE);

                request.getSession().setAttribute("suborders", suborders);
            }

            /*
             * Delete resultset if the customerorder of the invoice form has
             * changed if(request.getSession().getAttribute("viewhelpers") !=
             * null){ List<InvoiceSuborderViewHelper>
             * invoiceSuborderViewHelperList = (List<InvoiceSuborderViewHelper>)
             * request.getSession().getAttribute("viewhelpers");
             * invoiceSuborderViewHelperList.getFirst().getParentorder().equals(customerorderDAO.getCustomerorderBySign(invoiceForm.getOrder())); }
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
            request.getSession().setAttribute("weeks", getWeeksToDisplay(showInvoiceForm.getFromYear()));
            request.getSession().setAttribute("lastDay", showInvoiceForm.getUntilDay());
            request.getSession().setAttribute("lastMonth", showInvoiceForm.getUntilMonth());
            request.getSession().setAttribute("lastYear", showInvoiceForm.getUntilYear());
            request.getSession().setAttribute("optionmwst", showInvoiceForm.getMwst());
            request.getSession().setAttribute("optionsuborderdescription", showInvoiceForm.getSuborderdescription());
            request.getSession().setAttribute("layerlimit", showInvoiceForm.getLayerlimit());
            request.getSession().setAttribute("customername", showInvoiceForm.getCustomername());
            request.getSession().setAttribute("order", showInvoiceForm.getOrder());
            String customeraddress = showInvoiceForm.getCustomeraddress();
            request.getSession().setAttribute("customeraddress", customeraddress);
            request.getSession().setAttribute("today", today());

            // calc dynamic column count - required by the jsp
            int dynamicColumnCount = 0;
            if(showInvoiceForm.isActualhoursbox()) dynamicColumnCount += 2;
            if(showInvoiceForm.isTimereportsbox()) dynamicColumnCount++;
            if(showInvoiceForm.isEmployeesignbox()) dynamicColumnCount++;
            request.getSession().setAttribute("dynamicColumnCount", dynamicColumnCount);

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
            var suborderIds = Arrays.asList(showInvoiceForm.getSuborderIdArray());
            var timereportIds = Arrays.asList(showInvoiceForm.getTimereportIdArray());
            for (InvoiceSuborderHelper invoiceSuborderViewHelper : suborderViewhelperList) {
                if(suborderIds.contains(invoiceSuborderViewHelper.getId().toString())) {
                    invoiceSuborderViewHelper.setVisible(true);
                }
                for (InvoiceTimereportHelper invoiceTimereportViewHelper : invoiceSuborderViewHelper.getInvoiceTimereportViewHelperList()) {
                    if(timereportIds.contains(invoiceTimereportViewHelper.getId().toString())) {
                        invoiceTimereportViewHelper.setVisible(true);
                    }
                }
            }
            Duration actualMinutesSum = Duration.ZERO;
            int layerlimit = Integer.parseInt(showInvoiceForm.getLayerlimit());
            for (InvoiceSuborderHelper invoiceSuborderViewHelper : suborderViewhelperList) {
                if (invoiceSuborderViewHelper.getLayer() <= layerlimit
                        || showInvoiceForm.getLayerlimit().equals("-1")) {
                    if (invoiceSuborderViewHelper.isVisible()) {
                        if (invoiceSuborderViewHelper.getLayer() < layerlimit
                                || showInvoiceForm.getLayerlimit().equals("-1")) {
                            actualMinutesSum = actualMinutesSum.plusMinutes(invoiceSuborderViewHelper.getTotalActualminutesPrint());
                        } else {
                            actualMinutesSum = actualMinutesSum.plusMinutes(invoiceSuborderViewHelper.getDurationInMinutes());
                        }
                    }
                }
            }
            request.getSession().setAttribute("actualminutessum", actualMinutesSum);
            request.getSession().setAttribute("actualhourssum", (double)actualMinutesSum.toMinutes() / MINUTES_PER_HOUR);
            request.getSession().setAttribute("printactualhourssum", DurationUtils.decimalFormat(actualMinutesSum));
            request.getSession().setAttribute("titleactualhourstext", showInvoiceForm.getTitleactualhourstext());
            request.getSession().setAttribute("titleactualdurationtext", showInvoiceForm.getTitleactualdurationtext());
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

                String invoiceSettingsName = ofNullable(request.getParameter("invoice-settings")).orElse("HBT");

                InvoiceSettings invoiceSettings = invoiceSettingsService.getAllSettings()
                    .stream()
                    .filter(is -> is.getName().equals(invoiceSettingsName))
                    .findFirst()
                    .orElseThrow();

                request.setAttribute("invoiceSettings", invoiceSettings);
                request.setAttribute("logoUrl", invoiceSettings.getImageUrl(ImageUrl.LOGO));
                request.setAttribute("claimUrl", invoiceSettings.getImageUrl(ImageUrl.CLAIM));
                request.setAttribute("customCss", invoiceSettings.getCustomCss());

                return mapping.findForward("print");
            } else if (task.equals("export")) {
                MessageResources messageResources = getResources(request);
                request.getSession().setAttribute("overall", messageResources.getMessage(getLocale(request), "main.invoice.overall.text"));
                ExcelArchivierer.exportInvoice(showInvoiceForm, request, response, ExcelArchivierer.getHSSFFactory());
                request.getSession().removeAttribute("overall");
                return null;
            } else if (task.equals("exportNew")) {
                MessageResources messageResources = getResources(request);
                request.getSession().setAttribute("overall", messageResources.getMessage(getLocale(request),"main.invoice.overall.text"));
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
            EmployeeViewHelper eh = new EmployeeViewHelper();
            Employeecontract ec = eh.getAndInitCurrentEmployee(request, employeeService, employeecontractService);
            request.getSession().setAttribute("days", getDaysToDisplay());
            request.getSession().setAttribute("years", getYearsToDisplay());
            request.getSession().setAttribute("weeks", getWeeksToDisplay(showInvoiceForm.getFromYear()));
            request.getSession().setAttribute("orders", customerorderService.getInvoiceableCustomerorders());
            request.getSession().setAttribute("suborders", new LinkedList<Suborder>());
            request.getSession().setAttribute("optionmwst", "19");
            request.getSession().setAttribute("layerlimit", "-1");
            // selected view and selected dates
            if (showInvoiceForm.getFromDay() == null || showInvoiceForm.getFromMonth() == null || showInvoiceForm.getFromYear() == null) {
                // set standard dates and view
                LocalDate today = today();
                showInvoiceForm.setFromDay("01");
                showInvoiceForm.setFromMonth(DateUtils.getMonthShortString(today));
                showInvoiceForm.setFromYear(DateUtils.getYearString(today));
                showInvoiceForm.setUntilDay(DateUtils.getLastDayOfMonth(today));
                showInvoiceForm.setUntilMonth(DateUtils.getMonthShortString(today));
                showInvoiceForm.setUntilYear(DateUtils.getYearString(today));
                request.getSession().setAttribute("invoiceview", GlobalConstants.VIEW_MONTHLY);
                showInvoiceForm.setInvoiceview(GlobalConstants.VIEW_MONTHLY);
            }
            MessageResources messageResources = getResources(request);
            showInvoiceForm.setTitleactualhourstext(messageResources.getMessage(getLocale(request),"main.invoice.title.actualhours.text"));
            showInvoiceForm.setTitleactualdurationtext(messageResources.getMessage(getLocale(request),"main.invoice.title.actualduration.text"));
            showInvoiceForm.setTitlecustomersigntext(messageResources.getMessage(getLocale(request),"main.invoice.title.customersign.text"));
            showInvoiceForm.setTitledatetext(messageResources.getMessage(getLocale(request),"main.invoice.title.date.text"));
            showInvoiceForm.setTitledescriptiontext(messageResources.getMessage(getLocale(request),"main.invoice.title.description.text"));
            showInvoiceForm.setTitleemployeesigntext(messageResources.getMessage(getLocale(request),"main.invoice.title.employeesign.text"));
            showInvoiceForm.setTitlesubordertext(messageResources.getMessage(getLocale(request),"main.invoice.title.suborder.text"));
            showInvoiceForm.setTitletargethourstext(messageResources.getMessage(getLocale(request),"main.invoice.title.targethours.text"));
            showInvoiceForm.setTitleinvoiceattachment(messageResources.getMessage(getLocale(request),"main.invoice.addresshead.text"));
            request.getSession().setAttribute("currentDay", showInvoiceForm.getFromDay());
            request.getSession().setAttribute("currentMonth", showInvoiceForm.getFromMonth());
            request.getSession().setAttribute("currentYear", showInvoiceForm.getFromYear());
            request.getSession().setAttribute("currentWeek", showInvoiceForm.getFromWeek());
            request.getSession().setAttribute("lastDay", showInvoiceForm.getUntilDay());
            request.getSession().setAttribute("lastMonth", showInvoiceForm.getUntilMonth());
            request.getSession().setAttribute("lastYear", showInvoiceForm.getUntilYear());
            request.getSession().removeAttribute("viewhelpers");
            showInvoiceForm.init();
        }
        return mapping.findForward("success");
    }

    private Duration fillViewHelper(List<Suborder> suborderList, List<InvoiceSuborderHelper> invoiceSuborderViewHelperList, LocalDate dateFirst, LocalDate dateLast,
                                  ShowInvoiceForm invoiceForm) {
        List<String> suborderIdList = new ArrayList<>(suborderList.size());
        List<String> timereportIdList = new ArrayList<>();
        for (Suborder suborder : suborderList) {
            List<InvoiceTimereportHelper> invoiceTimereportViewHelperList = new LinkedList<>();
            List<TimereportDTO> timereportList =
                timereportService.getTimereportsByDatesAndSuborderId(dateFirst, dateLast, suborder.getId())
                    .stream()
                    .sorted(comparing(TimereportDTO::getReferenceday).thenComparing(TimereportDTO::getEmployeeSign))
                    .toList();
            for (TimereportDTO timereport : timereportList) {
                InvoiceTimereportHelper invoiceTimereportViewHelper = new InvoiceTimereportHelper(timereport);
                invoiceTimereportViewHelperList.add(invoiceTimereportViewHelper);
                timereportIdList.add(String.valueOf(invoiceTimereportViewHelper.getId()));
            }
            InvoiceSuborderHelper newInvoiceSuborderViewHelper = new InvoiceSuborderHelper(suborder, timereportService, dateFirst, dateLast, invoiceForm.isInvoicebox());
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

        return Duration.ofMinutes(totalActualminutes);
    }

}
