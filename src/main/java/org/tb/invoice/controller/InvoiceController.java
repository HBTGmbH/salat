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
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DateUtils;
import org.tb.invoice.domain.InvoiceData;
import org.tb.invoice.domain.InvoiceSettings;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.service.ExcelExportService;
import org.tb.invoice.service.InvoiceService;
import org.tb.invoice.service.InvoiceService.InvoiceOptions;
import org.tb.budget.service.BudgetQueryService;
import org.tb.invoice.service.InvoiceSettingsService;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/invoice")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BACKOFFICE')")
public class InvoiceController {

    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final InvoiceSettingsService invoiceSettingsService;
    private final InvoiceService invoiceService;
    private final BudgetQueryService budgetQueryService;
    private final ExcelExportService excelExportService;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String createForm(Model model) {
        var form = createInitialForm();
        model.addAttribute("invoiceForm", form);
        addCommonModel(model, form);
        return "invoice/invoice-form";
    }

    @PostMapping
    public String updateOptions(@ModelAttribute("invoiceForm") InvoiceForm form, Model model) {
        prefillPeriodFromBudget(form);
        form.setPreviousOrderBudgetId(form.getOrderBudgetId());
        rejectAmbiguousScope(form, model);
        addCommonModel(model, form);
        return "invoice/invoice-form";
    }

    @PostMapping("/generate")
    public String generate(@ModelAttribute("invoiceForm") InvoiceForm form, Model model) {
        if (form.getTitlesubordertext() == null || form.getTitlesubordertext().isBlank()) {
            initColumnHeaders(form);
        }
        prefillPeriodFromBudget(form);
        form.setPreviousOrderBudgetId(form.getOrderBudgetId());
        if (rejectAmbiguousScope(form, model)) {
            model.addAttribute("invoiceForm", form);
            addCommonModel(model, form);
            return "invoice/invoice-form";
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
                        @RequestParam(name = "invoice-settings", required = false, defaultValue = "HBT") String invoiceSettingsName,
                        Model model) {
        if (rejectAmbiguousScope(form, model)) {
            model.addAttribute("invoiceForm", form);
            addCommonModel(model, form);
            return "invoice/invoice-form";
        }
        var invoiceData = buildInvoiceData(form);
        updateVisibleFlags(form, invoiceData);
        model.addAttribute("invoiceData", invoiceData);

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
                       HttpServletResponse response) throws Exception {
        if (form.isScopeAmbiguous()) {
            // Not reachable through the form, which clears one when the other is picked.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        var invoiceData = buildInvoiceData(form);
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
        // The plans of the selected order, refreshed by the same full-form submit that refreshes the
        // suborders. Empty when the user may see no budget data of this order — then only the
        // suborder narrowing is available (#915).
        model.addAttribute("orderBudgets", ofNullable(form.getOrderId())
            .map(orderId -> customerorderService.getCustomerorderById(orderId))
            .map(order -> budgetQueryService.getActivePlans(order.getSign()))
            .orElse(List.of()));
        model.addAttribute("invoiceSettings", invoiceSettingsService.getAllSettings());
        model.addAttribute("dynamicColumnCount", computeDynamicColumnCount(form));
        model.addAttribute("section", "backoffice");
        model.addAttribute("subSection", "invoice");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.backoffice.text", "Backoffice"));
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
            ofNullable(form.getOrderBudgetId()),
            new LocalDateRange(dateFirst, dateLast),
            options);
    }

    /**
     * Prefills the billing period from the chosen plan, but only when the choice actually changed —
     * otherwise a re-render would keep overwriting dates the user adjusted afterwards. The period
     * stays editable; the plan only proposes it.
     */
    private void prefillPeriodFromBudget(InvoiceForm form) {
        var chosen = form.getOrderBudgetId();
        if (chosen == null || chosen.equals(form.getPreviousOrderBudgetId())) {
            return;
        }
        budgetQueryService.getPlan(chosen).ifPresent(plan -> {
            form.setInvoiceview(GlobalConstants.VIEW_CUSTOM);
            form.setFromDate(plan.validFrom());
            form.setUntilDate(plan.validUntil());
        });
    }

    /** Both narrowings at once has no defined meaning — refused rather than silently resolved. */
    private boolean rejectAmbiguousScope(InvoiceForm form, Model model) {
        if (!form.isScopeAmbiguous()) {
            return false;
        }
        model.addAttribute("invoiceError",
            messages.getMessage("main.invoice.error.scope.ambiguous"));
        return true;
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
