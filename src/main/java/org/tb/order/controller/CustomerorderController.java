package org.tb.order.controller;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;
import static org.tb.order.controller.OrderUiStateKeyContributor.CUSTOMER_ID;
import static org.tb.order.controller.OrderUiStateKeyContributor.CUSTOMER_ORDER_FILTER;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
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
import org.tb.common.GlobalConstants;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.web.UiState;
import org.tb.customer.service.CustomerService;
import org.tb.customer.domain.Customer;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employee;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.CustomerorderDTO;
import org.tb.order.service.CustomerorderService;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;

@Controller
@RequestMapping("/orders/customerorders")
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class CustomerorderController {

  private final CustomerorderService customerorderService;
  private final CustomerService customerService;
  private final EmployeeService employeeService;
  private final MessageSourceAccessor messages;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final AuthorizedEmployee authorizedEmployee;
  private final UiState uiState;

  @GetMapping
  public String list(
      @RequestParam(required = false) String coFilter,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Boolean coShowInvalid,
      @RequestParam(required = false) Boolean coShowActualHours,
      @RequestParam(required = false) Boolean coShowHidden,
      HttpServletRequest request,
      Model model) {
    var filterSet = (coFilter != null && !coFilter.isEmpty()) || customerId != null;
    var customerorders = filterSet ? customerorderService.getCustomerordersByFilters(coShowInvalid, coFilter, customerId, coShowHidden) : List.<Customerorder>of();
    if (Boolean.TRUE.equals(coShowActualHours)) {
      List<CustomerOrderViewDecorator> decorators = new LinkedList<>();
      for (Customerorder co : customerorders) {
        decorators.add(new CustomerOrderViewDecorator(customerorderService, co));
      }
      model.addAttribute("customerorders", decorators);
    } else {
      model.addAttribute("customerorders", customerorders);
    }
    model.addAttribute("customers", customerService.getCustomersOrderedByShortName());
    model.addAttribute("coFilter", coFilter);
    model.addAttribute("customerId", customerId);
    model.addAttribute("coShowInvalid", coShowInvalid);
    model.addAttribute("coShowHidden", coShowHidden);
    model.addAttribute("coShowActualHours", Boolean.TRUE.equals(coShowActualHours));
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "customerorders");
    model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.customerorders.text", "Customer Orders"));
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    boolean htmxRequest = "true".equals(request.getHeader("HX-Request"));
    model.addAttribute("htmxRequest", htmxRequest);
    return htmxRequest ? "order/customer-order-list :: results" : "order/customer-order-list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(@RequestParam(required = false) Long customerId, Model model) {
    var form = new CustomerorderForm();
    form.setValidFrom(format(today()));
    form.setOrderType(OrderType.STANDARD);
    form.setResponsibleHbtIds(new java.util.ArrayList<>(List.of(authorizedEmployee.getEmployeeId())));
    form.setRespContrEmployeeId(authorizedEmployee.getEmployeeId());
    form.setCustomerId(customerId);
    form.setHide(false);
    addFormModel(model, form, false);
    return "order/customer-order-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/edit")
  public String editForm(@RequestParam Long id, Model model) {
    Customerorder co = customerorderService.getCustomerorderById(id);
    var form = toForm(co);
    addFormModel(model, form, true);
    return "order/customer-order-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/store")
  public String store(@ModelAttribute("customerorderForm") CustomerorderForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    validateForm(form, bindingResult);

    boolean hasErrors = bindingResult.hasErrors();
    Long newId = null;
    if (!hasErrors) {
      try {
        LocalDate fromDate = DateUtils.parseOrNull(form.getValidFrom());
        LocalDate untilDate = (form.getValidUntil() != null && !form.getValidUntil().trim().isEmpty())
            ? DateUtils.parseOrNull(form.getValidUntil()) : null;
        var dto = new CustomerorderDTO(
            form.getCustomerId(), fromDate, untilDate, form.getSign(),
            form.getDescription(), form.getShortdescription(), form.getOrderCustomer(),
            form.getResponsibleCustomerContractually(), form.getResponsibleCustomerTechnical(),
            form.getResponsibleHbtIds(), form.getRespContrEmployeeId(),
            form.getDebithours(), form.getDebithoursunit(), form.getHide(), form.getOrderType());
        if (form.getId() == null) {
          newId = customerorderService.create(dto).getId();
        } else {
          customerorderService.update(form.getId(), dto);
        }
      } catch (ErrorCodeException ex) {
        model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
        hasErrors = true;
      }
    }

    if (hasErrors) {
      addFormModel(model, form, form.getId() != null);
      return "order/customer-order-form";
    }

    if (newId != null) {
      uiState.clearState(CUSTOMER_ORDER_FILTER);
      if(!Objects.equals(form.getCustomerId(), uiState.getLongValue(CUSTOMER_ID))) {
        uiState.clearState(CUSTOMER_ID);
      }
    }
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.customerorder.message.stored", "Customer order saved successfully"));
    if (newId != null) {
      redirectAttributes.addFlashAttribute("toastAction", "/orders/suborders/create?customerOrderId=" + newId);
      redirectAttributes.addFlashAttribute("toastActionLabel",
          messages.getMessage("main.general.button.add.suborder.text", "Add Suborder"));
    }
    return "redirect:/orders/customerorders";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/toggle-hide")
  public String toggleHide(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
    try {
      var co = customerorderService.toggleHide(id);
      model.addAttribute("co", co);
      return "fragments/hide-toggle :: customerorderHideFlag";
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError",
          errorCodeViewHelper.toViewMessages(ex).stream().map(Object::toString).findFirst().orElse("Error"));
      return "redirect:/orders/customerorders";
    }
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      customerorderService.deleteCustomerorderById(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("form.customerorder.message.deleted", "Customer order deleted successfully"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError",
          errorCodeViewHelper.toViewMessages(ex).stream()
              .map(Object::toString).findFirst().orElse("Error deleting customer order"));
    }
    return "redirect:/orders/customerorders";
  }

  private void validateForm(CustomerorderForm form, BindingResult bindingResult) {
    if (form.getSign() == null || form.getSign().isEmpty()) {
      bindingResult.rejectValue("sign", "error.sign",
          messages.getMessage("form.customerorder.error.sign.required", "Sign is required"));
    } else if (form.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
      bindingResult.rejectValue("sign", "error.sign",
          messages.getMessage("form.customerorder.error.sign.toolong", "Sign is too long"));
    } else if (form.getId() == null) {
      // Check sign uniqueness only on create
      boolean signExists = customerorderService.getAllCustomerorders().stream()
          .anyMatch(co -> co.getSign().equalsIgnoreCase(form.getSign()));
      if (signExists) {
        bindingResult.rejectValue("sign", "error.sign",
            messages.getMessage("form.customerorder.error.sign.alreadyexists", "Sign already exists"));
      }
    }

    if (form.getDescription() == null || form.getDescription().trim().isEmpty()) {
      bindingResult.rejectValue("description", "error.description",
          messages.getMessage("form.error.description.necessary", "Description is required"));
    } else if (form.getDescription().length() > GlobalConstants.CUSTOMERORDER_DESCRIPTION_MAX_LENGTH) {
      bindingResult.rejectValue("description", "error.description",
          messages.getMessage("form.customerorder.error.description.toolong", "Description is too long"));
    }

    if (form.getShortdescription() != null
        && form.getShortdescription().length() > GlobalConstants.CUSTOMERORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
      bindingResult.rejectValue("shortdescription", "error.shortdescription",
          messages.getMessage("form.customerorder.error.shortdescription.toolong", "Short description is too long"));
    }

    if (form.getOrderCustomer() == null || form.getOrderCustomer().isEmpty()) {
      form.setOrderCustomer("-");
    } else if (form.getOrderCustomer().length() > GlobalConstants.CUSTOMERORDER_ORDER_CUSTOMER_MAX_LENGTH) {
      bindingResult.rejectValue("orderCustomer", "error.orderCustomer",
          messages.getMessage("form.customerorder.error.ordercustomer.toolong", "Order customer is too long"));
    }

    if (form.getResponsibleCustomerContractually() == null || form.getResponsibleCustomerContractually().isEmpty()) {
      bindingResult.rejectValue("responsibleCustomerContractually", "error.responsibleCustomerContractually",
          messages.getMessage("form.customerorder.error.responsiblecustomer.required", "Responsible customer contractually is required"));
    } else if (form.getResponsibleCustomerContractually().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
      bindingResult.rejectValue("responsibleCustomerContractually", "error.responsibleCustomerContractually",
          messages.getMessage("form.customerorder.error.responsiblecustomer.toolong", "Responsible customer contractually is too long"));
    }

    if (form.getResponsibleCustomerTechnical() == null || form.getResponsibleCustomerTechnical().isEmpty()) {
      bindingResult.rejectValue("responsibleCustomerTechnical", "error.responsibleCustomerTechnical",
          messages.getMessage("form.customerorder.error.responsiblecustomer.required", "Responsible customer technical is required"));
    } else if (form.getResponsibleCustomerTechnical().length() > GlobalConstants.CUSTOMERORDER_RESP_CUSTOMER_MAX_LENGTH) {
      bindingResult.rejectValue("responsibleCustomerTechnical", "error.responsibleCustomerTechnical",
          messages.getMessage("form.customerorder.error.responsiblecustomer.toolong", "Responsible customer technical is too long"));
    }

    if (!DateUtils.validateDate(form.getValidFrom())) {
      bindingResult.rejectValue("validFrom", "error.validFrom",
          messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
    }

    if (form.getValidUntil() != null && !form.getValidUntil().trim().isEmpty()
        && !DateUtils.validateDate(form.getValidUntil())) {
      bindingResult.rejectValue("validUntil", "error.validUntil",
          messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
    }

    if (form.getResponsibleHbtIds() == null || form.getResponsibleHbtIds().isEmpty()) {
      bindingResult.rejectValue("responsibleHbtIds", "error.responsibleHbtIds",
          messages.getMessage("form.customerorder.error.responsiblehbt.required", "Responsible HBT employee is required"));
    }

    if (form.getRespContrEmployeeId() == null) {
      bindingResult.rejectValue("respContrEmployeeId", "error.respContrEmployeeId",
          messages.getMessage("form.customerorder.error.responsiblehbt.contract.required", "Contract responsible employee is required"));
    }

    if (!DurationUtils.validateDuration(form.getDebithours())) {
      bindingResult.rejectValue("debithours", "error.debithours",
          messages.getMessage("form.customerorder.error.debithours.wrongformat", "Invalid debit hours format"));
    }
  }

  private void addFormModel(Model model, CustomerorderForm form, boolean isEdit) {
    model.addAttribute("customerorderForm", form);

    var customers = new ArrayList<>(customerService.getAllCustomers());
    // In edit mode, ensure the stored customer appears even if hidden
    if (isEdit && form.getCustomerId() != null
        && customers.stream().noneMatch(c -> Objects.equals(c.getId(), form.getCustomerId()))) {
      Customer storedCustomer = customerService.getCustomerEntityById(form.getCustomerId());
      if (storedCustomer != null) {
        customers.add(storedCustomer);
      }
    }
    customers.sort(Comparator.comparing(c -> c.getShortname().toLowerCase()));
    model.addAttribute("customers", customers);

    var employees = employeeService.getEmployeesWithValidContracts();
    // In edit mode, ensure assigned employees appear even if hidden or their contracts have expired.
    // Fall back to stored IDs when the form fields are null (e.g. after validation failure with empty select).
    if (isEdit) {
      var effectiveHbtIds = (form.getResponsibleHbtIds() != null && !form.getResponsibleHbtIds().isEmpty())
          ? form.getResponsibleHbtIds() : form.getStoredResponsibleHbtIds();
      effectiveHbtIds.forEach(id -> addEmployeeIfAbsent(employees, id));
      addEmployeeIfAbsent(employees, form.getRespContrEmployeeId() != null ? form.getRespContrEmployeeId() : form.getStoredRespContrEmployeeId());
      employees.sort(Comparator.comparing(Employee::getName));
    }
    model.addAttribute("employees", employees);

    model.addAttribute("orderTypes", OrderType.values());
    model.addAttribute("isEdit", isEdit);
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "customerorders");
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    String titleKey = isEdit ? "main.general.editcustomerorder.text" : "main.general.addcustomerorder.text";
    model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Customer Order" : "Create Customer Order"));
  }

  private void addEmployeeIfAbsent(List<Employee> employees, Long employeeId) {
    if (employeeId != null && employees.stream().noneMatch(e -> Objects.equals(e.getId(), employeeId))) {
      Employee emp = employeeService.getEmployeeById(employeeId);
      if (emp != null) {
        employees.add(emp);
      }
    }
  }

  private CustomerorderForm toForm(Customerorder co) {
    var form = new CustomerorderForm();
    form.setId(co.getId());
    form.setCustomerId(co.getCustomer().getId());
    form.setSign(co.getSign());
    form.setDescription(co.getDescription());
    form.setShortdescription(co.getShortdescription());
    form.setOrderCustomer(co.getOrder_customer());
    form.setResponsibleCustomerContractually(co.getResponsible_customer_contractually());
    form.setResponsibleCustomerTechnical(co.getResponsible_customer_technical());
    var responsibleHbtIds = co.getResponsibleHbt().stream().map(Employee::getId).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    form.setResponsibleHbtIds(responsibleHbtIds);
    form.setStoredResponsibleHbtIds(new java.util.ArrayList<>(responsibleHbtIds));
    if (co.getRespEmpHbtContract() != null) {
      form.setRespContrEmployeeId(co.getRespEmpHbtContract().getId());
      form.setStoredRespContrEmployeeId(co.getRespEmpHbtContract().getId());
    }
    form.setValidFrom(format(co.getFromDate()));
    form.setValidUntil(co.getUntilDate() != null ? format(co.getUntilDate()) : "");
    if (co.getDebithours() != null && !co.getDebithours().isZero()) {
      form.setDebithours(DurationUtils.format(co.getDebithours()));
      form.setDebithoursunit(co.getDebithoursunit());
    }
    form.setHide(co.getHide());
    form.setOrderType(co.getOrderType() != null ? co.getOrderType() : OrderType.STANDARD);
    return form;
  }
}
