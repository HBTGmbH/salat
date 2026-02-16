package org.tb.order.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.order.service.OrderRevenueImportResult;
import org.tb.order.service.OrderRevenueService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequestMapping("/orders/revenue-upload")
@RequiredArgsConstructor
public class OrderRevenueUploadController {

    private final OrderRevenueService orderRevenueService;
    private final AuthorizedEmployee authorizedEmployee;
    private final MessageSourceAccessor messages;
    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('BACKOFFICE','MANAGER')")
    public String uploadForm(Model model) {
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "revenue-upload");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
        model.addAttribute("title", messages.getMessage("orderrevenue.upload.title", "Umsatz-/Kosten-Upload"));
        model.addAttribute("comment", "");
        model.addAttribute("mapping", new HashMap<String, String>());
        return "order/revenue-upload";
    }

    @PostMapping(path = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('BACKOFFICE','MANAGER')")
    public String process(
            @ModelAttribute UploadForm uploadForm,
            Model model,
            BindingResult bindingResult
    ) throws IOException {
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "revenue-upload");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));

        var file = uploadForm.getFile();
        var comment = uploadForm.getComment();
        var mapping = uploadForm.getMapping();

        if (file == null || file.isEmpty()) {
            bindingResult.reject("file", messages.getMessage("orderrevenue.upload.error.file.required", "Bitte eine .xlsx-Datei auswählen"));
            model.addAttribute("title", messages.getMessage("orderrevenue.upload.title", "Umsatz-/Kosten-Upload"));
            return "order/revenue-upload";
        }

        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            bindingResult.reject("file", messages.getMessage("orderrevenue.upload.error.file.type", "Nur .xlsx-Dateien erlaubt"));
            model.addAttribute("title", messages.getMessage("orderrevenue.upload.title", "Umsatz-/Kosten-Upload"));
            return "order/revenue-upload";
        }

        // ausführenden Mitarbeiter auslesen
        Employee employee = toEmployee();

        // Lese Header und leite Mapping ab, falls keines explizit gesetzt ist
        List<String> headers = orderRevenueService.getHeaderColumns(file.getInputStream());
        Map<String, String> initial = orderRevenueService.getInitialMapping(headers, employee);
        if (mapping == null || mapping.isEmpty()) {
            mapping = initial;
        } else {
            // Ergänze fehlende Felder mit Auto-Detection
            initial.forEach(mapping::putIfAbsent);
        }

        // Pflichtfelder müssen in der Mapping-Zuordnung vorkommen
        if (!mapping.containsValue(OrderRevenueService.FIELD_ORDER)
                || !mapping.containsValue(OrderRevenueService.FIELD_DATE)
                || !mapping.containsValue(OrderRevenueService.FIELD_TYPE)
                || !mapping.containsValue(OrderRevenueService.FIELD_AMOUNT)) {
            model.addAttribute("title", messages.getMessage("orderrevenue.upload.mapping.title", "Spalten zuordnen"));
            model.addAttribute("headers", headers);
            model.addAttribute("mapping", mapping);
            model.addAttribute("showMapping", true);
            return "order/revenue-upload";
        }

        // Mappings speichern
        Map<String, String> columnMapping = reverseMapping(mapping);
        orderRevenueService.saveMappings(employee, columnMapping);

        // Verarbeitung starten
        OrderRevenueImportResult result = orderRevenueService.importData(file.getInputStream(), comment, columnMapping);

        model.addAttribute("title", messages.getMessage("orderrevenue.upload.done", "Verarbeitung abgeschlossen"));
        model.addAttribute("result", result);
        model.addAttribute("showResult", true);
        return "order/revenue-upload";
    }

    private Map<String, String> reverseMapping(Map<String, String> fieldByHeader) {
        // erwartet: key = Header-Name, value = internes Feld
        return fieldByHeader;
    }

    private org.tb.employee.domain.Employee toEmployee() {
        return employeeService.getEmployeeById(authorizedEmployee.getEmployeeId());
    }

    @Getter
    @Setter
    public static class UploadForm {
        private MultipartFile file;
        private String comment;
        private Map<String, String> mapping = new HashMap<>();
    }

}
