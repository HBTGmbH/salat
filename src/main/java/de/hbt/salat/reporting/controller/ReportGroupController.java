package de.hbt.salat.reporting.controller;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.reporting.service.ReportGroupService;

/**
 * Maintaining the report groups (#1523), for managers: create, rename, delete. A personal group is
 * listed with the name of its person and is neither renamed nor, while it holds reports, deleted.
 */
@Controller
@RequestMapping("/reporting/report-groups")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class ReportGroupController {

  private static final String LIST = "redirect:/reporting/report-groups";

  private final ReportGroupService reportGroupService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String list(Model model) {
    addPageModel(model, messages.getMessage("main.reporting.group.manage.title"));
    model.addAttribute("groups", reportGroupService.getManagedGroups());
    return "reporting/report-groups";
  }

  @GetMapping("/create")
  public String createForm(Model model) {
    addPageModel(model, messages.getMessage("main.reporting.group.create.title"));
    model.addAttribute("groupForm", new GroupForm());
    return "reporting/report-group-form";
  }

  @GetMapping("/edit")
  public String editForm(@RequestParam("id") long id, Model model) {
    var managed = reportGroupService.getManagedGroup(id);
    var form = new GroupForm();
    form.setId(id);
    form.setName(managed.group().name());
    addPageModel(model, messages.getMessage("main.reporting.group.edit.title"));
    model.addAttribute("groupForm", form);
    return "reporting/report-group-form";
  }

  @PostMapping("/store")
  public String store(@ModelAttribute("groupForm") GroupForm form, Model model, RedirectAttributes redirectAttributes) {
    try {
      if (form.getId() == null) {
        reportGroupService.create(form.getName());
      } else {
        reportGroupService.rename(form.getId(), form.getName());
      }
    } catch (ErrorCodeException e) {
      addPageModel(model, messages.getMessage(form.getId() == null
          ? "main.reporting.group.create.title" : "main.reporting.group.edit.title"));
      model.addAttribute("errors", errorCodeViewHelper.toViewMessages(e));
      return "reporting/report-group-form";
    }
    redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.reporting.group.saved"));
    return LIST;
  }

  @PostMapping("/delete")
  public String delete(@RequestParam("id") long id, RedirectAttributes redirectAttributes) {
    try {
      reportGroupService.delete(id);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.reporting.group.deleted"));
    } catch (ErrorCodeException e) {
      redirectAttributes.addFlashAttribute("toastError", errorCodeViewHelper.toViewMessages(e).stream()
          .map(Object::toString).findFirst().orElse("Error"));
    }
    return LIST;
  }

  private static void addPageModel(Model model, String title) {
    model.addAttribute("pageTitle", title);
    model.addAttribute("section", "reports");
    model.addAttribute("subSection", "reports");
    model.addAttribute("sectionTitle", "Reports");
  }

  @Getter
  @Setter
  public static class GroupForm {
    private Long id;
    private String name;
  }
}
