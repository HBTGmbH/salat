package org.tb.order.controller;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.common.util.DateUtils.validateDate;
import static org.tb.common.util.DurationUtils.validateDuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.customer.service.CustomerService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.domain.EmployeeorderListItemDTO;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

@Controller
@RequestMapping("/orders/employeeorders")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class EmployeeorderController {

    private final EmployeeorderService employeeorderService;
    private final CustomerService customerService;
    private final EmployeecontractService employeecontractService;
    private final CustomerorderService customerorderService;
    private final SuborderService suborderService;
    private final AuthorizedEmployee authorizedEmployee;
    private final MessageSourceAccessor messages;
    private final ErrorCodeViewHelper errorCodeViewHelper;

    @GetMapping
    public String list(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long suborderId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Boolean show,
            @RequestParam(required = false) Boolean showActualHours,
            @RequestParam(required = false) Boolean showHidden,
            HttpServletRequest request,
            HttpSession session,
            Model model) {
        if (request.getParameterMap().containsKey("filter")) {
            session.setAttribute("orders.employeeorders.filter", filter);
            session.setAttribute("orders.employeeorders.customerId", customerId);
            session.setAttribute("orders.employeeorders.employeeContractId", employeeContractId);
            session.setAttribute("orders.employeeorders.orderId", orderId);
            session.setAttribute("orders.employeeorders.suborderId", suborderId);
            session.setAttribute("orders.employeeorders.show", show);
            session.setAttribute("orders.employeeorders.showActualHours", showActualHours);
            session.setAttribute("orders.employeeorders.showHidden", showHidden);
        } else {
            filter = (String) session.getAttribute("orders.employeeorders.filter");
            employeeContractId = (Long) session.getAttribute("orders.employeeorders.employeeContractId");
            customerId = (Long) session.getAttribute("orders.employeeorders.customerId");
            orderId = (Long) session.getAttribute("orders.employeeorders.orderId");
            suborderId = (Long) session.getAttribute("orders.employeeorders.suborderId");
            show = (Boolean) session.getAttribute("orders.employeeorders.show");
            showActualHours = (Boolean) session.getAttribute("orders.employeeorders.showActualHours");
            showHidden = (Boolean) session.getAttribute("orders.employeeorders.showHidden");
        }

        var employeeContracts = employeecontractService.getVisibleEmployeeContracts();
        if (employeeContractId == null && employeeContracts.size() == 1) {
            employeeContractId = employeeContracts.getFirst().getId();
        }
        var orders = customerorderService.getCustomerordersByFilters(show, filter, customerId, showHidden);

        var filterSet = (filter != null && !filter.isEmpty()) ||
                        customerId != null ||
                        employeeContractId != null ||
                        orderId != null ||
                        suborderId != null;

        List<EmployeeorderListItemDTO> employeeOrders = List.of();
        if(filterSet) {
            employeeOrders = employeeorderService.getEmployeeorderListItemsByFilters(
                show, filter, employeeContractId, customerId, orderId, suborderId, Boolean.TRUE.equals(showActualHours), showHidden);
        }

        List<Suborder> suborders = List.of();
        if (orderId != null) {
            suborders = suborderService.getSubordersByCustomerorderId(orderId);
        }

        model.addAttribute("customerId", customerId);
        model.addAttribute("employeecontracts", employeeContracts);
        model.addAttribute("orders", orders);
        model.addAttribute("suborders", suborders);
        model.addAttribute("employeeorders", employeeOrders);
        model.addAttribute("employeeContractId", employeeContractId);
        model.addAttribute("orderId", orderId);
        model.addAttribute("suborderId", suborderId);
        model.addAttribute("filter", filter);
        model.addAttribute("show", show);
        model.addAttribute("showHidden", showHidden);
        model.addAttribute("showActualHours", showActualHours);
        model.addAttribute("showActualHoursToggle", Boolean.TRUE.equals(showActualHours));
        addListModel(model);
        boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
        model.addAttribute("htmxRequest", htmxRequest);
        return htmxRequest ? "order/employee-order-list :: results" : "order/employee-order-list";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/create")
    public String createForm(
            @RequestParam(required = false) Long employeeContractId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) Long suborderId,
            Model model) {

        var form = (EmployeeorderForm) model.asMap().get("prefillForm");
        if (form == null) {
            form = new EmployeeorderForm();
            form.setEmployeeContractId(employeeContractId);
            form.setCustomerId(customerId);
            form.setOrderId(orderId);
            form.setSuborderId(suborderId);
            form.setValidFrom(format(today()));
        }

        addFormModel(model, form, false, true);
        prefillValidity(form);
        prefillDebitHours(form);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/edit")
    public String editForm(@RequestParam Long id, Model model) {
        Employeeorder eo = employeeorderService.getEmployeeorderById(id);
        var form = toForm(eo);
        addFormModel(model, form, true, true);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/change-employeecontract")
    public String changeEmployeecontract(@ModelAttribute("employeeorderForm") EmployeeorderForm form, Model model,
        HttpServletRequest request) {
        addFormModel(model, form, form.getId() != null);
        prefillValidity(form);
        boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
        model.addAttribute("htmxRequest", htmxRequest);
        model.addAttribute("ordersChanged", true);
        model.addAttribute("subordersChanged", true);
        model.addAttribute("datesChanged", true);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/change-customer")
    public String changeCustomer(@ModelAttribute("employeeorderForm") EmployeeorderForm form, Model model,
        HttpServletRequest request) {
        form.setOrderId(null);
        form.setSuborderId(null);
        addFormModel(model, form, form.getId() != null);
        prefillValidity( form);
        boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
        model.addAttribute("htmxRequest", htmxRequest);
        model.addAttribute("ordersChanged", true);
        model.addAttribute("subordersChanged", true);
        model.addAttribute("datesChanged", true);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/change-customerorder")
    public String changeCustomerorder(@ModelAttribute("employeeorderForm") EmployeeorderForm form, Model model,
        HttpServletRequest request) {
        form.setSuborderId(null);
        addFormModel(model, form, form.getId() != null);
        prefillValidity( form);
        boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
        model.addAttribute("htmxRequest", htmxRequest);
        model.addAttribute("subordersChanged", true);
        model.addAttribute("datesChanged", true);
        return "order/employee-order-form";
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/change-suborder")
    public String changeSuborder(@ModelAttribute("employeeorderForm") EmployeeorderForm form, Model model,
        HttpServletRequest request) {
        prefillValidity(form);
        prefillDebitHours(form);
        addFormModel(model, form, form.getId() != null);
        prefillValidity( form);
        boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
        model.addAttribute("htmxRequest", htmxRequest);
        model.addAttribute("datesChanged", true);
        return "order/employee-order-form";
    }

    private void prefillValidity(EmployeeorderForm form) {
        if(form.getEmployeeContractId() != null && form.getSuborderId() != null) {
            Suborder so = suborderService.getSuborderById(form.getSuborderId());
            var contract = employeecontractService.getEmployeecontractById(form.getEmployeeContractId());
            var from = DateUtils.max(contract.getValidFrom(), so.getFromDate());
            LocalDate until = DateUtils.min(contract.getValidUntil(), so.getUntilDate());
            form.setValidFrom(format(from));
            form.setValidUntil(until != null ? format(until) : "");
        }
    }

    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/store")
    public String store(
            @ModelAttribute("employeeorderForm") EmployeeorderForm form,
            BindingResult bindingResult,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            @RequestParam(required = false) String saveAndNew) {

        validateForm(form, bindingResult);

        if (bindingResult.hasErrors()) {
            addFormModel(model, form, form.getId() != null);
            return "order/employee-order-form";
        }

        boolean isCreate = form.getId() == null;
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
            addFormModel(model, form, form.getId() != null);
            return "order/employee-order-form";
        }

        if (isCreate) {
            session.setAttribute("orders.employeeorders.employeeContractId", form.getEmployeeContractId());
            session.setAttribute("orders.employeeorders.customerId", form.getCustomerId());
            session.setAttribute("orders.employeeorders.orderId", form.getOrderId());
            session.setAttribute("orders.employeeorders.suborderId", form.getSuborderId());
            session.setAttribute("orders.employeeorders.filter", null);
        }
        redirectAttributes.addFlashAttribute("toastSuccess",
                messages.getMessage("form.employeeorder.message.stored", "Employee order saved successfully"));
        if (saveAndNew != null) {
            form.setId(null);
            redirectAttributes.addFlashAttribute("prefillForm", form);
            return "redirect:/orders/employeeorders/create";
        }
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
            @RequestParam(required = false, defaultValue = "-1") Long employeeContractId,
            @RequestParam(required = false, defaultValue = "-1") Long orderId,
            @RequestParam(required = false, defaultValue = "-1") Long suborderId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Boolean show,
            RedirectAttributes redirectAttributes) {

        Long filterEmployeeContractId = employeeContractId == -1 ? null : employeeContractId;
        Long filterOrderId = orderId == -1 ? null : orderId;
        Long filterSuborderId = suborderId == -1 ? null : suborderId;
        var employeeOrders = employeeorderService.getEmployeeordersByFilters(show, filter, filterEmployeeContractId, filterOrderId, filterSuborderId, null);
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

    private void addFormModel(Model model, EmployeeorderForm form, boolean isEdit) {
        addFormModel(model, form, isEdit, false);
    }

    private void addFormModel(Model model, EmployeeorderForm form, boolean isEdit, boolean initialize) {
        model.addAttribute("employeeorderForm", form);
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("section", "orders");
        model.addAttribute("subSection", "employeeorders");
        model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
        String titleKey = isEdit ? "main.employeeorder.modify.text" : "main.employeeorder.new.text";
        model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Employee Order" : "Create Employee Order"));

        if(initialize) {
            if(form.getOrderId() != null && form.getCustomerId() == null) {
                var order = customerorderService.getCustomerorderById(form.getOrderId());
                if(order != null) {
                    form.setCustomerId(order.getCustomer().getId());
                }
            }
        }

        var customers = customerService.getCustomersOrderedByShortName();
        model.addAttribute("customers", customers);
        if (form.getCustomerId() == null && !customers.isEmpty()) {
            form.setCustomerId(customers.getFirst().getId());
        }

        var employeeContracts = new ArrayList<>(employeecontractService.getVisibleEmployeeContracts());
        // In edit mode, ensure the stored contract appears even if hidden
        if (isEdit && form.getEmployeeContractId() != null
                && employeeContracts.stream().noneMatch(ec -> Objects.equals(ec.getId(), form.getEmployeeContractId()))) {
            Employeecontract storedContract = employeecontractService.getEmployeecontractById(form.getEmployeeContractId());
            if (storedContract != null) {
                employeeContracts.add(storedContract);
            }
        }
        employeeContracts.sort(Comparator.comparing((Employeecontract ec) -> ec.getEmployee().getLastname())
            .thenComparing(Employeecontract::getValidFrom));
        model.addAttribute("employeecontracts", employeeContracts);
        if (form.getEmployeeContractId() == null && !employeeContracts.isEmpty()) {
            form.setEmployeeContractId(employeeContracts.getFirst().getId());
        }
        var validity = employeeContracts.stream()
            .filter(ec -> Objects.equals(ec.getId(), form.getEmployeeContractId()))
            .findFirst()
            .map(Employeecontract::getValidity)
            .orElse(null);

        var orders = new ArrayList<>(customerorderService.getVisibleCustomerorders().stream()
            .filter(co -> co.getCustomer().getId().equals(form.getCustomerId()))
            .filter(Customerorder::getCurrentlyValid)
            .filter(co -> co.getValidity().overlaps(validity))
            .toList());
        // In edit mode, ensure the stored order appears even if hidden or expired
        if (isEdit && form.getOrderId() != null
                && orders.stream().noneMatch(co -> Objects.equals(co.getId(), form.getOrderId()))) {
            Customerorder storedOrder = customerorderService.getCustomerorderById(form.getOrderId());
            if (storedOrder != null) {
                orders.add(storedOrder);
            }
        }
        orders.sort(Comparator.comparing(Customerorder::getSign));
        model.addAttribute("orders", orders);
        if (form.getOrderId() == null && !orders.isEmpty()) {
            form.setOrderId(orders.getFirst().getId());
        }

        List<Suborder> suborders = List.of();
        if(form.getOrderId() != null) {
            var filteredSuborders = new ArrayList<>(getVisibleSuborders(form.getOrderId(), true)
                .stream()
                .filter(so -> so.getValidity().overlaps(validity))
                .toList());
            // In edit mode, ensure the stored suborder appears even if hidden or expired.
            // Fall back to storedSuborderId when suborderId was reset by a CO change and the user switched back.
            Long suborderIdToEnsure = form.getSuborderId() != null ? form.getSuborderId() : form.getStoredSuborderId();
            if (isEdit && suborderIdToEnsure != null
                    && filteredSuborders.stream().noneMatch(so -> Objects.equals(so.getId(), suborderIdToEnsure))) {
                Suborder suborderToAdd = suborderService.getSuborderById(suborderIdToEnsure);
                if (suborderToAdd != null && Objects.equals(suborderToAdd.getCustomerorder().getId(), form.getOrderId())) {
                    filteredSuborders.add(suborderToAdd);
                }
            }
            filteredSuborders.sort(SubOrderComparator.INSTANCE);
            suborders = filteredSuborders;
            if (form.getSuborderId() == null && !suborders.isEmpty()) {
                form.setSuborderId(suborders.getFirst().getId());
            }
        }
        model.addAttribute("suborders", suborders);
    }

    private void addListModel(Model model) {
        model.addAttribute("customers", customerService.getCustomersOrderedByShortName());
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

    private void prefillDebitHours(EmployeeorderForm form) {
        if (form.getSuborderId() != null) {
            Suborder suborder = suborderService.getSuborderById(form.getSuborderId());
            if (suborder != null && suborder.getDebithours() != null && !suborder.getDebithours().isZero()) {
                form.setDebithours(DurationUtils.format(suborder.getDebithours()));
                form.setDebithoursunit(suborder.getDebithoursunit());
            }
        }
    }

    private EmployeeorderForm toForm(Employeeorder eo) {
        var form = new EmployeeorderForm();
        form.setId(eo.getId());
        form.setEmployeeContractId(eo.getEmployeecontract().getId());
        form.setOrderId(eo.getSuborder().getCustomerorder().getId());
        form.setSuborderId(eo.getSuborder().getId());
        form.setStoredSuborderId(eo.getSuborder().getId());
        form.setValidFrom(format(eo.getFromDate()));
        form.setValidUntil(eo.getUntilDate() != null ? format(eo.getUntilDate()) : "");
        if (eo.getDebithours() != null && !eo.getDebithours().isZero()) {
            form.setDebithours(DurationUtils.format(eo.getDebithours()));
            form.setDebithoursunit(eo.getDebithoursunit());
        }
        return form;
    }

}
