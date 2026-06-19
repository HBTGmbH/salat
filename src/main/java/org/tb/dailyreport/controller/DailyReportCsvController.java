package org.tb.dailyreport.controller;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.web.UiState;
import org.tb.dailyreport.rest.DailyWorkingReportCsvConverter;
import org.tb.dailyreport.service.DailyWorkingReportService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequestMapping("/dailyreport/csv")
@RequiredArgsConstructor
@Authorized
public class DailyReportCsvController {

    private final DailyWorkingReportCsvConverter csvConverter;
    private final DailyWorkingReportService dailyWorkingReportService;
    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final UiState uiState;

    @GetMapping
    public String show(Model model) {
        YearMonth current = YearMonth.now();
        List<YearMonth> availableMonths = IntStream.range(0, 12)
            .mapToObj(current::minusMonths)
            .toList();
        model.addAttribute("availableMonths", availableMonths);
        model.addAttribute("selectedMonth", current.toString());
        model.addAttribute("section", "dailyreport");
        model.addAttribute("subSection", "csv");
        model.addAttribute("pageTitle", messages.getMessage("main.dailyreport.csv.title.text"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.timereports.text"));
        model.addAttribute("title", messages.getMessage("main.dailyreport.csv.title.text"));
        return "dailyreport/csv";
    }

    @PostMapping("/import")
    @PreAuthorize("isAuthenticated()")
    public String importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "add") String importMode,
            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.dailyreport.csv.import.error.file.required.text"));
            return "redirect:/dailyreport/csv";
        }
        try {
            var reports = csvConverter.read(file.getInputStream());
            if ("replace".equals(importMode)) {
                dailyWorkingReportService.updateReports(reports);
            } else {
                dailyWorkingReportService.createReports(reports);
            }
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.dailyreport.csv.import.success.text"));
        } catch (AuthorizationException | InvalidDataException | BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst()
                    .orElse(messages.getMessage("main.dailyreport.csv.import.error.parse.text")));
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.dailyreport.csv.import.error.parse.text"));
        }
        return "redirect:/dailyreport/csv";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam String month) throws IOException {
        YearMonth yearMonth = YearMonth.parse(month);
        long ecId = effectiveContractId();
        var reports = dailyWorkingReportService.getReportsForMonth(yearMonth, ecId);
        var baos = new ByteArrayOutputStream();
        csvConverter.write(reports, null, new HttpOutputMessage() {
            @Override public OutputStream getBody() { return baos; }
            @Override public HttpHeaders getHeaders() { return new HttpHeaders(); }
        });
        return ResponseEntity.ok()
            .header(CONTENT_DISPOSITION, "attachment; filename=" + month + ".csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(baos.toByteArray());
    }

    private long effectiveContractId() {
        Long fromCookie = uiState.getSelectedContractId();
        if (fromCookie != null && fromCookie > 0) return fromCookie;
        var loginEmployee = employeeService.getLoginEmployee();
        return employeecontractService.getCurrentContract(loginEmployee.getId())
            .map(c -> c.getId())
            .orElse(-1L);
    }
}
