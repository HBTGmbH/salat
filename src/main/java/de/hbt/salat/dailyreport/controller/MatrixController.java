package de.hbt.salat.dailyreport.controller;

import static de.hbt.salat.common.util.DateUtils.formatDateTime;
import static de.hbt.salat.common.util.DateUtils.formatMonth;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.YearMonth;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.service.MatrixService;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;
import de.hbt.salat.employee.service.EmployeeService;

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
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam(required = false) Integer fMonth,
            @RequestParam(required = false) Integer fYear,
            Model model) {

        var today = today();
        int targetMonth = fMonth != null ? fMonth : today.getMonthValue();
        int targetYear = fYear != null ? fYear : today.getYear();
        YearMonth yearMonth = YearMonth.of(targetYear, targetMonth);

        long ecId = effectiveContractId(fEmployeeContractId);

        var matrixData = matrixService.buildMatrix(yearMonth, ecId);
        var selectedContract = Optional.ofNullable(employeecontractService.getEmployeecontractById(ecId));
        boolean showBeginBreakEnd = selectedContract
            .map(c -> !c.getFreelancer())
            .orElse(false);

        String monthKey = "main.timereport.select.month." + formatMonth(yearMonth.atDay(1)).toLowerCase() + ".text";

        boolean monthReleased = selectedContract
            .map(c -> c.getReportReleaseDate() != null && !c.getReportReleaseDate().isBefore(yearMonth.atEndOfMonth()))
            .orElse(false);

        selectedContract.ifPresent(c -> model.addAttribute("selectedEmployeeName",
            EmployeeLabelViewHelper.title(c)));
        model.addAttribute("matrixData", matrixData);
        model.addAttribute("selectedContractId", ecId);
        model.addAttribute("monthReleased", monthReleased);
        model.addAttribute("showBeginBreakEnd", showBeginBreakEnd);
        model.addAttribute("yearMonth", yearMonth);
        model.addAttribute("monthShortForm", formatMonth(yearMonth.atDay(1)));
        // Stand des Ausdrucks im Fuss jeder Seite (#1148), in der Zeitzone der Anwendung
        model.addAttribute("printAsOf", formatDateTime(ClockProvider.now(), "dd.MM.yyyy HH:mm"));
        // month/year navigation is rendered from yearMonth by fragments/month-navigation
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "matrix");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.matrixmenu.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("title", messages.getMessage(monthKey) + " " + targetYear);
        return "dailyreport/matrix";
    }

    @PostMapping("/fill-not-worked")
    @Authorized
    public String fillNotWorked(
            @RequestParam(required = false) Long fEmployeeContractId,
            @RequestParam Integer month,
            @RequestParam Integer year,
            RedirectAttributes redirectAttributes) {
        long ecId = effectiveContractId(fEmployeeContractId);
        try {
            matrixService.fillNotWorked(YearMonth.of(year, month), ecId);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.matrix.fillnotworked.success.text"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst().orElse("Error"));
        }
        return "redirect:/dailyreport/matrix?fMonth=" + month + "&fYear=" + year;
    }

    private long effectiveContractId(Long fEmployeeContractId) {
        if (fEmployeeContractId != null && fEmployeeContractId > 0) {
            return fEmployeeContractId;
        }
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
                .map(Employeecontract::getId)
                .orElse(-1L);
    }
}
