package org.tb.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.EmployeeCostAssignmentData;
import org.tb.budget.domain.EmployeeCostData;
import org.tb.budget.service.EmployeeCostService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.service.EmployeeService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/budget/employee-cost")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class EmployeeCostController {

    private final EmployeeCostService employeeCostService;
    private final EmployeeService employeeService;
    private final SuborderService suborderService;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("costs", employeeCostService.getAll());
        model.addAttribute("assignments", employeeCostService.getAllAssignments());
        model.addAttribute("assignmentForm", new EmployeeCostAssignmentForm());
        model.addAttribute("costNames", employeeCostService.getSelectableCostNames(null));
        model.addAttribute("employees", employeeService.getAllEmployees());
        model.addAttribute("suborders", suborderService.getAllVisibleSuborders());
        return "budget/employee-cost-list";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/create")
    public String createForm(Model model) {
        addCostFormModel(model, new EmployeeCostForm(), false);
        return "budget/employee-cost-form";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        var cost = employeeCostService.getById(id);
        var form = new EmployeeCostForm();
        form.setId(cost.getId());
        form.setName(cost.getName());
        form.setCostEuro(new BigDecimal(cost.getCostCentsPerHour()).movePointLeft(2));
        form.setValidFrom(cost.getValidFrom());
        var until = cost.getValidUntil();
        if (until != null && until.getYear() != 2999) {
            form.setValidUntil(until);
        }
        addCostFormModel(model, form, true);
        return "budget/employee-cost-form";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/store")
    public String store(@ModelAttribute("costForm") EmployeeCostForm form,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (form.getName() == null || form.getName().isBlank()) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.employeecost.error.name.required")));
            addCostFormModel(model, form, !form.isNew());
            return "budget/employee-cost-form";
        }
        if (form.getCostEuro() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.employeecost.error.cost.required")));
            addCostFormModel(model, form, !form.isNew());
            return "budget/employee-cost-form";
        }
        if (form.getValidFrom() == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.employeecost.error.validfrom.required")));
            addCostFormModel(model, form, !form.isNew());
            return "budget/employee-cost-form";
        }
        if (form.getValidUntil() != null && form.getValidFrom().isAfter(form.getValidUntil())) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.employeecost.error.dates.invalid")));
            addCostFormModel(model, form, !form.isNew());
            return "budget/employee-cost-form";
        }

        var data = new EmployeeCostData(
            form.getName(),
            form.getCostEuro().movePointRight(2).intValue(),
            form.getValidFrom(),
            form.getValidUntil()
        );

        try {
            if (form.isNew()) {
                employeeCostService.create(data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.message.created"));
            } else {
                employeeCostService.update(form.getId(), data);
                redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.message.updated"));
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
            addCostFormModel(model, form, !form.isNew());
            return "budget/employee-cost-form";
        }
        return "redirect:/budget/employee-cost";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            employeeCostService.delete(id);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/employee-cost";
    }

    /**
     * The create form sits on the list page, so its errors go back as a toast rather than into a
     * re-rendered form.
     */
    @Authorized(requiresManager = true)
    @PostMapping("/assignments/store")
    public String storeAssignment(@ModelAttribute("assignmentForm") EmployeeCostAssignmentForm form,
                                  RedirectAttributes redirectAttributes) {
        var errors = validateAssignment(form);
        if (!errors.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError", errors.get(0));
            return "redirect:/budget/employee-cost";
        }

        try {
            employeeCostService.createAssignment(toAssignmentData(form));
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.assignment.message.created"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/employee-cost";
    }

    @Authorized(requiresManager = true)
    @GetMapping("/assignments/{id}/edit")
    public String editAssignmentForm(@PathVariable long id, Model model) {
        var assignment = employeeCostService.getAssignmentById(id);
        var form = new EmployeeCostAssignmentForm();
        form.setId(assignment.getId());
        form.setEmployeeCostName(assignment.getEmployeeCostName());
        form.setEmployeeSign(assignment.getEmployeeSign());
        form.setSuborderSign(assignment.getSuborderSign());
        form.setValidFrom(assignment.getValidFrom());
        var until = assignment.getValidUntil();
        if (until != null && until.getYear() != 2999) {
            form.setValidUntil(until);
        }
        addAssignmentFormModel(model, form);
        return "budget/employee-cost-assignment-form";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/assignments/{id}/store")
    public String storeAssignmentEdit(@PathVariable long id,
                                      @ModelAttribute("assignmentForm") EmployeeCostAssignmentForm form,
                                      Model model,
                                      RedirectAttributes redirectAttributes) {
        form.setId(id);
        var errors = validateAssignment(form);
        if (!errors.isEmpty()) {
            model.addAttribute("formErrors", errors);
            addAssignmentFormModel(model, form);
            return "budget/employee-cost-assignment-form";
        }

        try {
            employeeCostService.updateAssignment(id, toAssignmentData(form));
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.assignment.message.updated"));
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList());
            addAssignmentFormModel(model, form);
            return "budget/employee-cost-assignment-form";
        }
        return "redirect:/budget/employee-cost";
    }

    @Authorized(requiresManager = true)
    @PostMapping("/assignments/{id}/delete")
    public String deleteAssignment(@PathVariable long id, RedirectAttributes redirectAttributes) {
        try {
            employeeCostService.deleteAssignment(id);
            redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.assignment.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).findFirst()
                    .orElse(messages.getMessage("main.general.error.unknown")));
        }
        return "redirect:/budget/employee-cost";
    }

    private void addCostFormModel(Model model, EmployeeCostForm form, boolean isEdit) {
        model.addAttribute("costForm", form);
        model.addAttribute("isEdit", isEdit);
    }

    /**
     * The stored category and suborder stay in their select even when no cost record carries the name
     * any more, or the suborder has meanwhile been hidden — otherwise editing an assignment would
     * silently drop the reference (#895).
     */
    private void addAssignmentFormModel(Model model, EmployeeCostAssignmentForm form) {
        model.addAttribute("assignmentForm", form);
        model.addAttribute("costNames", employeeCostService.getSelectableCostNames(form.getEmployeeCostName()));
        model.addAttribute("employees", employeeService.getAllEmployees());
        model.addAttribute("suborders", suborderService.getAllSelectableSuborders(form.getSuborderSign()));
    }

    private List<String> validateAssignment(EmployeeCostAssignmentForm form) {
        var errors = new ArrayList<String>();
        if (form.getEmployeeCostName() == null || form.getEmployeeCostName().isBlank()) {
            errors.add(messages.getMessage("main.employeecost.assignment.error.category.required"));
        }
        if (form.getEmployeeSign() == null || form.getEmployeeSign().isBlank()) {
            errors.add(messages.getMessage("main.employeecost.assignment.error.employee.required"));
        }
        if (form.getValidFrom() == null) {
            errors.add(messages.getMessage("main.employeecost.error.validfrom.required"));
        } else if (form.getValidUntil() != null && form.getValidFrom().isAfter(form.getValidUntil())) {
            errors.add(messages.getMessage("main.employeecost.error.dates.invalid"));
        }
        return errors;
    }

    private static EmployeeCostAssignmentData toAssignmentData(EmployeeCostAssignmentForm form) {
        return new EmployeeCostAssignmentData(
            trimToNull(form.getEmployeeCostName()),
            trimToNull(form.getEmployeeSign()),
            trimToNull(form.getSuborderSign()),
            form.getValidFrom(),
            form.getValidUntil()
        );
    }

}
