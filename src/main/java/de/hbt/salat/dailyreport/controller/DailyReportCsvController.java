package de.hbt.salat.dailyreport.controller;

import static org.springframework.http.HttpHeaders.CONTENT_DISPOSITION;
import static de.hbt.salat.common.exception.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.util.DateUtils.today;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.rest.DailyWorkingReportCsvConverter;
import de.hbt.salat.dailyreport.service.DailyWorkingReportService;
import de.hbt.salat.dailyreport.service.ImportReport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;
import de.hbt.salat.employee.viewhelper.EmployeeLabelViewHelper;
import de.hbt.salat.employee.service.EmployeeService;

@Slf4j
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

    @GetMapping
    public String show(@RequestParam(required = false) Long fEmployeeContractId, Model model) {
        YearMonth current = YearMonth.from(today());
        List<YearMonth> availableMonths = IntStream.range(0, 12)
            .mapToObj(current::minusMonths)
            .toList();
        long ecId = effectiveContractId(fEmployeeContractId);
        if (ecId > 0) {
            var ec = employeecontractService.getEmployeecontractById(ecId);
            if (ec != null) {
                model.addAttribute("selectedEmployeeName", EmployeeLabelViewHelper.title(ec));
            }
        }
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
    @Authorized
    public String importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "add") String importMode,
            @RequestParam(required = false) Long fEmployeeContractId,
            RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.dailyreport.csv.import.error.file.required.text"));
            return "redirect:/dailyreport/csv";
        }
        long ecId = fEmployeeContractId != null ? fEmployeeContractId : effectiveContractId(fEmployeeContractId);
        try {
            // the file belongs to the employee of the selected contract, not to whoever is logged in (#1142)
            var employee = employeeOfContract(ecId);
            var readResult = csvConverter.read(file.getInputStream(), employee);
            var importReport = "replace".equals(importMode)
                ? dailyWorkingReportService.updateReports(readResult.reports(), employee)
                : dailyWorkingReportService.createReports(readResult.reports(), employee);
            redirectAttributes.addFlashAttribute("importReport",
                new ImportReport(importReport.days(), readResult.linesRead()));
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.dailyreport.csv.import.success.text"));
        } catch (AuthorizationException | InvalidDataException | BusinessRuleException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream()
                    .map(Object::toString).findFirst()
                    .orElse(messages.getMessage("main.dailyreport.csv.import.error.parse.text")));
        } catch (IOException ex) {
            log.error("Could not import CSV.", ex);
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.dailyreport.csv.import.error.parse.text"));
        }
        return "redirect:/dailyreport/csv";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) Long fEmployeeContractId, @RequestParam String month) throws IOException {
        YearMonth yearMonth = YearMonth.parse(month);
        long ecId = effectiveContractId(fEmployeeContractId);
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

    private Employee employeeOfContract(long employeecontractId) {
        var employeecontract = employeecontractService.getEmployeecontractById(employeecontractId);
        if (employeecontract == null) {
            throw new AuthorizationException(EC_EMPLOYEE_CONTRACT_NOT_FOUND);
        }
        return employeecontract.getEmployee();
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
