package org.tb.order.controller;

import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
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
import org.tb.common.GlobalConstants;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.customer.service.CustomerService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.service.EmployeeService;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.CustomerorderDTO;
import org.tb.order.service.CustomerorderService;
import org.tb.order.viewhelper.CustomerOrderViewDecorator;

@Controller
@RequestMapping("/orders/customerorders")
@RequiredArgsConstructor
public class CustomerorderController {

  private final CustomerorderService customerorderService;
  private final CustomerService customerService;
  private final EmployeeService employeeService;
  private final MessageSourceAccessor messages;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final AuthorizedEmployee authorizedEmployee;

  @GetMapping
  public String list(
      @RequestParam(required = false) String filter,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Boolean show,
      @RequestParam(required = false) Boolean showActualHours,
      Model model) {
    var customerorders = customerorderService.getCustomerordersByFilters(show, filter, customerId);
    if (Boolean.TRUE.equals(showActualHours)) {
      List<CustomerOrderViewDecorator> decorators = new LinkedList<>();
      for (Customerorder co : customerorders) {
        decorators.add(new CustomerOrderViewDecorator(customerorderService, co));
      }
      model.addAttribute("customerorders", decorators);
    } else {
      model.addAttribute("customerorders", customerorders);
    }
    model.addAttribute("customers", customerService.getCustomersOrderedByShortName());
    model.addAttribute("filter", filter);
    model.addAttribute("customerId", customerId);
    model.addAttribute("show", show);
    model.addAttribute("showActualHours", Boolean.TRUE.equals(showActualHours));
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "customerorders");
    model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.customerorders.text", "Customer Orders"));
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    return "order/customerorders-list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(Model model) {
    var form = new CustomerorderForm();
    form.setValidFrom(format(today()));
    form.setOrderType(OrderType.STANDARD);
    form.setEmployeeId(authorizedEmployee.getEmployeeId());
    form.setRespContrEmployeeId(authorizedEmployee.getEmployeeId());
    form.setHide(false);
    addFormModel(model, form, false);
    return "order/customerorder-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/edit")
  public String editForm(@RequestParam Long id, Model model) {
    Customerorder co = customerorderService.getCustomerorderById(id);
    var form = toForm(co);
    addFormModel(model, form, true);
    // Ensure currently assigned employees are in the list even if their contracts expired
    var employees = employeeService.getEmployeesWithValidContracts();
    if (co.getRespEmpHbtContract() != null && !employees.contains(co.getRespEmpHbtContract())) {
      employees.add(co.getRespEmpHbtContract());
    }
    if (co.getResponsible_hbt() != null && !employees.contains(co.getResponsible_hbt())) {
      employees.add(co.getResponsible_hbt());
    }
    model.addAttribute("employees", employees);
    return "order/customerorder-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/store")
  public String store(@ModelAttribute("customerorderForm") CustomerorderForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    validateForm(form, bindingResult);

    boolean hasErrors = bindingResult.hasErrors();
    if (!hasErrors) {
      try {
        LocalDate fromDate = DateUtils.parseOrNull(form.getValidFrom());
        LocalDate untilDate = (form.getValidUntil() != null && !form.getValidUntil().trim().isEmpty())
            ? DateUtils.parseOrNull(form.getValidUntil()) : null;
        var dto = new CustomerorderDTO(
            form.getCustomerId(), fromDate, untilDate, form.getSign(),
            form.getDescription(), form.getShortdescription(), form.getOrderCustomer(),
            form.getResponsibleCustomerContractually(), form.getResponsibleCustomerTechnical(),
            form.getEmployeeId(), form.getRespContrEmployeeId(),
            form.getDebithours(), form.getDebithoursunit(), form.getHide(), form.getOrderType());
        if (form.getId() == null) {
          customerorderService.create(dto);
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
      return "order/customerorder-form";
    }

    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.customerorder.message.stored", "Customer order saved successfully"));
    return "redirect:/orders/customerorders";
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

    if (!DurationUtils.validateDuration(form.getDebithours())) {
      bindingResult.rejectValue("debithours", "error.debithours",
          messages.getMessage("form.customerorder.error.debithours.wrongformat", "Invalid debit hours format"));
    }
  }

  private void addFormModel(Model model, CustomerorderForm form, boolean isEdit) {
    model.addAttribute("customerorderForm", form);
    model.addAttribute("customers", customerService.getAllCustomers());
    model.addAttribute("employees", employeeService.getEmployeesWithValidContracts());
    model.addAttribute("orderTypes", OrderType.values());
    model.addAttribute("isEdit", isEdit);
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "customerorders");
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    String titleKey = isEdit ? "main.general.editcustomerorder.text" : "main.general.addcustomerorder.text";
    model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Customer Order" : "Create Customer Order"));
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
    if (co.getResponsible_hbt() != null) {
      form.setEmployeeId(co.getResponsible_hbt().getId());
    }
    if (co.getRespEmpHbtContract() != null) {
      form.setRespContrEmployeeId(co.getRespEmpHbtContract().getId());
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
