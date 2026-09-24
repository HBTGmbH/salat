package org.tb.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.Authorized;
import org.tb.auth.service.AuthService;
import org.tb.auth.service.AuthorizationRuleService;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.viewhelper.ErrorCodeViewHelper;

/**
 * Maintains the fine-grained authorization rules (#1074) — the rows that used to be edited by hand via SQL.
 *
 * <p>Management only, without a gradation: a rule of category {@code EMPLOYEE} with {@code LOGIN} lets somebody act
 * in another person's name, so whoever writes rules here can grant that to themselves. The check sits on this class
 * and again in the service.
 */
@Controller
@RequestMapping("/auth/rules")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class AuthorizationRuleController {

  private final AuthorizationRuleService authorizationRuleService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  @GetMapping
  public String list(Model model) {
    model.addAttribute("rules", authorizationRuleService.getAll());
    return "auth/rule-list";
  }

  @GetMapping("/create")
  public String createForm(Model model) {
    addFormModel(model, new AuthorizationRuleForm());
    return "auth/rule-form";
  }

  @GetMapping("/{id}/edit")
  public String editForm(@PathVariable long id, Model model) {
    addFormModel(model, AuthorizationRuleForm.of(authorizationRuleService.getById(id)));
    return "auth/rule-form";
  }

  @PostMapping("/store")
  @Authorized(requiresManager = true)
  public String store(@ModelAttribute("ruleForm") AuthorizationRuleForm form,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    var data = form.toData();
    try {
      var unknownObjects = form.isNew()
          ? authorizationRuleService.create(data)
          : authorizationRuleService.update(form.getId(), data);
      redirectAttributes.addFlashAttribute("toastSuccess", storedMessage(form, unknownObjects));
    } catch (ErrorCodeException ex) {
      model.addAttribute("formErrors", toMessages(ex));
      addFormModel(model, form);
      return "auth/rule-form";
    }
    return "redirect:/auth/rules";
  }

  /** Ends a rule as of today — the usual way out, because it keeps who was allowed what traceable. */
  @PostMapping("/{id}/end")
  @Authorized(requiresManager = true)
  public String end(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      authorizationRuleService.end(id);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.auth.rule.message.ended"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/auth/rules";
  }

  @PostMapping("/{id}/delete")
  @Authorized(requiresManager = true)
  public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      authorizationRuleService.delete(id);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage("main.auth.rule.message.deleted"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/auth/rules";
  }

  /**
   * Refills the object field when the category changes: which values are selectable, and what the hint at the field
   * says, are the answer of the module that owns the category.
   */
  @PostMapping("/objects")
  @Authorized(requiresManager = true)
  public String objects(@ModelAttribute("ruleForm") AuthorizationRuleForm form,
                        Model model, HttpServletRequest request) {
    form.setObjectIds(new ArrayList<>()); // the previous pick belongs to the category that was just replaced
    addFormModel(model, form);
    model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
    return "auth/rule-form";
  }

  private void addFormModel(Model model, AuthorizationRuleForm form) {
    model.addAttribute("ruleForm", form);
    model.addAttribute("isEdit", !form.isNew());
    model.addAttribute("categories", authorizationRuleService.getCategories(form.getCategory()));
    model.addAttribute("accessLevels", AccessLevel.values());
    model.addAttribute("granteeCandidates", withKept(
        authorizationRuleService.getGranteeCandidates(), form.getGranteeIds()));
    model.addAttribute("objectCandidates", objectCandidates(form));
    model.addAttribute("objectHintKey", authorizationRuleService.getObjectHintKey(form.getCategory()));
  }

  /**
   * The objects the category offers, plus whatever the rule already carries. Without the second part an edit would
   * silently drop a value the module no longer lists — an order that has expired, say — and write back what the
   * browser happened to preselect instead.
   */
  private List<AuthorizationObject> objectCandidates(AuthorizationRuleForm form) {
    var offered = authorizationRuleService.getObjects(form.getCategory());
    var candidates = new ArrayList<>(offered);
    Set<String> known = offered.stream().map(AuthorizationObject::id).collect(Collectors.toSet());
    form.getObjectIds().stream()
        .filter(objectId -> objectId != null && !objectId.isBlank() && !known.contains(objectId))
        .forEach(objectId -> candidates.add(new AuthorizationObject(objectId, objectId)));
    return candidates;
  }

  private List<String> withKept(List<String> candidates, List<String> current) {
    var all = new LinkedHashSet<>(candidates);
    current.stream().filter(value -> value != null && !value.isBlank()).forEach(all::add);
    return List.copyOf(all);
  }

  private String storedMessage(AuthorizationRuleForm form, List<String> unknownObjects) {
    var stored = messages.getMessage(form.isNew()
        ? "main.auth.rule.message.created"
        : "main.auth.rule.message.updated");
    if (unknownObjects.isEmpty()) {
      return stored;
    }
    // Saved on purpose: a rule may precede the thing it is about. Saying so is what keeps a typo from passing as one.
    return stored + " " + messages.getMessage("main.auth.rule.message.unknownobjects",
        new Object[]{String.join(", ", unknownObjects)});
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }

  private String firstMessageOf(ErrorCodeException ex) {
    return toMessages(ex).stream().findFirst().orElse(ex.getMessage());
  }

  /** The wildcard, offered explicitly so that "applies to every object" is a pick and not a guess. */
  @ModelAttribute("anyObject")
  public String anyObject() {
    return AuthService.ANY_MATCH;
  }

}
