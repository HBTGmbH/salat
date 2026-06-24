package org.tb.employee.controller;

import static org.tb.common.GlobalConstants.DEFAULT_VACATION_PER_YEAR;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DurationUtils.validateDuration;
import static org.tb.employee.controller.EmployeeUiStateKeyContributor.EMPLOYEE_CONTRACT_FILTER;
import static org.tb.employee.controller.EmployeeUiStateKeyContributor.EMPLOYEE_ID;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DataValidationUtils;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.web.UiState;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Overtime;
import org.tb.employee.domain.Vacation;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.employee.service.EmployeecontractService.ContractStoredInfo;

@Controller
@RequestMapping("/employees/contracts")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class EmployeecontractController {

    private final EmployeecontractService employeecontractService;
    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final UiState uiState;

    @GetMapping
    public String list(
            @RequestParam(required = false) String ecFilter,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Boolean ecShowInvalid,
            @RequestParam(required = false) Boolean ecShowHidden,
            Model model) {
        var employees = employeecontractService.getVisibleEmployeeContracts().stream()
                .map(Employeecontract::getEmployee)
                .distinct()
                .sorted(Comparator.comparing(Employee::getName))
                .toList();
        if (employeeId == null && employees.size() == 1) {
            employeeId = employees.getFirst().getId();
        }
        var contracts = employeecontractService.getEmployeeContractViewsByFilters(ecShowInvalid, ecFilter, employeeId, ecShowHidden);
        model.addAttribute("employeecontracts", contracts);
        model.addAttribute("employees", employees);
        model.addAttribute("ecFilter", ecFilter);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("ecShowInvalid", ecShowInvalid);
        model.addAttribute("ecShowHidden", ecShowHidden);
        addListModel(model);
        return "employee/employee-contract-list";
    }

    @GetMapping("/view")
    public String view(@RequestParam Long id, Model model) {
        Employeecontract ec;
        try {
            ec = employeecontractService.getEmployeecontractForView(id);
        } catch (AuthorizationException e) {
            throw new ErrorResponseException(HttpStatus.FORBIDDEN);
        }
        if (ec == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        List<Overtime> overtimes = employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(id);
        Duration totalOvertime = overtimes.stream()
                .map(Overtime::getTimeMinutes)
                .reduce(Duration.ZERO, Duration::plus);
        model.addAttribute("employeecontract", ec);
        model.addAttribute("overtimes", overtimes);
        model.addAttribute("totalovertime", DurationUtils.format(totalOvertime));
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "contracts");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.employeecontracts.text", "Employee Contracts"));
        return "employee/employee-contract-view";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/create")
    public String createForm(@RequestParam(required = false) Long employeeId, Model model) {
        var form = new EmployeecontractForm();
        form.setEmployeeId(employeeId != null && employeeId > 0 ? employeeId : null);
        form.setValidFrom(DateUtils.getCurrentYearString() + "-01-01");
        form.setValidUntil("");
        form.setDailyWorkingTime("8:00");
        form.setYearlyVacation(String.valueOf(DEFAULT_VACATION_PER_YEAR));
        form.setInitialOvertime("0:00");
        model.addAttribute("employeecontractForm", form);
        addFormModel(model, form, false, null, null);
        return "employee/employee-contract-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/edit")
    public String editForm(@RequestParam Long id, Model model) {
        Employeecontract ec = employeecontractService.getEmployeeContractWithVacationsById(id);
        var form = toForm(ec);
        model.addAttribute("employeecontractForm", form);

        // Load overtime adjustments
        List<Overtime> overtimes = employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(id);
        Duration totalOvertime = overtimes.stream()
                .map(Overtime::getTimeMinutes)
                .reduce(Duration.ZERO, Duration::plus);

        addFormModel(model, form, true, overtimes, DurationUtils.format(totalOvertime));
        model.addAttribute("currentEmployee", ec.getEmployee().getName());
        return "employee/employee-contract-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/store")
    public String store(@ModelAttribute("employeecontractForm") EmployeecontractForm form,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        validateContractForm(form, bindingResult);

        boolean isCreate = form.getId() == null;
        boolean hasErrors = bindingResult.hasErrors();
        List<String> logs = List.of();
        if (!hasErrors) {
            try {
                ContractStoredInfo info;
                if (form.getId() != null) {
                    info = employeecontractService.updateEmployeecontract(
                            form.getId(),
                            form.getValidFromTyped(),
                            form.getValidUntilTyped(),
                            form.getSupervisorIds(),
                            form.getTaskdescription(),
                            Boolean.TRUE.equals(form.getFreelancer()),
                            Boolean.TRUE.equals(form.getHide()),
                            form.getDailyWorkingTimeTyped(),
                            form.getYearlyVacationTyped(),
                            Boolean.TRUE.equals(form.getResolveConflicts())
                    );
                } else {
                    info = employeecontractService.createEmployeecontract(
                            form.getEmployeeId(),
                            form.getValidFromTyped(),
                            form.getValidUntilTyped(),
                            form.getSupervisorIds(),
                            form.getTaskdescription(),
                            Boolean.TRUE.equals(form.getFreelancer()),
                            Boolean.TRUE.equals(form.getHide()),
                            form.getDailyWorkingTimeTyped(),
                            form.getYearlyVacationTyped(),
                            form.getInitialOvertimeTyped(),
                            Boolean.TRUE.equals(form.getResolveConflicts())
                    );
                }
                logs = info.getLog();
            } catch (ErrorCodeException ex) {
                model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
                hasErrors = true;
            }
        }

        if (hasErrors) {
            if (form.getId() != null) {
                List<Overtime> overtimes = employeecontractService.getOvertimeAdjustmentsByEmployeeContractId(form.getId());
                Duration totalOvertime = overtimes.stream()
                        .map(Overtime::getTimeMinutes)
                        .reduce(Duration.ZERO, Duration::plus);
                addFormModel(model, form, true, overtimes, DurationUtils.format(totalOvertime));
                Employeecontract ec = employeecontractService.getEmployeecontractById(form.getId());
                if (ec != null) {
                    model.addAttribute("currentEmployee", ec.getEmployee().getName());
                }
            } else {
                addFormModel(model, form, false, null, null);
            }
            return "employee/employee-contract-form";
        }

        if (isCreate) {
            uiState.clearState(EMPLOYEE_CONTRACT_FILTER);
            if(!Objects.equals(form.getEmployeeId(), uiState.getLongValue(EMPLOYEE_ID))) {
                uiState.clearState(EMPLOYEE_ID);
            }
        }
        if (!logs.isEmpty()) {
            redirectAttributes.addFlashAttribute("logs", logs);
        }
        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employeecontract.message.stored", "Employee contract saved successfully"));
        return "redirect:/employees/contracts";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/overtime")
    public String addOvertime(@PathVariable Long id,
                              @ModelAttribute("employeecontractForm") EmployeecontractForm form,
                              BindingResult bindingResult,
                              RedirectAttributes redirectAttributes) {
        // Validate overtime fields
        if (form.getNewOvertimeComment() == null || form.getNewOvertimeComment().isBlank()) {
            redirectAttributes.addFlashAttribute("toastError",
                    messages.getMessage("form.employeecontract.error.overtimecomment.missing", "Overtime comment is required"));
            return "redirect:/employees/contracts/edit?id=" + id;
        }
        if (form.getNewOvertimeComment().length() > GlobalConstants.EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH) {
            redirectAttributes.addFlashAttribute("toastError",
                    messages.getMessage("form.employeecontract.error.overtimecomment.toolong", "Overtime comment is too long"));
            return "redirect:/employees/contracts/edit?id=" + id;
        }
        if (!validateDuration(form.getNewOvertime())) {
            redirectAttributes.addFlashAttribute("toastError",
                    messages.getMessage("form.timereport.error.date.wrongformat", "Invalid duration format"));
            return "redirect:/employees/contracts/edit?id=" + id;
        }

        Employeecontract ec = employeecontractService.getEmployeecontractById(id);
        Overtime overtime = new Overtime();
        overtime.setComment(form.getNewOvertimeComment());
        overtime.setEmployeecontract(ec);
        var effective = form.getNewOvertimeEffectiveTyped();
        if (effective != null && effective.isBefore(ec.getValidFrom())) {
            effective = ec.getValidFrom();
        }
        overtime.setEffective(effective);
        overtime.setTimeMinutes(form.getNewOvertimeTyped());
        employeecontractService.create(overtime);

        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employeecontract.overtime.message.stored", "Overtime entry added successfully"));
        return "redirect:/employees/contracts/edit?id=" + id;
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/toggle-hide")
    public String toggleHide(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Employeecontract ec = employeecontractService.toggleHide(id);
            model.addAttribute("ec", ec);
            return "fragments/hide-toggle :: contractHideFlag";
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream().map(Object::toString).findFirst().orElse("Error"));
            return "redirect:/employees/contracts";
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Employeecontract ec = employeecontractService.getEmployeecontractById(id);
        Employee loginEmployee = employeeService.getLoginEmployee();
        if (ec != null && ec.getEmployee().getId().equals(loginEmployee.getId())) {
            redirectAttributes.addFlashAttribute("toastError",
                    messages.getMessage("form.employeecontract.error.delete.isloginemployee", "Cannot delete the contract of the currently logged-in employee"));
            return "redirect:/employees/contracts";
        }
        try {
            employeecontractService.deleteEmployeeContractById(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("form.employeecontract.message.deleted", "Employee contract deleted successfully"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream()
                            .map(Object::toString).findFirst().orElse("Error deleting employee contract"));
        }
        return "redirect:/employees/contracts";
    }

    private void validateContractForm(EmployeecontractForm form, BindingResult bindingResult) {
        if (form.getValidFrom() == null || !DateUtils.validateDate(form.getValidFrom())) {
            bindingResult.rejectValue("validFrom", "error.validFrom",
                    messages.getMessage("form.employeecontract.error.validfrom.wrongformat", "Invalid from date format"));
        }
        if (form.getValidUntil() != null && !form.getValidUntil().isBlank() && !DateUtils.validateDate(form.getValidUntil())) {
            bindingResult.rejectValue("validUntil", "error.validUntil",
                    messages.getMessage("form.employeecontract.error.validuntil.wrongformat", "Invalid until date format"));
        }
        if (!validateDuration(form.getDailyWorkingTime())) {
            bindingResult.rejectValue("dailyWorkingTime", "error.dailyWorkingTime",
                    messages.getMessage("form.employeecontract.error.dailyworkingtime.wrongformat", "Invalid daily working time format"));
        }
        if (form.getInitialOvertime() != null && !form.getInitialOvertime().isBlank() && !validateDuration(form.getInitialOvertime())) {
            bindingResult.rejectValue("initialOvertime", "error.initialOvertime",
                    messages.getMessage("form.employeecontract.error.initialovertime.wrongformat", "Invalid initial overtime format"));
        }
        if (form.getYearlyVacation() == null || form.getYearlyVacation().isBlank()) {
            bindingResult.rejectValue("yearlyVacation", "error.yearlyVacation",
                    messages.getMessage("form.employeecontract.error.yearlyvacation.wrongformat", "Invalid yearly vacation"));
        } else if (!DataValidationUtils.isPositiveInteger(form.getYearlyVacation())) {
            bindingResult.rejectValue("yearlyVacation", "error.yearlyVacation",
                    messages.getMessage("form.employeecontract.error.yearlyvacation.wrongformat", "Yearly vacation must be a positive number"));
        } else if (!DataValidationUtils.isInRange(Integer.parseInt(form.getYearlyVacation()), 0, GlobalConstants.MAX_VACATION_PER_YEAR)) {
            bindingResult.rejectValue("yearlyVacation", "error.yearlyVacation",
                    messages.getMessage("form.employeecontract.error.yearlyvacation.wrongformat", "Yearly vacation is out of range"));
        }
        if (form.getTaskdescription() != null && form.getTaskdescription().length() > GlobalConstants.EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH) {
            bindingResult.rejectValue("taskdescription", "error.taskdescription",
                    messages.getMessage("form.employeecontract.error.taskdescription.toolong", "Task description is too long"));
        }
    }

    private void addFormModel(Model model, EmployeecontractForm form, boolean isEdit, List<Overtime> overtimes, String totalOvertime) {
        var employees = employeeService.getAllEmployees();
        var supervisors = new ArrayList<>(employeeService.getEligibleSupervisors());
        if (isEdit && form != null) {
            var idsToEnsure = new ArrayList<Long>();
            idsToEnsure.addAll(form.getSupervisorIds());
            idsToEnsure.addAll(form.getStoredSupervisorIds());
            for (Long id : idsToEnsure) {
                if (id != null && supervisors.stream().noneMatch(e -> Objects.equals(e.getId(), id))) {
                    Employee storedSupervisor = employeeService.getEmployeeById(id);
                    if (storedSupervisor != null) {
                        supervisors.add(storedSupervisor);
                    }
                }
            }
        }
        supervisors.sort(Comparator.comparing(Employee::getName));
        model.addAttribute("employees", employees);
        model.addAttribute("supervisors", supervisors);
        model.addAttribute("isEdit", isEdit);
        if (isEdit && overtimes != null) {
            model.addAttribute("overtimes", overtimes);
            model.addAttribute("totalovertime", totalOvertime);
        }
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "contracts");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        String titleKey = isEdit ? "main.employeecontract.modify.text" : "main.employeecontract.new.text";
        model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Employee Contract" : "Create Employee Contract"));
    }

    private void addListModel(Model model) {
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "contracts");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.employeecontracts.text", "Employee Contracts"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
    }

    private EmployeecontractForm toForm(Employeecontract ec) {
        var form = new EmployeecontractForm();
        form.setId(ec.getId());
        form.setEmployeeId(ec.getEmployee().getId());
        var supervisorIds = ec.getSupervisors().stream().map(Employee::getId).toList();
        form.setSupervisorIds(new ArrayList<>(supervisorIds));
        form.setStoredSupervisorIds(new ArrayList<>(supervisorIds));
        form.setTaskdescription(ec.getTaskDescription());
        form.setFreelancer(ec.getFreelancer());
        form.setHide(ec.getHide());
        form.setDailyWorkingTime(DurationUtils.format(ec.getDailyWorkingTime()));
        if (!ec.getVacations().isEmpty()) {
            Vacation va = ec.getVacations().getFirst();
            form.setYearlyVacation(va.getEntitlement().toString());
        } else {
            form.setYearlyVacation(String.valueOf(DEFAULT_VACATION_PER_YEAR));
        }
        form.setValidFrom(format(ec.getValidFrom()));
        form.setValidUntil(ec.getValidUntil() != null ? format(ec.getValidUntil()) : "");
        // Initialize overtime effective date
        if (ec.getValidFrom() != null && form.getNewOvertimeEffectiveTyped() != null
                && form.getNewOvertimeEffectiveTyped().isBefore(ec.getValidFrom())) {
            form.setNewOvertimeEffective(format(ec.getValidFrom()));
        }
        return form;
    }

}
