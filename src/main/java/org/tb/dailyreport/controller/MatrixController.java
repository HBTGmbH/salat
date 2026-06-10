package org.tb.dailyreport.controller;

import static org.tb.common.util.DateUtils.formatMonth;
import static org.tb.common.util.DateUtils.today;

import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.dailyreport.service.MatrixService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequestMapping("/dailyreport/matrix")
@RequiredArgsConstructor
@Authorized
public class MatrixController {

    private final MatrixService matrixService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String show(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Model model) {

        var today = today();
        int targetMonth = month != null ? month : today.getMonthValue();
        int targetYear = year != null ? year : today.getYear();
        YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);

        var loginEmployee = employeeService.getLoginEmployee();
        var myContract = employeecontractService.getCurrentContract(loginEmployee.getId()).orElse(null);
        long ecId = employeeContractId != null ? employeeContractId
            : (myContract != null ? myContract.getId() : -1L);

        var matrixData = matrixService.buildMatrix(yearMonth, ecId);
        var contracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today);
        boolean showBeginBreakEnd = contracts.stream()
            .filter(c -> c.getId() == ecId)
            .findFirst()
            .map(c -> !c.getFreelancer())
            .orElse(false);

        YearMonth prev = yearMonth.minusMonths(1);
        YearMonth next = yearMonth.plusMonths(1);

        String monthKey = "main.timereport.select.month." + formatMonth(yearMonth.atDay(1)).toLowerCase() + ".text";

        model.addAttribute("matrixData", matrixData);
        model.addAttribute("employeecontracts", contracts);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("showBeginBreakEnd", showBeginBreakEnd);
        model.addAttribute("yearMonth", yearMonth);
        model.addAttribute("monthShortForm", formatMonth(yearMonth.atDay(1)));
        model.addAttribute("prevMonth", prev.getMonthValue());
        model.addAttribute("prevYear", prev.getYear());
        model.addAttribute("nextMonth", next.getMonthValue());
        model.addAttribute("nextYear", next.getYear());
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "matrix");
        model.addAttribute("containerClass", "container-fluid");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.matrixmenu.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("title", messages.getMessage(monthKey) + " " + targetYear);
        return "dailyreport/matrix";
    }

    @PostMapping("/fill-not-worked")
    @PreAuthorize("isAuthenticated()")
    public String fillNotWorked(
            @RequestParam Long employeeContractId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {
        try {
            matrixService.fillNotWorked(YearMonth.of(year, month), employeeContractId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.matrix.fillnotworked.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/matrix?month=" + month + "&year=" + year + "&employeeContractId=" + employeeContractId;
    }
}
