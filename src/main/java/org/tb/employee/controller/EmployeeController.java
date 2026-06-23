package org.tb.employee.controller;

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.SalatUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.web.UiState;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;

@Controller
@RequestMapping("/employees")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final UiState uiState;

    @GetMapping
    public String list(
            @RequestParam(required = false) String eFilter,
            @RequestParam(required = false) Boolean eShowHidden,
            Model model) {
        var employees = employeeService.getEmployeesByFilter(eFilter, eShowHidden);
        employees.sort(Comparator.comparing(Employee::getLastname).thenComparing(Employee::getFirstname));
        model.addAttribute("employees", employees);
        model.addAttribute("eFilter", eFilter);
        model.addAttribute("eShowHidden", eShowHidden);
        addListModel(model);
        return "employee/employee-list";
    }

    @GetMapping("/view")
    public String view(@RequestParam Long id, Model model) {
        Employee employee;
        try {
            employee = employeeService.getEmployeeForView(id);
        } catch (AuthorizationException e) {
            throw new ErrorResponseException(HttpStatus.FORBIDDEN);
        }
        if (employee == null) throw new ErrorResponseException(HttpStatus.NOT_FOUND);
        model.addAttribute("employee", employee);
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "employees");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        return "employee/employee-view";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/create")
    public String createForm(Model model) {
        var form = new EmployeeForm();
        form.setGender("m");
        form.setStatus("ma");
        model.addAttribute("employeeForm", form);
        addFormModel(model, false);
        return "employee/employee-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/edit")
    public String editForm(@RequestParam Long id, Model model) {
        Employee employee = employeeService.getEmployeeById(id);
        var form = toForm(employee);
        model.addAttribute("employeeForm", form);
        addFormModel(model, true);
        return "employee/employee-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/store")
    public String store(@ModelAttribute("employeeForm") EmployeeForm form,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        validateForm(form, bindingResult);

        boolean isCreate = form.getId() == null;
        boolean hasErrors = bindingResult.hasErrors();
        if (!hasErrors) {
            try {
                Employee employee;
                if (form.getId() != null) {
                    employee = employeeService.getEmployeeById(form.getId());
                } else {
                    employee = new Employee();
                    employee.setSalatUser(new SalatUser());
                }
                employee.getSalatUser().setLoginname(form.getLoginname());
                employee.getSalatUser().setStatus(form.getStatus());
                employee.setFirstname(form.getFirstname());
                employee.setLastname(form.getLastname());
                employee.setSign(form.getSign());
                employee.setGender(form.getGender().charAt(0));
                employee.setHide(Boolean.TRUE.equals(form.getHide()));
                employeeService.createOrUpdate(employee);
            } catch (ErrorCodeException ex) {
                model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
                hasErrors = true;
            }
        }

        if (hasErrors) {
            addFormModel(model, form.getId() != null);
            return "employee/employee-form";
        }

        if (isCreate) {
            uiState.clearState(EmployeeUiStateKeyContributor.EMPLOYEE_FILTER);
        }
        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employee.message.stored", "Employee saved successfully"));
        return "redirect:/employees";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/toggle-hide")
    public String toggleHide(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            Employee employee = employeeService.toggleHide(id);
            model.addAttribute("employee", employee);
            return "fragments/hide-toggle :: employeeHideFlag";
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream().map(Object::toString).findFirst().orElse("Error"));
            return "redirect:/employees";
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/anonymize")
    public String anonymize(@PathVariable Long id,
                            @RequestParam String confirmSign,
                            RedirectAttributes redirectAttributes) {
        try {
            employeeService.anonymizeEmployee(id, confirmSign);
            redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("form.employee.message.anonymized", "Employee anonymized successfully"));
            return "redirect:/employees";
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream()
                            .map(Object::toString).findFirst().orElse("Error anonymizing employee"));
            return "redirect:/employees/edit?id=" + id;
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Employee loginEmployee = employeeService.getLoginEmployee();
        if (loginEmployee.getId().equals(id)) {
            redirectAttributes.addFlashAttribute("toastError",
                    messages.getMessage("form.employee.error.delete.isloginemployee", "Cannot delete the currently logged-in employee"));
            return "redirect:/employees";
        }
        try {
            employeeService.deleteEmployeeById(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("form.employee.message.deleted", "Employee deleted successfully"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream()
                            .map(Object::toString).findFirst().orElse("Error deleting employee"));
        }
        return "redirect:/employees";
    }

    private void validateForm(EmployeeForm form, BindingResult bindingResult) {
        if (form.getFirstname() == null || form.getFirstname().isEmpty()) {
            bindingResult.rejectValue("firstname", "error.firstname",
                    messages.getMessage("form.employee.error.firstname.required", "First name is required"));
        } else if (form.getFirstname().length() > GlobalConstants.EMPLOYEE_FIRSTNAME_MAX_LENGTH) {
            bindingResult.rejectValue("firstname", "error.firstname",
                    messages.getMessage("form.employee.error.firstname.toolong", "First name is too long"));
        }

        if (form.getLastname() == null || form.getLastname().isEmpty()) {
            bindingResult.rejectValue("lastname", "error.lastname",
                    messages.getMessage("form.employee.error.lastname.required", "Last name is required"));
        } else if (form.getLastname().length() > GlobalConstants.EMPLOYEE_LASTNAME_MAX_LENGTH) {
            bindingResult.rejectValue("lastname", "error.lastname",
                    messages.getMessage("form.employee.error.lastname.toolong", "Last name is too long"));
        }

        if (form.getSign() == null || form.getSign().isEmpty()) {
            bindingResult.rejectValue("sign", "error.sign",
                    messages.getMessage("form.employee.error.sign.required", "Sign is required"));
        } else if (form.getSign().length() > GlobalConstants.EMPLOYEE_SIGN_MAX_LENGTH) {
            bindingResult.rejectValue("sign", "error.sign",
                    messages.getMessage("form.employee.error.sign.toolong", "Sign is too long"));
        }

        if (form.getLoginname() == null || form.getLoginname().isEmpty()) {
            bindingResult.rejectValue("loginname", "error.loginname",
                    messages.getMessage("form.employee.error.loginname.required", "Login name is required"));
        } else if (form.getLoginname().length() > GlobalConstants.EMPLOYEE_LOGINNAME_MAX_LENGTH) {
            bindingResult.rejectValue("loginname", "error.loginname",
                    messages.getMessage("form.employee.error.loginname.toolong", "Login name is too long"));
        }

        if (form.getStatus() == null || form.getStatus().isEmpty()) {
            bindingResult.rejectValue("status", "error.status",
                    messages.getMessage("form.employee.error.status.required", "Status is required"));
        }

        // For new employees, check if name already exists
        if (form.getId() == null && !bindingResult.hasFieldErrors("firstname") && !bindingResult.hasFieldErrors("lastname")) {
            boolean nameExists = employeeService.getAllEmployees().stream()
                    .anyMatch(em -> em.getFirstname().equalsIgnoreCase(form.getFirstname())
                            && em.getLastname().equalsIgnoreCase(form.getLastname()));
            if (nameExists) {
                bindingResult.rejectValue("lastname", "error.lastname",
                        messages.getMessage("form.employee.error.name.alreadyexists", "An employee with this name already exists"));
            }
        }
    }

    private void addListModel(Model model) {
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "employees");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
    }

    private void addFormModel(Model model, boolean isEdit) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("section", "employees");
        model.addAttribute("subSection", "employees");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.employees.text", "Employees"));
        String titleKey = isEdit ? "main.general.editemployee.text" : "main.general.addemployee.text";
        model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Employee" : "Create Employee"));
    }

    private EmployeeForm toForm(Employee employee) {
        var form = new EmployeeForm();
        form.setId(employee.getId());
        form.setFirstname(employee.getFirstname());
        form.setLastname(employee.getLastname());
        form.setSign(employee.getSign());
        form.setLoginname(employee.getLoginname());
        form.setStatus(employee.getStatus());
        form.setGender(String.valueOf(employee.getGender()));
        form.setHide(employee.getHide());
        return form;
    }

}
