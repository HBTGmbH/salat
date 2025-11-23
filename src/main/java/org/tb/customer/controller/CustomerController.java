package org.tb.customer.controller;

import static org.tb.common.GlobalConstants.CUSTOMERADDRESS_MAX_LENGTH;
import static org.tb.common.GlobalConstants.CUSTOMERNAME_MAX_LENGTH;
import static org.tb.common.GlobalConstants.CUSTOMERSHORTNAME_MAX_LENGTH;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.common.exception.ErrorCodeException;
import org.tb.customer.domain.CustomerDTO;
import org.tb.customer.service.CustomerService;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;
  private final MessageSourceAccessor messageSourceAccessor;
  private final ErrorCodeViewHelper errorCodeViewHelper;

  @GetMapping
  public String list(@RequestParam(value = "filter", required = false) String filter,
                     Model model) {
    model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.mainmenu.customers.text", "Customers"));
    model.addAttribute("filter", filter);
    model.addAttribute("customers", customerService.getAllCustomerDTOsByFilter(filter));
    return "customer/list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.addcustomer.text", "Create Customer"));
    model.addAttribute("isEdit", false);
    model.addAttribute("customer", CustomerDTO.builder().build());
    return "customer/form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/edit")
  public String editForm(@RequestParam("id") Long id, Model model) {
    var dto = customerService.getCustomerById(id);
    model.addAttribute("pageTitle", messageSourceAccessor.getMessage("main.general.editcustomer.text", "Edit Customer"));
    model.addAttribute("isEdit", true);
    model.addAttribute("customer", dto);
    return "customer/form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/store")
  public String store(@Valid @ModelAttribute("customer") CustomerDTO form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    if (form.getShortName() == null || form.getShortName().isBlank()) {
      bindingResult.rejectValue("shortName", "error.shortName",
          messageSourceAccessor.getMessage("form.customer.error.shortname.required", "Short name is required"));
    }
    if (form.getShortName() != null && form.getShortName().length() > CUSTOMERSHORTNAME_MAX_LENGTH) {
      bindingResult.rejectValue("shortName", "error.shortName",
          messageSourceAccessor.getMessage("form.customer.error.shortname.toolong", "Name is required"));
    }
    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name",
          messageSourceAccessor.getMessage("form.customer.error.name.required", "Name is required"));
    }
    if (form.getName() != null && form.getName().length() > CUSTOMERNAME_MAX_LENGTH) {
      bindingResult.rejectValue("name", "error.name",
          messageSourceAccessor.getMessage("form.customer.error.name.toolong", "Name is required"));
    }
    if (form.getAddress() == null || form.getAddress().isBlank()) {
      bindingResult.rejectValue("address", "error.address",
          messageSourceAccessor.getMessage("form.customer.error.address.required", "Name is required"));
    }
    if (form.getAddress() != null && form.getAddress().length() > CUSTOMERADDRESS_MAX_LENGTH) {
      bindingResult.rejectValue("address", "error.address",
          messageSourceAccessor.getMessage("form.customer.error.address.toolong", "Name is required"));
    }

    var errors = !bindingResult.getFieldErrors().isEmpty();

    if(!errors) {
      try {
        customerService.createOrUpdate(form);
      } catch(ErrorCodeException ex) {
        model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
        errors = true;
      }
    }

    if (errors) {
      String titleKey = form.getId() == null ? "main.general.addcustomer.text" : "main.general.editcustomer.text";
      String titleFallback = form.getId() == null ? "Create Customer" : "Edit Customer";
      model.addAttribute("pageTitle", messageSourceAccessor.getMessage(titleKey, titleFallback));
      model.addAttribute("isEdit", form.getId() != null);
      return "customer/form";
    }

    redirectAttributes.addFlashAttribute("toastSuccess",
        messageSourceAccessor.getMessage("form.customer.message.stored", "Customer saved successfully"));
    return "redirect:/customers";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/delete")
  public String delete(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
    customerService.deleteCustomerById(id);
    redirectAttributes.addFlashAttribute("toastSuccess",
        messageSourceAccessor.getMessage("form.customer.message.deleted", "Customer deleted successfully"));
    return "redirect:/customers";
  }
}
