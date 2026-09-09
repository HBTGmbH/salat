package org.tb.budget.controller;

import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.budget.domain.EmployeeCost;
import org.tb.budget.domain.EmployeeCostAssignment;
import org.tb.budget.domain.EmployeeCostAssignmentData;
import org.tb.budget.domain.EmployeeCostData;
import org.tb.budget.service.EmployeeCostService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.service.EmployeeService;
import org.tb.order.service.SuborderService;

/**
 * The cost categories and their assignments (#954).
 *
 * <p>A category is a name, not a record: several {@link EmployeeCost} rows share one name to model a
 * rate that changed over time, and an assignment binds to the category by that name. The views
 * follow that structure — the overview lists categories, everything else happens on the page of one
 * category. The name is therefore the key of that page and travels as a request parameter; as a path
 * variable a {@code /} in the name would break the path.
 */
@Controller
@RequestMapping("/budget/employee-cost")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class EmployeeCostController {

    private static final String CATEGORY_VIEW = "redirect:/budget/employee-cost/category";

    /** An open validity end is stored as {@code 2999-12-31} (→ {@code EmployeeCostService}). */
    private static final int OPEN_END_YEAR = 2999;

    private final EmployeeCostService employeeCostService;
    private final EmployeeService employeeService;
    private final SuborderService suborderService;
    private final ErrorCodeViewHelper errorCodeViewHelper;
    private final MessageSourceAccessor messages;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", employeeCostService.getCategories());
        return "budget/employee-cost-list";
    }

    // --- creating a category ---------------------------------------------------------------------

    @GetMapping("/create")
    public String createForm(Model model) {
        model.addAttribute("costForm", new EmployeeCostForm());
        return "budget/employee-cost-form";
    }

    /**
     * Name and first rate, nothing else: the validity of that rate is set by the service to today
     * with an open end. A rate that has to start earlier is corrected on the category page.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/store")
    public String store(@ModelAttribute("costForm") EmployeeCostForm form,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        var errors = new ArrayList<String>();
        if (trimToNull(form.getName()) == null) {
            errors.add(messages.getMessage("main.employeecost.error.name.required"));
        }
        if (form.getCostEuro() == null) {
            errors.add(messages.getMessage("main.employeecost.error.cost.required"));
        }
        if (!errors.isEmpty()) {
            model.addAttribute("formErrors", errors);
            return "budget/employee-cost-form";
        }

        var name = form.getName().trim();
        try {
            employeeCostService.createCategory(name, toCents(form.getCostEuro()));
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors", toMessages(ex));
            return "budget/employee-cost-form";
        }
        redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.message.created"));
        return redirectToCategory(name, redirectAttributes);
    }

    // --- one category ----------------------------------------------------------------------------

    @GetMapping("/category")
    public String category(@RequestParam("name") String name, Model model,
                           RedirectAttributes redirectAttributes) {
        var rates = employeeCostService.getByName(name);
        var assignments = assignmentsOf(name);
        if (rates.isEmpty() && assignments.isEmpty()) {
            redirectAttributes.addFlashAttribute("toastError",
                messages.getMessage("main.employeecost.error.category.notfound", new Object[] {name}));
            return "redirect:/budget/employee-cost";
        }
        addCategoryModel(model, name, rates, assignments);
        return "budget/employee-cost-category";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/category/rename")
    public String rename(@RequestParam("name") String name,
                         @RequestParam("newName") String newName,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        var target = trimToNull(newName);
        if (target == null) {
            model.addAttribute("formErrors", List.of(messages.getMessage("main.employeecost.error.name.required")));
            addCategoryModel(model, name, employeeCostService.getByName(name), assignmentsOf(name));
            return "budget/employee-cost-category";
        }
        try {
            employeeCostService.renameCategory(name, target);
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors", toMessages(ex));
            addCategoryModel(model, name, employeeCostService.getByName(name), assignmentsOf(name));
            return "budget/employee-cost-category";
        }
        redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.employeecost.message.renamed"));
        return redirectToCategory(target, redirectAttributes);
    }

    // --- the rate periods of a category ----------------------------------------------------------

    @GetMapping("/rates/create")
    public String createRateForm(@RequestParam("name") String name, Model model) {
        var form = new EmployeeCostForm();
        form.setName(name);
        model.addAttribute("costForm", form);
        model.addAttribute("isEdit", false);
        return "budget/employee-cost-rate-form";
    }

    @GetMapping("/rates/{id}/edit")
    public String editRateForm(@PathVariable long id, Model model) {
        var cost = employeeCostService.getById(id);
        var form = new EmployeeCostForm();
        form.setId(cost.getId());
        form.setName(cost.getName());
        form.setCostEuro(new BigDecimal(cost.getCostCentsPerHour()).movePointLeft(2));
        form.setValidFrom(cost.getValidFrom());
        form.setValidUntil(openEnd(cost.getValidUntil()));
        model.addAttribute("costForm", form);
        model.addAttribute("isEdit", true);
        return "budget/employee-cost-rate-form";
    }

    /**
     * The category of a rate is not editable here — a rename carries all periods and all assignments
     * along and therefore belongs to the category, not to one of its periods.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/rates/store")
    public String storeRate(@ModelAttribute("costForm") EmployeeCostForm form,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        var errors = new ArrayList<String>();
        if (form.getCostEuro() == null) {
            errors.add(messages.getMessage("main.employeecost.error.cost.required"));
        }
        if (form.getValidFrom() == null) {
            errors.add(messages.getMessage("main.employeecost.error.validfrom.required"));
        } else if (form.getValidUntil() != null && form.getValidFrom().isAfter(form.getValidUntil())) {
            errors.add(messages.getMessage("main.employeecost.error.dates.invalid"));
        }
        if (!errors.isEmpty()) {
            return rateFormWithErrors(model, form, errors);
        }

        var data = new EmployeeCostData(form.getName(), toCents(form.getCostEuro()),
            form.getValidFrom(), form.getValidUntil());
        try {
            if (form.isNew()) {
                employeeCostService.create(data);
                redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("main.employeecost.rate.message.created"));
            } else {
                employeeCostService.update(form.getId(), data);
                redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("main.employeecost.rate.message.updated"));
            }
        } catch (ErrorCodeException ex) {
            return rateFormWithErrors(model, form, toMessages(ex));
        }
        return redirectToCategory(form.getName(), redirectAttributes);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/rates/{id}/delete")
    public String deleteRate(@PathVariable long id, RedirectAttributes redirectAttributes) {
        var name = employeeCostService.getById(id).getName();
        try {
            employeeCostService.delete(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.employeecost.rate.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessage(ex));
        }
        // Also on success: the category survives its last rate as long as assignments name it, and
        // the overview would not show what just happened.
        return redirectToCategory(name, redirectAttributes);
    }

    // --- the assignments of a category -----------------------------------------------------------

    @GetMapping("/assignments/create")
    public String createAssignmentForm(@RequestParam("name") String name, Model model) {
        var form = new EmployeeCostAssignmentForm();
        form.setEmployeeCostName(name);
        addAssignmentFormModel(model, form);
        return "budget/employee-cost-assignment-form";
    }

    @GetMapping("/assignments/{id}/edit")
    public String editAssignmentForm(@PathVariable long id, Model model) {
        var assignment = employeeCostService.getAssignmentById(id);
        var form = new EmployeeCostAssignmentForm();
        form.setId(assignment.getId());
        form.setEmployeeCostName(assignment.getEmployeeCostName());
        form.setEmployeeSign(assignment.getEmployeeSign());
        form.setSuborderSign(assignment.getSuborderSign());
        form.setValidFrom(assignment.getValidFrom());
        form.setValidUntil(openEnd(assignment.getValidUntil()));
        addAssignmentFormModel(model, form);
        return "budget/employee-cost-assignment-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/assignments/store")
    public String storeAssignment(@ModelAttribute("assignmentForm") EmployeeCostAssignmentForm form,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        var errors = validateAssignment(form);
        if (!errors.isEmpty()) {
            model.addAttribute("formErrors", errors);
            addAssignmentFormModel(model, form);
            return "budget/employee-cost-assignment-form";
        }

        try {
            if (form.isNew()) {
                employeeCostService.createAssignment(toAssignmentData(form));
                redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("main.employeecost.assignment.message.created"));
            } else {
                employeeCostService.updateAssignment(form.getId(), toAssignmentData(form));
                redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("main.employeecost.assignment.message.updated"));
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("formErrors", toMessages(ex));
            addAssignmentFormModel(model, form);
            return "budget/employee-cost-assignment-form";
        }
        return redirectToCategory(form.getEmployeeCostName(), redirectAttributes);
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/assignments/{id}/delete")
    public String deleteAssignment(@PathVariable long id, RedirectAttributes redirectAttributes) {
        var name = employeeCostService.getAssignmentById(id).getEmployeeCostName();
        try {
            employeeCostService.deleteAssignment(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("main.employeecost.assignment.message.deleted"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError", firstMessage(ex));
        }
        return redirectToCategory(name, redirectAttributes);
    }

    // --- shared ----------------------------------------------------------------------------------

    /** Non-flash attributes end up as query parameters, encoded — which a category name needs. */
    private static String redirectToCategory(String name, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("name", name);
        return CATEGORY_VIEW;
    }

    private List<EmployeeCostAssignment> assignmentsOf(String name) {
        return employeeCostService.getAssignmentsByName(name).stream()
            .sorted(Comparator.comparing(EmployeeCostAssignment::getEmployeeSign)
                .thenComparing(EmployeeCostAssignment::getValidFrom))
            .toList();
    }

    private void addCategoryModel(Model model, String name, List<EmployeeCost> rates,
                                  List<EmployeeCostAssignment> assignments) {
        model.addAttribute("categoryName", name);
        model.addAttribute("rates", rates);
        model.addAttribute("assignments", assignments);
    }

    private String rateFormWithErrors(Model model, EmployeeCostForm form, List<String> errors) {
        model.addAttribute("formErrors", errors);
        model.addAttribute("costForm", form);
        model.addAttribute("isEdit", !form.isNew());
        return "budget/employee-cost-rate-form";
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
        model.addAttribute("isEdit", !form.isNew());
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

    private static Integer toCents(BigDecimal costEuro) {
        return costEuro.movePointRight(2).intValue();
    }

    /** The stored far future date means "no end"; a form must show it as an empty field. */
    private static LocalDate openEnd(LocalDate validUntil) {
        return validUntil != null && validUntil.getYear() != OPEN_END_YEAR ? validUntil : null;
    }

    private List<String> toMessages(ErrorCodeException ex) {
        return errorCodeViewHelper.toViewMessages(ex).stream().map(m -> m.resolved()).toList();
    }

    private String firstMessage(ErrorCodeException ex) {
        return toMessages(ex).stream().findFirst()
            .orElse(messages.getMessage("main.general.error.unknown"));
    }

}
