package org.tb.invoice.controller;

import static java.util.Optional.ofNullable;
import static org.tb.common.util.DateUtils.today;
import static org.tb.invoice.domain.InvoiceSettings.ImageUrl.CLAIM;
import static org.tb.invoice.domain.InvoiceSettings.ImageUrl.LOGO;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DateUtils;
import org.tb.invoice.domain.InvoiceData;
import org.tb.invoice.domain.InvoiceSettings;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.service.ExcelExportService;
import org.tb.invoice.service.InvoiceService;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;
import org.tb.invoice.service.InvoiceSettingsService;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/invoice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BACKOFFICE')")
@SessionAttributes("invoiceData")
public class InvoiceController {

    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final InvoiceSettingsService invoiceSettingsService;
    private final InvoiceService invoiceService;
    private final ExcelExportService excelExportService;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String createForm(Model model, SessionStatus sessionStatus) {
        sessionStatus.setComplete();
        var form = createInitialForm();
        model.addAttribute("invoiceForm", form);
        addCommonModel(model, form);
        return "invoice/invoice-form";
    }

    @PostMapping
    public String updateOptions(@ModelAttribute("invoiceForm") InvoiceForm form,
                                SessionStatus sessionStatus, Model model) {
        sessionStatus.setComplete();
        addCommonModel(model, form);
        return "invoice/invoice-form";
    }

    @PostMapping("/generate")
    public String generate(@ModelAttribute("invoiceForm") InvoiceForm form, Model model) {
        if (form.getTitlesubordertext() == null || form.getTitlesubordertext().isBlank()) {
            initColumnHeaders(form);
        }
        if (form.getOrderId() != null) {
            var invoiceData = buildInvoiceData(form);

            form.setSuborderIdArray(invoiceData.getSuborders().stream()
                .filter(InvoiceSuborder::isVisible)
                .map(InvoiceSuborder::getId)
                .map(Long::valueOf)
                .toList());
            form.setTimereportIdArray(invoiceData.getSuborders().stream()
                .flatMap(so -> so.getTimereports().stream())
                .filter(tr -> tr.isVisible())
                .map(tr -> tr.getId())
                .map(Long::valueOf)
                .toList());

            form.setCustomername(invoiceData.getCustomer().getName());
            form.setCustomeraddress(invoiceData.getCustomer().getAddress());

            model.addAttribute("invoiceData", invoiceData);
        }
        model.addAttribute("invoiceForm", form);
        addCommonModel(model, form);
        return "invoice/invoice-form";
    }

    @PostMapping("/print")
    public String print(@ModelAttribute("invoiceForm") InvoiceForm form,
                        @ModelAttribute("invoiceData") InvoiceData invoiceData,
                        @RequestParam(name = "invoice-settings", required = false, defaultValue = "HBT") String invoiceSettingsName,
                        Model model) {
        updateVisibleFlags(form, invoiceData);

        InvoiceSettings invoiceSettings = invoiceSettingsService.getAllSettings().stream()
            .filter(s -> s.getName().equals(invoiceSettingsName))
            .findFirst()
            .orElseGet(() -> invoiceSettingsService.getAllSettings().get(0));

        model.addAttribute("invoiceForm", form);
        model.addAttribute("logoUrl", invoiceSettings.getImageUrl(LOGO));
        model.addAttribute("claimUrl", invoiceSettings.getImageUrl(CLAIM));
        model.addAttribute("customCss", invoiceSettings.getCustomCss());
        model.addAttribute("today", today());
        model.addAttribute("dynamicColumnCount", computeDynamicColumnCount(form));
        model.addAttribute("targethoursbox", form.isTargethoursbox());
        return "invoice/invoice-print";
    }

    @PostMapping("/export")
    public void export(@ModelAttribute("invoiceForm") InvoiceForm form,
                       @ModelAttribute("invoiceData") InvoiceData invoiceData,
                       HttpServletResponse response) throws Exception {
        updateVisibleFlags(form, invoiceData);
        var displayOptions = InvoiceOptions.builder()
            .showTimereports(form.isTimereportsbox())
            .showEmployee(form.isEmployeesignbox())
            .showTaskdescriptions(form.isTimereportdescriptionbox())
            .showBudget(form.isTargethoursbox())
            .build();
        var bytes = excelExportService.exportToExcel(invoiceData, displayOptions, form);
        var fileName = createFileName(invoiceData);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }

    private InvoiceForm createInitialForm() {
        var form = new InvoiceForm();
        var today = today();
        var currentYearMonth = YearMonth.from(today);
        form.setFromYearMonth(currentYearMonth.toString()); // "yyyy-MM"
        form.setFromDate(today.withDayOfMonth(1));
        form.setUntilDate(today.withDayOfMonth(today.lengthOfMonth()));
        form.setInvoiceview(GlobalConstants.VIEW_MONTHLY);
        form.setTimereportsbox(true);
        form.setTimereportdescriptionbox(true);
        form.setEmployeesignbox(true);
        form.setShowOnlyValid(true);
        initColumnHeaders(form);
        return form;
    }

    private void initColumnHeaders(InvoiceForm form) {
        form.setTitleactualhourstext(messages.getMessage("main.invoice.title.actualhours.text", "Hours"));
        form.setTitleactualdurationtext(messages.getMessage("main.invoice.title.actualduration.text", "Duration"));
        form.setTitledatetext(messages.getMessage("main.invoice.title.date.text", "Date"));
        form.setTitledescriptiontext(messages.getMessage("main.invoice.title.description.text", "Description"));
        form.setTitleemployeesigntext(messages.getMessage("main.invoice.title.employeesign.text", "Employee"));
        form.setTitlesubordertext(messages.getMessage("main.invoice.title.suborder.text", "Suborder"));
        form.setTitletargethourstext(messages.getMessage("main.invoice.title.targethours.text", "Budget"));
        form.setTitleinvoiceattachment(messages.getMessage("main.invoice.addresshead.text", "Invoice"));
    }

    private void addCommonModel(Model model, InvoiceForm form) {
        model.addAttribute("orders", customerorderService.getInvoiceableCustomerorders());
        model.addAttribute("suborders", ofNullable(form.getOrderId())
            .map(orderId -> suborderService.getSubordersByCustomerorderId(orderId, form.isShowOnlyValid()).stream()
                .sorted(SubOrderComparator.INSTANCE)
                .toList())
            .orElse(List.of()));
        model.addAttribute("invoiceSettings", invoiceSettingsService.getAllSettings());
        model.addAttribute("dynamicColumnCount", computeDynamicColumnCount(form));
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "invoice");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.invoice.title.text", "Invoice"));
    }

    private static int computeDynamicColumnCount(InvoiceForm form) {
        int count = 0;
        if (form.isTimereportsbox()) count++;
        if (form.isEmployeesignbox()) count++;
        if (form.isTimereportdescriptionbox()) count++;
        return count;
    }

    private InvoiceData buildInvoiceData(InvoiceForm form) {
        LocalDate dateFirst;
        LocalDate dateLast;

        switch (form.getInvoiceview()) {
            case GlobalConstants.VIEW_MONTHLY -> {
                var yearMonth = YearMonth.parse(form.getFromYearMonth());
                dateFirst = yearMonth.atDay(1);
                dateLast = yearMonth.atEndOfMonth();
            }
            case GlobalConstants.VIEW_CUSTOM -> {
                dateFirst = form.getFromDate();
                dateLast = form.getUntilDate();
            }
            default -> {
                dateFirst = today();
                dateLast = today();
            }
        }

        var options = InvoiceOptions.builder()
            .showNonInvoicableSuborders(form.isInvoicebox())
            .showFixedPriceSuborders(form.isFixedpricebox())
            .showBudget(form.isTargethoursbox())
            .useCustomerDescriptions(form.isCustomeridbox())
            .showTimereports(form.isTimereportsbox())
            .showEmployee(form.isEmployeesignbox())
            .showTaskdescriptions(form.isTimereportdescriptionbox())
            .shortDescriptions("shortdescription".equals(form.getSuborderdescription()))
            .build();

        return invoiceService.generateInvoiceData(
            form.getOrderId(),
            ofNullable(form.getSuborderId()),
            new LocalDateRange(dateFirst, dateLast),
            options);
    }

    private static void updateVisibleFlags(InvoiceForm form, InvoiceData invoiceData) {
        var suborderIds = form.getSuborderIdArray();
        var timereportIds = form.getTimereportIdArray();

        for (var invoiceSuborder : invoiceData.getSuborders()) {
            invoiceSuborder.setVisible(suborderIds.contains(invoiceSuborder.getId()));
            for (var invoiceTimereport : invoiceSuborder.getTimereports()) {
                invoiceTimereport.setVisible(timereportIds.contains(invoiceTimereport.getId()));
            }
        }
    }

    private static String createFileName(InvoiceData invoiceData) {
        var fileName = "rechnung-" + invoiceData.getCustomerOrderSign() +
                       "-" + DateUtils.format(invoiceData.getBillingPeriod().getFrom(), "dd.MM.yy") +
                       "-" + DateUtils.format(invoiceData.getBillingPeriod().getUntil(), "dd.MM.yy") +
                       ".xlsx";
        return fileName.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }
}
