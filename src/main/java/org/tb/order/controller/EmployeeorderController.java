package org.tb.order.controller;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;
import static org.tb.common.util.DurationUtils.validateDuration;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.EmployeeOrderViewDecorator;

@Controller
@RequestMapping("/orders/employeeorders")
@RequiredArgsConstructor
public class EmployeeorderController {

    private final EmployeeorderService employeeorderService;
    private final EmployeecontractService employeecontractService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String list(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long suborderId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Boolean show,
            @RequestParam(required = false) Boolean showActualHours,
            Model model) {

        var employeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        var orders = customerorderService.getAllCustomerorders();

        List<?> employeeOrders;
        if (Boolean.TRUE.equals(showActualHours)) {
            employeeOrders = employeeorderService
                    .getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId)
                    .stream()
                    .map(eo -> new EmployeeOrderViewDecorator(employeeorderService, eo))
                    .toList();
        } else {
            employeeOrders = employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);
        }

        List<Suborder> suborders = List.of();
        if (orderId != null) {
            suborders = suborderService.getSubordersByCustomerorderId(orderId);
        }

        model.addAttribute("employeecontracts", employeeContracts);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("employeeorders", employeeOrders);
        model.addAttribute("employeeContractId", employeeContractId);
        model.addAttribute("orderId", orderId);
        model.addAttribute("suborderId", suborderId);
        model.addAttribute("filter", filter);
        model.addAttribute("show", show);
        model.addAttribute("showActualHours", showActualHours);
        model.addAttribute("showActualHoursToggle", Boolean.TRUE.equals(showActualHours));
        addListModel(model);
        return "order/employee-order-list";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/create")
    public String createForm(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long suborderId,
            @RequestParam(required = false) Boolean showOnlyValid,
            Model model) {

        var form = new EmployeeorderForm();
        form.setEmployeeContractId(employeeContractId);
        form.setShowOnlyValid(showOnlyValid == null ? Boolean.TRUE : showOnlyValid);

        if (orderId != null) {
            Customerorder co = customerorderService.getCustomerorderById(orderId);
            if (co != null) {
                form.setOrderId(orderId);
                form.setValidFrom(co.getFromDate() != null ? format(co.getFromDate()) : format(today()));
                form.setValidUntil(co.getUntilDate() != null ? format(co.getUntilDate()) : "");

                // Determine which suborder to pre-select
                boolean onlyValid = Boolean.TRUE.equals(form.getShowOnlyValid());
                List<Suborder> visibleSuborders = getVisibleSuborders(orderId, onlyValid);
                if (suborderId != null) {
                    form.setSuborderId(suborderId);
                    Suborder so = suborderService.getSuborderById(suborderId);
                    prefillDebitHours(form, so);
                } else if (!visibleSuborders.isEmpty()) {
                    Suborder firstSuborder = visibleSuborders.getFirst();
                    form.setSuborderId(firstSuborder.getId());
                    prefillDebitHours(form, firstSuborder);
                }
            }
        } else {
            form.setValidFrom(format(today()));
            form.setValidUntil("");
        }

        model.addAttribute("employeeorderForm", form);
        addFormModel(model, false, orderId);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/edit")
    public String editForm(@RequestParam Long id, Model model) {
        Employeeorder eo = employeeorderService.getEmployeeorderById(id);
        var form = toForm(eo);
        model.addAttribute("employeeorderForm", form);
        addFormModel(model, true, eo.getSuborder().getCustomerorder().getId());
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/store")
    public String store(
            @ModelAttribute("employeeorderForm") EmployeeorderForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {

        validateForm(form, bindingResult);

        if (bindingResult.hasErrors()) {
            addFormModel(model, form.getId() != null, form.getOrderId());
            return "order/employee-order-form";
        }

        Employeeorder eo;
        if (form.getId() != null) {
            eo = employeeorderService.getEmployeeorderById(form.getId());
        } else {
            eo = new Employeeorder();
        }

        eo.setEmployeecontract(employeecontractService.getEmployeecontractById(form.getEmployeeContractId()));
        eo.setSuborder(suborderService.getSuborderById(form.getSuborderId()));
        eo.setFromDate(form.getValidFromTyped());
        eo.setUntilDate(form.getValidUntilTyped());
        if (eo.getSign() == null) {
            eo.setSign(" ");
        }

        Duration debitDuration = form.getDebithoursTyped();
        if (debitDuration == null || debitDuration.isZero()) {
            eo.setDebithours(Duration.ZERO);
            eo.setDebithoursunit(null);
        } else {
            eo.setDebithours(debitDuration);
            eo.setDebithoursunit(form.getDebithoursunit());
        }

        try {
            if (eo.isNew()) {
                employeeorderService.create(eo);
            } else {
                employeeorderService.update(eo);
            }
        } catch (ErrorCodeException ex) {
            model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
            addFormModel(model, form.getId() != null, form.getOrderId());
            return "order/employee-order-form";
        }

        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employeeorder.message.stored", "Employee order saved successfully"));
        return "redirect:/orders/employeeorders";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeorderService.deleteEmployeeorderById(id);
            redirectAttributes.addFlashAttribute("toastSuccess",
                    messages.getMessage("form.employeeorder.message.deleted", "Employee order deleted successfully"));
        } catch (ErrorCodeException ex) {
            redirectAttributes.addFlashAttribute("toastError",
                    errorCodeViewHelper.toViewMessages(ex).stream()
                            .map(Object::toString).findFirst().orElse("Error deleting employee order"));
        }
        return "redirect:/orders/employeeorders";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/adjust-dates")
    public String adjustDates(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long suborderId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Boolean show,
            RedirectAttributes redirectAttributes) {

        var employeeOrders = employeeorderService.getEmployeeordersByFilters(show, filter, employeeContractId, orderId, suborderId);
        for (Employeeorder employeeorder : employeeOrders) {
            if (!employeeorder.getFitsToSuperiorObjects()) {
                boolean changed = false;
                // adjust to employee contract
                if (employeeorder.getFromDate().isBefore(employeeorder.getEmployeecontract().getValidFrom())) {
                    employeeorder.setFromDate(employeeorder.getEmployeecontract().getValidFrom());
                    changed = true;
                }
                if (employeeorder.getEmployeecontract().getValidUntil() != null &&
                        (employeeorder.getUntilDate() == null ||
                                employeeorder.getUntilDate().isAfter(employeeorder.getEmployeecontract().getValidUntil()))) {
                    employeeorder.setUntilDate(employeeorder.getEmployeecontract().getValidUntil());
                    changed = true;
                }
                // adjust to suborder
                if (employeeorder.getFromDate().isBefore(employeeorder.getSuborder().getFromDate())) {
                    employeeorder.setFromDate(employeeorder.getSuborder().getFromDate());
                    changed = true;
                }
                if (employeeorder.getSuborder().getUntilDate() != null &&
                        (employeeorder.getUntilDate() == null ||
                                employeeorder.getUntilDate().isAfter(employeeorder.getSuborder().getUntilDate()))) {
                    employeeorder.setUntilDate(employeeorder.getSuborder().getUntilDate());
                    changed = true;
                }
                if (changed && employeeorder.getUntilDate() != null &&
                        !employeeorder.getFromDate().isAfter(employeeorder.getUntilDate())) {
                    employeeorderService.update(employeeorder);
                } else if (changed) {
                    employeeorderService.deleteEmployeeorderById(employeeorder.getId());
                }
            }
        }

        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employeeorder.adjustdates.success", "Dates adjusted successfully"));
        return "redirect:/orders/employeeorders";
    }

    private void validateForm(EmployeeorderForm form, BindingResult bindingResult) {
        if (form.getValidFrom() == null || !validateDate(form.getValidFrom())) {
            bindingResult.rejectValue("validFrom", "error.validFrom",
                    messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
        }
        if (form.getValidUntil() != null && !form.getValidUntil().isBlank() && !validateDate(form.getValidUntil())) {
            bindingResult.rejectValue("validUntil", "error.validUntil",
                    messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
        }
        if (bindingResult.hasErrors()) {
            return; // further validation not possible
        }

        var validFrom = form.getValidFromTyped();
        var validUntil = form.getValidUntilTyped();

        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            bindingResult.rejectValue("validUntil", "error.validUntil",
                    messages.getMessage("form.timereport.error.date.endbeforebegin", "End date must not be before start date"));
        }

        if (form.getSuborderId() == null || form.getSuborderId() <= 0) {
            bindingResult.rejectValue("suborderId", "error.suborderId",
                    messages.getMessage("form.employeeorder.suborder.invalid", "Please select a valid suborder"));
        }

        if (form.getDebithours() != null && !form.getDebithours().isBlank() && !validateDuration(form.getDebithours())) {
            bindingResult.rejectValue("debithours", "error.debithours",
                    messages.getMessage("form.employeeorder.error.debithours.wrongformat", "Invalid debit hours format"));
        }

        if (bindingResult.hasErrors()) {
            return;
        }

        // Check for overlap with other employee orders for same contract+suborder
        if (form.getEmployeeContractId() != null && form.getSuborderId() != null && validFrom != null) {
            var existing = employeeorderService.getEmployeeOrdersByEmployeeContractIdAndSuborderId(
                    form.getEmployeeContractId(), form.getSuborderId());
            Employeeorder tempOrder = new Employeeorder();
            tempOrder.setFromDate(validFrom);
            tempOrder.setUntilDate(validUntil);
            for (Employeeorder other : existing) {
                if (!Objects.equals(form.getId(), other.getId()) && tempOrder.overlaps(other)) {
                    bindingResult.reject("error.overleap",
                            messages.getMessage("form.employeeorder.error.overleap", "Overlapping employee order exists"));
                    break;
                }
            }
        }

        // Check dates fit to employee contract and suborder
        if (form.getEmployeeContractId() != null && form.getSuborderId() != null && validFrom != null) {
            var ec = employeecontractService.getEmployeecontractById(form.getEmployeeContractId());
            var suborder = suborderService.getSuborderById(form.getSuborderId());
            if (ec != null && validFrom.isBefore(ec.getValidFrom())) {
                bindingResult.rejectValue("validFrom", "error.validFrom",
                        messages.getMessage("form.employeeorder.error.date.outofrange.employeecontract",
                                "Start date is before employee contract start"));
            }
            if (suborder != null && validFrom.isBefore(suborder.getFromDate())) {
                bindingResult.rejectValue("validFrom", "error.validFrom",
                        messages.getMessage("form.employeeorder.error.date.outofrange.suborder",
                                "Start date is before suborder start"));
            }
            if (ec != null && ec.getValidUntil() != null &&
                    (validUntil == null || validUntil.isAfter(ec.getValidUntil()))) {
                bindingResult.rejectValue("validUntil", "error.validUntil",
                        messages.getMessage("form.employeeorder.error.date.outofrange.employeecontract",
                                "End date exceeds employee contract end"));
            }
            if (suborder != null && suborder.getUntilDate() != null &&
                    (validUntil == null || validUntil.isAfter(suborder.getUntilDate()))) {
                bindingResult.rejectValue("validUntil", "error.validUntil",
                        messages.getMessage("form.employeeorder.error.date.outofrange.suborder",
                                "End date exceeds suborder end"));
            }
        }
    }

    private void addFormModel(Model model, boolean isEdit, Long selectedOrderId) {
        var employeeContracts = employeecontractService.getViewableEmployeeContractsForAuthorizedUserValidAt(today());
        List<Customerorder> orders;
        if (authorizedUser.isManager()) {
            orders = customerorderService.getAllCustomerorders();
        } else {
            orders = customerorderService.getCustomerOrdersByResponsibleEmployeeId(authorizedEmployee.getEmployeeId());
        }

        List<Suborder> suborders = List.of();
        if (selectedOrderId != null) {
            suborders = getVisibleSuborders(selectedOrderId, true);
        }

        model.addAttribute("employeecontracts", employeeContracts);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "employeeorders");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
        String titleKey = isEdit ? "main.general.editemployeeorder.text" : "main.general.addemployeeorder.text";
        model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Employee Order" : "Create Employee Order"));
    }

    private void addListModel(Model model) {
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "employeeorders");
        model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.employeeorders.text", "Employee Orders"));
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    }

    private List<Suborder> getVisibleSuborders(long customerOrderId, boolean showOnlyValid) {
        return suborderService.getSubordersByCustomerorderId(customerOrderId, false).stream()
                .filter(so -> !so.isHide())
                .filter(so -> !showOnlyValid || so.getCurrentlyValid())
                .toList();
    }

    private void prefillDebitHours(EmployeeorderForm form, Suborder suborder) {
        if (suborder != null && suborder.getDebithours() != null && !suborder.getDebithours().isZero()) {
            form.setDebithours(DurationUtils.format(suborder.getDebithours()));
            form.setDebithoursunit(suborder.getDebithoursunit());
        }
    }

    private EmployeeorderForm toForm(Employeeorder eo) {
        var form = new EmployeeorderForm();
        form.setId(eo.getId());
        form.setEmployeeContractId(eo.getEmployeecontract().getId());
        form.setOrderId(eo.getSuborder().getCustomerorder().getId());
        form.setSuborderId(eo.getSuborder().getId());
        form.setValidFrom(format(eo.getFromDate()));
        form.setValidUntil(eo.getUntilDate() != null ? format(eo.getUntilDate()) : "");
        if (eo.getDebithours() != null && !eo.getDebithours().isZero()) {
            form.setDebithours(DurationUtils.format(eo.getDebithours()));
            form.setDebithoursunit(eo.getDebithoursunit());
        }
        return form;
    }

}
