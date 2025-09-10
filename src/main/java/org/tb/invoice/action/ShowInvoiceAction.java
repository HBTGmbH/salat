package org.tb.invoice.action;

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static org.tb.common.util.DateTimeUtils.getDaysToDisplay;
import static org.tb.common.util.DateTimeUtils.getWeeksToDisplay;
import static org.tb.common.util.DateTimeUtils.getYearsToDisplay;
import static org.tb.common.util.DateUtils.getDateFormStrings;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DateUtils;
import org.tb.invoice.domain.InvoiceData;
import org.tb.invoice.domain.InvoiceSettings;
import org.tb.invoice.domain.InvoiceSettings.ImageUrl;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.service.ExcelExportService;
import org.tb.invoice.service.InvoiceService;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;
import org.tb.invoice.service.InvoiceSettingsService;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Component
@RequiredArgsConstructor
public class ShowInvoiceAction extends LoginRequiredAction<ShowInvoiceForm> {

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final InvoiceSettingsService invoiceSettingsService;
  private final InvoiceService invoiceService;
  private final ExcelExportService excelExportService;

  @Override
  public ActionForward executeAuthenticated(ActionMapping mapping, ShowInvoiceForm form, HttpServletRequest request, HttpServletResponse response) throws Exception {

    // check if special tasks initiated from the daily display need to be
    // carried out...

    String task = request.getParameter("task");
    if ("updateOptions".equals(task)) {
      List<Suborder> suborders = ofNullable(form.getOrderIdTyped())
          .map(orderId -> suborderService.getSubordersByCustomerorderId(orderId, form.isShowOnlyValid()).stream()
              .sorted(SubOrderComparator.INSTANCE)
              .toList()
          )
          .orElse(List.of());
      request.getSession().setAttribute("suborders", suborders);

      // reset invoice data
      request.getSession().removeAttribute("invoiceData");

      int dynamicColumnCount = 0;
      if(form.isTimereportsbox()) dynamicColumnCount++;
      if(form.isEmployeesignbox()) dynamicColumnCount++;
      if(form.isTimereportdescriptionbox()) dynamicColumnCount++;
      request.getSession().setAttribute("dynamicColumnCount", dynamicColumnCount);

    } else if ("generateMaximumView".equals(task)) {
      if (isOrderSelected(form)) {

        String selectedView = form.getInvoiceview();
        LocalDate dateFirst;
        LocalDate dateLast;

        switch(selectedView) {
          case GlobalConstants.VIEW_MONTHLY:
            // generate dates for monthly view mode
            dateFirst = getDateFormStrings("1", form.getFromMonth(), form.getFromYear(), false);
            dateLast = DateUtils.getEndOfMonth(dateFirst);
            break;
          case GlobalConstants.VIEW_CUSTOM:
            // generate dates for a period of time in custom view mode
            dateFirst = getDateFormStrings(form.getFromDay(), form.getFromMonth(), form.getFromYear(), false);
            dateLast = getDateFormStrings(form.getUntilDay(), form.getUntilMonth(), form.getUntilYear(), false);
            break;
          default:
            return mapping.getInputForward();
        }

        var customerorderId = form.getOrderIdTyped();
        var suborderId = ofNullable(form.getSuborderIdTyped());
        var validity = new LocalDateRange(dateFirst, dateLast);
        var options = getInvoiceOptions(form);

        var invoiceData = invoiceService.generateInvoiceData(customerorderId, suborderId, validity, options);

        var suborderIdArray = invoiceData.getSuborders().stream()
            .filter(InvoiceSuborder::isVisible)
            .mapToLong(InvoiceSuborder::getId)
            .toArray();
        var timereportIdArray = invoiceData.getSuborders().stream()
            .flatMap(so -> so.getTimereports().stream())
            .filter(tr -> tr.isVisible())
            .mapToLong(tr -> tr.getId())
            .toArray();

        form.setSuborderIdArrayTyped(suborderIdArray);
        form.setTimereportIdArrayTyped(timereportIdArray);

        form.setCustomername(invoiceData.getCustomer().getName());
        form.setCustomeraddress(invoiceData.getCustomer().getAddress());

        request.getSession().setAttribute("invoiceData", invoiceData);
      }
    } else if ("print".equals(task)) {
      updateVisibleFlags(form, request);

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
      request.setAttribute("today", today());

      return mapping.findForward("print");
    } else if ("export".equals(task)) {
      updateVisibleFlags(form, request);

      var invoiceData = (InvoiceData) request.getSession().getAttribute("invoiceData");
      try (ServletOutputStream out = response.getOutputStream()) {
        var bytes = excelExportService.exportToExcel(invoiceData, form);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-disposition", "attachment; filename=" + createFileName(invoiceData));
        response.setContentLength(bytes.length);
        out.write(bytes);
      }
      return RESPONSE_COMPLETED;
    } else if ("back".equals(task)) {
      // END
      // call on InvoiceView with any parameter to forward or go back
      // just go back to main menu
      return mapping.findForward("backtomenu");
    } else {
      // initial request to display invoice form - just set initial values
      request.getSession().setAttribute("days", getDaysToDisplay());
      request.getSession().setAttribute("years", getYearsToDisplay());
      request.getSession().setAttribute("weeks", getWeeksToDisplay(form.getFromYear()));
      request.getSession().setAttribute("orders", customerorderService.getInvoiceableCustomerorders());
      request.getSession().setAttribute("suborders", List.of());
      request.getSession().removeAttribute("invoiceData");
      form.init(getResources(request), getLocale(request));
    }
    return mapping.findForward("success");
  }

  private static void updateVisibleFlags(ShowInvoiceForm form, HttpServletRequest request) {
    var invoiceData = (InvoiceData) request.getSession().getAttribute("invoiceData");

    var suborderIds = stream(form.getSuborderIdArrayTyped()).boxed().toList();
    var timereportIds = stream(form.getTimereportIdArrayTyped()).boxed().toList();

    for (var invoiceSuborder : invoiceData.getSuborders()) {
      invoiceSuborder.setVisible(suborderIds.contains(invoiceSuborder.getId()));
      for(var invoiceTimereport : invoiceSuborder.getTimereports()) {
        invoiceTimereport.setVisible(timereportIds.contains(invoiceTimereport.getId()));
      }
    }
  }

  private static InvoiceOptions getInvoiceOptions(ShowInvoiceForm form) {
    return InvoiceOptions.builder()
        .showNonInvoicableSuborders(form.isInvoicebox())
        .showFixedPriceSuborders(form.isFixedpricebox())
        .showBudget(form.isTargethoursbox())
        .useCustomerDescriptions(form.isCustomeridbox())
        .showTimereports(form.isTimereportsbox())
        .showEmployee(form.isEmployeesignbox())
        .showTaskdescriptions(form.isTimereportdescriptionbox())
        .build();
  }

  private static boolean isOrderSelected(ShowInvoiceForm showInvoiceForm) {
    return showInvoiceForm.getOrderId() != null;
  }

  private static String createFileName(InvoiceData invoiceData) {
    var fileName = "rechnung-" + invoiceData.getCustomerOrderSign() +
                   "-" + DateUtils.format(invoiceData.getBillingPeriod().getFrom(), "dd.MM.yy") +
                   "-" + DateUtils.format(invoiceData.getBillingPeriod().getUntil(), "dd.MM.yy") +
                   "-erzeugt-" + DateUtils.formatDateTime(DateUtils.now(), "dd-MM-yy-HHmm") +
                   ".xlsx";
    var sanitizedFileName = fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    return sanitizedFileName;
  }

}
