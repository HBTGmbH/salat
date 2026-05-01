package org.tb.order.controller;

import static org.tb.common.GlobalConstants.YESNO_NO;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.util.DateUtils.format;
import static org.tb.common.util.DateUtils.today;

import java.text.DecimalFormat;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.GlobalConstants;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.customer.service.CustomerService;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;
import org.tb.order.service.CustomerorderService;
import org.tb.order.domain.SuborderDTO;
import org.tb.order.service.SuborderService;
import org.tb.order.viewhelper.SuborderViewDecorator;

@Controller
@RequestMapping("/orders/suborders")
@RequiredArgsConstructor
public class SuborderController {

  private final SuborderService suborderService;
  private final CustomerorderService customerorderService;
  private final CustomerService customerService;
  private final MessageSourceAccessor messages;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final AuthorizedEmployee authorizedEmployee;
  private final AuthorizedUser authorizedUser;

  @GetMapping
  public String list(
      @RequestParam(required = false) String filter,
      @RequestParam(required = false, defaultValue = "-1") Long customerOrderId,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Boolean show,
      @RequestParam(required = false) Boolean showActualHours,
      Model model) {
    Long filterCustomerOrderId = customerOrderId != null && customerOrderId == -1 ? null : customerOrderId;
    var suborders = suborderService.getSubordersByFilters(show, filter, filterCustomerOrderId, customerId);
    if (Boolean.TRUE.equals(showActualHours)) {
      List<SuborderViewDecorator> decorators = new LinkedList<>();
      for (Suborder so : suborders) {
        decorators.add(new SuborderViewDecorator(suborderService, so));
      }
      model.addAttribute("suborders", decorators);
    } else {
      model.addAttribute("suborders", suborders);
    }
    var visibleCustomerOrders = customerorderService.getVisibleCustomerorders();
    if (customerId != null && customerId > 0) {
      visibleCustomerOrders = visibleCustomerOrders.stream()
          .filter(co -> co.getCustomer().getId().equals(customerId))
          .toList();
    }
    model.addAttribute("customers", customerService.getCustomersOrderedByShortName());
    model.addAttribute("visibleCustomerOrders", visibleCustomerOrders);
    model.addAttribute("filter", filter);
    model.addAttribute("customerId", customerId);
    model.addAttribute("customerOrderId", customerOrderId);
    model.addAttribute("show", show);
    model.addAttribute("showActualHours", Boolean.TRUE.equals(showActualHours));
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "suborders");
    model.addAttribute("pageTitle", messages.getMessage("main.general.mainmenu.suborders.text", "Suborders"));
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    return "order/suborders-list";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/create")
  public String createForm(
      @RequestParam(required = false) Long customerOrderId,
      Model model) {
    var form = new SuborderForm();
    form.setInvoice(true);
    form.setStandard(false);
    form.setCommentnecessary(false);
    form.setFixedPrice(false);
    form.setTrainingFlag(false);
    form.setHide(false);
    if (customerOrderId != null) {
      Customerorder co = customerorderService.getCustomerorderById(customerOrderId);
      form.setCustomerorderId(customerOrderId);
      form.setParentId(customerOrderId);
      form.setValidFrom(format(co.getFromDate()));
      form.setValidUntil(co.getUntilDate() != null ? format(co.getUntilDate()) : "");
    } else {
      form.setValidFrom(format(today()));
    }
    addFormModel(model, form, false);
    return "order/suborder-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/{id}/edit")
  public String editForm(@PathVariable Long id, Model model) {
    Suborder so = suborderService.getSuborderById(id);
    var form = toForm(so);
    addFormModel(model, form, true);
    return "order/suborder-form";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/store")
  public String store(@ModelAttribute("suborderForm") SuborderForm form,
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    validateForm(form, bindingResult);

    boolean hasErrors = bindingResult.hasErrors();
    if (!hasErrors) {
      try {
        char invoice = form.getInvoice() != null && form.getInvoice() ? YESNO_YES : YESNO_NO;
        SuborderDTO data = new SuborderDTO(
            form.getCustomerorderId(),
            form.getSign(),
            form.getDescription(),
            form.getShortdescription(),
            form.getSuborder_customer(),
            invoice,
            form.getStandard(),
            form.getCommentnecessary(),
            form.getFixedPrice(),
            form.getTrainingFlag(),
            form.getOrderType(),
            form.getValidFrom(),
            form.getValidUntil(),
            form.getDebithours(),
            form.getDebithoursunit(),
            form.getHide(),
            form.getParentId()
        );
        if (form.getId() == null) {
          suborderService.create(data, form.getCustomerorderId());
        } else {
          suborderService.update(form.getId(), data, form.getCustomerorderId());
        }
      } catch (ErrorCodeException ex) {
        model.addAttribute("errors", errorCodeViewHelper.toViewMessages(ex));
        hasErrors = true;
      }
    }

    if (hasErrors) {
      addFormModel(model, form, form.getId() != null);
      return "order/suborder-form";
    }

    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.suborder.message.stored", "Suborder saved successfully"));
    return "redirect:/orders/suborders";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/delete")
  public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    try {
      suborderService.deleteSuborderById(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("form.suborder.message.deleted", "Suborder deleted successfully"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError",
          errorCodeViewHelper.toViewMessages(ex).stream()
              .map(Object::toString).findFirst().orElse("Error deleting suborder"));
    }
    return "redirect:/orders/suborders";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/copy")
  public String copy(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    suborderService.createCopy(id);
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.suborder.message.copied", "Suborder copied successfully"));
    return "redirect:/orders/suborders";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/{id}/fit-dates")
  public String fitDates(@PathVariable Long id, RedirectAttributes redirectAttributes) {
    suborderService.fitValidityOfChildren(id);
    redirectAttributes.addFlashAttribute("toastSuccess",
        messages.getMessage("form.suborder.message.fitdates", "Child dates adjusted successfully"));
    return "redirect:/orders/suborders/" + id + "/edit";
  }

  @PreAuthorize("hasRole('MANAGER')")
  @GetMapping("/sign")
  @ResponseBody
  public String generateSign(
      @RequestParam Long customerorderId,
      @RequestParam Long parentId,
      @RequestParam(required = false) Long currentId) {
    List<Suborder> siblings;
    if (Objects.equals(parentId, customerorderId)) {
      siblings = suborderService.getSubordersByCustomerorderId(customerorderId).stream()
          .filter(so -> so.getParentorder() == null)
          .toList();
    } else {
      siblings = suborderService.getSuborderChildren(parentId);
    }
    int version = 1;
    DecimalFormat df = new DecimalFormat("00");
    for (Suborder sibling : siblings) {
      if (sibling.getCompleteOrderSign().endsWith("/" + df.format(version))
          && !Objects.equals(sibling.getId(), currentId)) {
        version++;
      }
    }
    return df.format(version);
  }

  @PreAuthorize("hasRole('MANAGER')")
  @PostMapping("/change-customerorder")
  public String changeCustomerorder(@ModelAttribute("suborderForm") SuborderForm form, Model model) {
    Customerorder co = customerorderService.getCustomerorderById(form.getCustomerorderId());
    form.setParentId(form.getCustomerorderId());
    form.setValidFrom(format(co.getFromDate()));
    form.setValidUntil(co.getUntilDate() != null ? format(co.getUntilDate()) : "");
    addFormModel(model, form, form.getId() != null);
    return "order/suborder-form";
  }

  private void validateForm(SuborderForm form, BindingResult bindingResult) {
    // Sign
    if (form.getSign() == null || form.getSign().isEmpty()) {
      bindingResult.rejectValue("sign", "error.sign",
          messages.getMessage("form.suborder.error.sign.required", "Sign is required"));
    } else if (form.getSign().length() > GlobalConstants.CUSTOMERORDER_SIGN_MAX_LENGTH) {
      bindingResult.rejectValue("sign", "error.sign",
          messages.getMessage("form.suborder.error.sign.toolong", "Sign is too long"));
    }

    // Description
    if (form.getDescription() == null || form.getDescription().trim().isEmpty()) {
      bindingResult.rejectValue("description", "error.description",
          messages.getMessage("form.error.description.necessary", "Description is required"));
    } else if (form.getDescription().length() > GlobalConstants.SUBORDER_DESCRIPTION_MAX_LENGTH) {
      bindingResult.rejectValue("description", "error.description",
          messages.getMessage("form.suborder.error.description.toolong", "Description is too long"));
    }

    if (form.getShortdescription() != null
        && form.getShortdescription().length() > GlobalConstants.SUBORDER_SHORT_DESCRIPTION_MAX_LENGTH) {
      bindingResult.rejectValue("shortdescription", "error.shortdescription",
          messages.getMessage("form.suborder.error.shortdescription.toolong", "Short description is too long"));
    }

    if (form.getSuborder_customer() != null
        && form.getSuborder_customer().length() > GlobalConstants.SUBORDER_SUBORDER_CUSTOMER_MAX_LENGTH) {
      bindingResult.rejectValue("suborder_customer", "error.suborder_customer",
          messages.getMessage("form.suborder.error.suborder_customer.toolong", "Suborder customer is too long"));
    }

    // Dates
    LocalDate suborderFromDate = null;
    if (DateUtils.validateDate(form.getValidFrom())) {
      suborderFromDate = DateUtils.parse(form.getValidFrom());
    } else {
      bindingResult.rejectValue("validFrom", "error.validFrom",
          messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
    }

    LocalDate suborderUntilDate = null;
    if (form.getValidUntil() != null && !form.getValidUntil().trim().isEmpty()) {
      if (DateUtils.validateDate(form.getValidUntil())) {
        suborderUntilDate = DateUtils.parse(form.getValidUntil());
      } else {
        bindingResult.rejectValue("validUntil", "error.validUntil",
            messages.getMessage("form.timereport.error.date.wrongformat", "Invalid date format"));
      }
    }

    if (suborderFromDate != null && suborderUntilDate != null
        && suborderUntilDate.isBefore(suborderFromDate)) {
      bindingResult.rejectValue("validUntil", "error.validUntil",
          messages.getMessage("form.suborder.error.date.untilbeforefrom", "Until date must not be before from date"));
    }

    // Debit hours
    if (!DurationUtils.validateDuration(form.getDebithours())) {
      bindingResult.rejectValue("debithours", "error.debithours",
          messages.getMessage("form.customerorder.error.debithours.wrongformat", "Invalid debit hours format"));
    }

    // Customer order date range check
    if (form.getCustomerorderId() != null) {
      Customerorder customerorder = customerorderService.getCustomerorderById(form.getCustomerorderId());
      if (customerorder == null) {
        bindingResult.rejectValue("customerorderId", "error.customerorderId",
            messages.getMessage("form.suborder.error.customerorder.notfound", "Customer order not found"));
      } else if (suborderFromDate != null) {
        LocalDate coFromDate = customerorder.getFromDate();
        LocalDate coUntilDate = customerorder.getUntilDate();
        if (coFromDate != null && suborderFromDate.isBefore(coFromDate)) {
          bindingResult.rejectValue("validFrom", "error.validFrom",
              messages.getMessage("form.suborder.error.date.outofrange.order", "From date is out of range of the customer order"));
        }
        if (!(coUntilDate == null || suborderUntilDate != null && !suborderUntilDate.isAfter(coUntilDate))) {
          bindingResult.rejectValue("validUntil", "error.validUntil",
              messages.getMessage("form.suborder.error.date.outofrange.order", "Until date is out of range of the customer order"));
        }
      }
    }

    // Parent suborder date range check
    if (form.getParentId() != null && !Objects.equals(form.getParentId(), form.getCustomerorderId())) {
      Suborder parentSuborder = suborderService.getSuborderById(form.getParentId());
      if (parentSuborder != null
          && Objects.equals(parentSuborder.getCustomerorder().getId(), form.getCustomerorderId())
          && suborderFromDate != null) {
        LocalDate parentFromDate = parentSuborder.getFromDate();
        LocalDate parentUntilDate = parentSuborder.getUntilDate();
        if (parentFromDate != null && suborderFromDate.isBefore(parentFromDate)) {
          bindingResult.rejectValue("validFrom", "error.validFrom",
              messages.getMessage("form.suborder.error.date.outofrange.suborder", "From date is out of range of the parent suborder"));
        }
        if (!(parentUntilDate == null || suborderUntilDate != null && !suborderUntilDate.isAfter(parentUntilDate))) {
          bindingResult.rejectValue("validUntil", "error.validUntil",
              messages.getMessage("form.suborder.error.date.outofrange.suborder", "Until date is out of range of the parent suborder"));
        }
      }
    }

    // Sign uniqueness among siblings
    if (form.getSign() != null && !form.getSign().isBlank() && suborderFromDate != null) {
      var validityToCheck = new LocalDateRange(suborderFromDate, suborderUntilDate);
      Long currentId = form.getId();
      String signToCheck = form.getSign();
      List<Suborder> siblings;
      if (form.getParentId() == null || Objects.equals(form.getParentId(), form.getCustomerorderId())) {
        siblings = suborderService.getSubordersByCustomerorderId(form.getCustomerorderId()).stream()
            .filter(s -> s.getParentorder() == null)
            .filter(s -> !s.getId().equals(currentId))
            .toList();
      } else {
        siblings = suborderService.getSuborderChildren(form.getParentId()).stream()
            .filter(s -> s.getParentorder() != null && s.getParentorder().getId().equals(form.getParentId()))
            .filter(s -> !s.getId().equals(currentId))
            .toList();
      }
      for (Suborder sibling : siblings) {
        if (sibling.getValidity().overlaps(validityToCheck)
            && sibling.getSign().equalsIgnoreCase(signToCheck)) {
          bindingResult.rejectValue("sign", "error.sign",
              messages.getMessage("form.suborder.error.sign.alreadyexists", "Sign already exists for this period"));
          break;
        }
      }
    }
  }

  private void addFormModel(Model model, SuborderForm form, boolean isEdit) {
    model.addAttribute("suborderForm", form);
    model.addAttribute("orderTypes", OrderType.values());
    // Customer orders for the dropdown (all visible if manager, else only responsible ones)
    List<Customerorder> customerorders;
    if (authorizedUser.isManager()) {
      customerorders = customerorderService.getVisibleCustomerorders();
    } else {
      customerorders = customerorderService.getVisibleCustomerOrdersByResponsibleEmployeeId(
          authorizedEmployee.getEmployeeId());
    }
    model.addAttribute("customerorders", customerorders);
    // Suborders of the current customer order for parent dropdown
    // If no customer order is selected yet, pre-select the first available one
    if (form.getCustomerorderId() == null && !customerorders.isEmpty()) {
      Customerorder first = customerorders.getFirst();
      form.setCustomerorderId(first.getId());
      form.setParentId(first.getId());
    }
    if (form.getCustomerorderId() != null) {
      model.addAttribute("parentSuborders",
          suborderService.getSubordersByCustomerorderId(form.getCustomerorderId()));
      model.addAttribute("currentCustomerorder",
          customerorderService.getCustomerorderById(form.getCustomerorderId()));
    }
    model.addAttribute("isEdit", isEdit);
    model.addAttribute("section", "orders");
    model.addAttribute("subSection", "suborders");
    model.addAttribute("sectionTitle", messages.getMessage("main.general.mainmenu.orders.text", "Orders"));
    String titleKey = isEdit ? "main.general.editsuborder.text" : "main.general.addsuborder.text";
    model.addAttribute("pageTitle", messages.getMessage(titleKey, isEdit ? "Edit Suborder" : "Create Suborder"));
  }

  private SuborderForm toForm(Suborder so) {
    var form = new SuborderForm();
    form.setId(so.getId());
    form.setCustomerorderId(so.getCustomerorder().getId());
    form.setSign(so.getSign());
    form.setDescription(so.getDescription());
    form.setShortdescription(so.getShortdescription());
    form.setSuborder_customer(so.getSuborder_customer());
    form.setInvoice(YESNO_YES == so.getInvoice());
    form.setStandard(so.getStandard());
    form.setCommentnecessary(so.getCommentnecessary());
    form.setFixedPrice(so.getFixedPrice());
    form.setTrainingFlag(so.getTrainingFlag());
    form.setHide(so.isHide());
    form.setOrderType(so.getOrderType());
    form.setValidFrom(format(so.getFromDate()));
    form.setValidUntil(so.getUntilDate() != null ? format(so.getUntilDate()) : "");
    if (so.getDebithours() != null && !so.getDebithours().isZero()) {
      form.setDebithours(DurationUtils.format(so.getDebithours()));
      form.setDebithoursunit(so.getDebithoursunit());
    }
    Long parentId = so.getParentorder() != null ? so.getParentorder().getId() : so.getCustomerorder().getId();
    form.setParentId(parentId);
    return form;
  }
}
