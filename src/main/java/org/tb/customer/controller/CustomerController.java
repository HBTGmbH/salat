package org.tb.customer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.tb.customer.domain.CustomerDTO;
import org.tb.customer.service.CustomerService;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

  private final CustomerService customerService;

  @GetMapping
  public String list(@RequestParam(value = "filter", required = false) String filter,
                     Model model) {
    model.addAttribute("pageTitle", "Customers");
    model.addAttribute("filter", filter);
    model.addAttribute("customers", customerService.getAllCustomerDTOsByFilter(filter));
    return "customer/list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", "Create Customer");
    model.addAttribute("isEdit", false);
    model.addAttribute("customer", CustomerDTO.builder().build());
    return "customer/form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/edit")
  public String editForm(@RequestParam("id") Long id, Model model) {
    var dto = customerService.getCustomerById(id);
    model.addAttribute("pageTitle", "Edit Customer");
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
      bindingResult.rejectValue("shortName", "error.shortName", "Short name is required");
    }
    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name", "Name is required");
    }

    if (bindingResult.hasErrors()) {
      model.addAttribute("pageTitle", form.getId() == null ? "Create Customer" : "Edit Customer");
      model.addAttribute("isEdit", form.getId() != null);
      return "customer/form";
    }

    customerService.createOrUpdate(form);
    redirectAttributes.addFlashAttribute("toastSuccess", "Customer saved successfully");
    return "redirect:/customers";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/delete")
  public String delete(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
    customerService.deleteCustomerById(id);
    redirectAttributes.addFlashAttribute("toastSuccess", "Customer deleted successfully");
    return "redirect:/customers";
  }
}
