package de.hbt.salat.jira.controller;


import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_NOT_SELECTED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE;
import static de.hbt.salat.common.exception.ErrorCode.SE_NO_KEY;
import static de.hbt.salat.common.exception.ErrorCode.SE_OAUTH_DENIED;
import static de.hbt.salat.common.exception.ErrorCode.SE_OAUTH_NOT_CONFIGURED;
import static de.hbt.salat.common.exception.ErrorCode.SE_OAUTH_STATE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.SE_OAUTH_TOKEN_REQUEST_FAILED;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraAuthMethod;
import de.hbt.salat.jira.domain.JiraFieldCatalog;
import de.hbt.salat.jira.domain.JiraReplicationConfigData;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;
import de.hbt.salat.jira.service.JiraReplicationConfigService;
import de.hbt.salat.jira.service.JiraReplicationLauncher;
import de.hbt.salat.jira.service.JiraReplicationOAuthService;
import de.hbt.salat.jira.service.JiraReplicationRunService;
import de.hbt.salat.jira.viewhelper.JiraReplicationRunViewHelper;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;
import de.hbt.salat.secret.service.OAuthService;

/**
 * Maintains the JIRA replications (#984) — the rows that used to be edited by hand via SQL.
 *
 * <p>Managers only. Since #982 the replicated tickets are offered to everyone who books, so a
 * misconfigured replication shows up as missing suggestions rather than only in the log; the
 * credentials behind it are still a matter for the management, not for the backoffice.
 */
@Controller
@RequestMapping("/jira/replications")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class JiraReplicationConfigController {

  /** How many runs the run tab shows — about a day of hourly runs over the usual number of configs. */
  static final int RUN_LIMIT = 200;

  /** The fragment that opens the run tab. */
  static final String RUNS_TAB = "#tab-runs";

  /** The outcome of a callback that connected (#1417). */
  private static final String OAUTH_CONNECTED = "connected";

  /** The codes a callback can end with, by their code, as the address carries them (#1417). */
  private static final Map<String, ErrorCode> OAUTH_FAILURES = Stream.of(
          SE_NO_KEY, SE_OAUTH_NOT_CONFIGURED, SE_OAUTH_STATE_INVALID, SE_OAUTH_DENIED, SE_OAUTH_TOKEN_REQUEST_FAILED,
          JI_REPLICATION_NOT_FOUND, JI_REPLICATION_OAUTH_NOT_SELECTED, JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE,
          XX_CONCURRENT_MODIFICATION)
      .collect(Collectors.toUnmodifiableMap(ErrorCode::getCode, Function.identity()));

  /** What an OAuth error code looks like ({@code invalid_grant}); nothing else is shown from the address. */
  private static final Pattern OAUTH_ERROR = Pattern.compile("[a-z_]{1,40}");

  private final JiraReplicationConfigService jiraReplicationConfigService;
  private final JiraReplicationLauncher jiraReplicationLauncher;
  private final JiraReplicationOAuthService jiraReplicationOAuthService;
  private final JiraReplicationRunService jiraReplicationRunService;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

  /**
   * The page has two tabs (#1282): the replications to maintain, and the runs they left. The tab
   * comes from the fragment of the address ({@code #tab-runs}), which Tabler opens on its own — the
   * request never sees it, so both lists are always loaded.
   */
  @GetMapping
  public String list(@RequestParam(required = false) Boolean fJiraRunFailedOnly,
                     @RequestParam(required = false) String oauth,
                     @RequestParam(required = false) String oauthDetail,
                     Model model) {
    addOAuthOutcome(model, oauth, oauthDetail, null);
    var replications = jiraReplicationConfigService.getAll();
    boolean failedOnly = Boolean.TRUE.equals(fJiraRunFailedOnly);

    model.addAttribute("replications", replications);
    model.addAttribute("runningReplicationIds", jiraReplicationRunService.getRunningReplicationIds());
    model.addAttribute("runs", jiraReplicationRunService.getLatestRuns(RUN_LIMIT, failedOnly).stream()
        .map(JiraReplicationRunViewHelper::from)
        .toList());
    model.addAttribute("fJiraRunFailedOnly", failedOnly);
    return "jira/replication-list";
  }

  @GetMapping("/create")
  public String createForm(Model model) {
    addFormModel(model, new JiraReplicationConfigForm());
    addCredentialsModel(model, true, null);
    return "jira/replication-form";
  }

  @GetMapping("/{id}/edit")
  public String editForm(@PathVariable long id,
                         @RequestParam(required = false) String oauth,
                         @RequestParam(required = false) String oauthDetail,
                         Model model) {
    var info = jiraReplicationConfigService.getById(id);
    addOAuthOutcome(model, oauth, oauthDetail, info.baseUrl());
    addFormModel(model, JiraReplicationConfigForm.of(info));
    addCredentialsModel(model, info.credentialsReadable(), info);
    model.addAttribute("lastMaxUpdated", info.lastMaxUpdated());
    return "jira/replication-form";
  }

  /**
   * The field catalogue of one replication, as the body of the picker dialogue (#1013).
   *
   * <p>A read, hence {@code GET}, and only ever for a stored config: the id is the whole input, so
   * nobody can point this at an address of their choosing. Deliberately not under {@code /api} or
   * {@code /rest} — those are stateless filter chains for machine clients and do not accept a
   * browser session.
   *
   * <p>Credentials that cannot be read (#1432) are said inside the dialogue, like an unreachable
   * JIRA.
   */
  @GetMapping("/{id}/fields")
  public String fields(@PathVariable long id, Model model) {
    JiraFieldCatalog catalog;
    try {
      catalog = jiraReplicationConfigService.getSelectableFields(id);
    } catch (ErrorCodeException ex) {
      catalog = JiraFieldCatalog.failed(firstMessageOf(ex));
    }
    model.addAttribute("fieldCatalog", catalog);
    return "jira/replication-fields :: fieldPicker";
  }

  @PostMapping("/store")
  @Authorized(requiresManager = true)
  public String store(@ModelAttribute("replicationForm") JiraReplicationConfigForm form,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    var data = new JiraReplicationConfigData(
        form.getName(),
        form.getCustomerorderId(),
        form.getSuborderId(),
        form.getBaseUrl(),
        form.getApiFlavor(),
        form.getAuthMethod(),
        form.getUsername(),
        form.getPassword(),
        form.getJql(),
        form.getParentFieldNames(),
        form.getAdditionalFieldNames(),
        form.getInheritedFieldNames(),
        form.getPageSize(),
        form.isEnabled(),
        form.isWorklogSyncEnabled(),
        form.getWorklogSyncFrom(),
        form.isWorklogSyncInvoiceableOnly()
    );

    try {
      if (form.isNew()) {
        jiraReplicationConfigService.create(data);
        redirectAttributes.addFlashAttribute("toastSuccess",
            messages.getMessage("main.jira.replication.message.created"));
      } else {
        jiraReplicationConfigService.update(form.getId(), data);
        redirectAttributes.addFlashAttribute("toastSuccess",
            messages.getMessage("main.jira.replication.message.updated"));
      }
    } catch (ErrorCodeException ex) {
      // A typed password is not carried back into the re-rendered form: the field starts empty
      // again, which the service reads as "keep the stored one" — never as "clear it".
      form.setPassword(null);
      model.addAttribute("formErrors", toMessages(ex));
      addFormModel(model, form);
      var stored = form.isNew() ? null : jiraReplicationConfigService.getById(form.getId());
      addCredentialsModel(model, stored == null || stored.credentialsReadable(), stored);
      return "jira/replication-form";
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/delete")
  @Authorized(requiresManager = true)
  public String delete(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.delete(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.deleted"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/enabled")
  @Authorized(requiresManager = true)
  public String setEnabled(@PathVariable long id, @RequestParam boolean enabled,
                           RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.setEnabled(id, enabled);
      redirectAttributes.addFlashAttribute("toastSuccess", messages.getMessage(
          enabled ? "main.jira.replication.message.enabled" : "main.jira.replication.message.disabled"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  @PostMapping("/{id}/reset-watermark")
  @Authorized(requiresManager = true)
  public String resetWatermark(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationConfigService.resetWatermark(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.watermarkreset"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications";
  }

  /**
   * Starts the replication and returns at once (#1282): the run goes on in the background, like a
   * manually started ETL run, and stands as "running" in the run tab the page lands on.
   */
  @PostMapping("/{id}/run")
  @Authorized(requiresManager = true)
  public String run(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      var name = jiraReplicationConfigService.getById(id).name();
      jiraReplicationLauncher.startManualRun(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.message.started", new Object[]{name}));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications" + RUNS_TAB;
  }

  /** Sets a run that only stands on "running" because of a crash to finished (#1282). */
  @PostMapping("/runs/{runId}/mark-finished")
  @Authorized(requiresManager = true)
  public String markRunFinished(@PathVariable long runId, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationRunService.markFinished(runId);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.run.message.markedfinished"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/jira/replications" + RUNS_TAB;
  }

  /**
   * Refills the suborder select when the customer order changes (#1025). Offering the suborders of
   * every order would be a list of several thousand entries, and the scope of a replication is
   * always a place below one order.
   */
  @PostMapping("/suborders")
  @Authorized(requiresManager = true)
  public String suborders(@ModelAttribute("replicationForm") JiraReplicationConfigForm form,
                          Model model, HttpServletRequest request) {
    form.setSuborderId(null); // the previous pick belongs to the order that was just replaced
    addFormModel(model, form);
    // The answer replaces the suborder select only; nothing of the credentials is rendered.
    addCredentialsModel(model, true, null);
    model.addAttribute("htmxRequest", "true".equals(request.getHeader("HX-Request")));
    model.addAttribute("subordersChanged", true);
    return "jira/replication-form";
  }

  /**
   * Starts connecting the replication to an Atlassian account (#1417): sets the cookie of the attempt
   * and sends the browser to Atlassian. A {@code POST}, because it starts something on behalf of the
   * replication, and with the CSRF token of the form.
   */
  @PostMapping("/{id}/oauth/connect")
  @Authorized(requiresManager = true)
  public String connectOAuth(@PathVariable long id, HttpServletResponse response,
                             RedirectAttributes redirectAttributes) {
    try {
      var authorization = jiraReplicationOAuthService.startConnection(id);
      response.addHeader(HttpHeaders.SET_COOKIE, authorization.cookie().toString());
      return "redirect:" + authorization.redirectUrl();
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
      return editRedirect(id);
    }
  }

  /**
   * Where Atlassian sends the browser back (#1417, ADR-0038 §8). One address per environment, as
   * registered at Atlassian, hence without the replication — that comes from the cookie, once it has
   * been checked. The cookie goes whatever comes of it, and the answer is a redirect right away, so
   * the {@code code} does not stay in the history and the referrer.
   *
   * <p>The outcome travels in the address of the redirect, not as a flash attribute: the navigation
   * comes from the site of Atlassian, and the session cookie is {@code SameSite=Strict}, so this
   * request has no session to put a flash attribute in — it would open a new one and lose the message.
   * {@link #addOAuthOutcome} turns the parameters back into the message.
   *
   * <p>Through EasyAuth like every page: it is a navigation of the person who started connecting.
   */
  @GetMapping("/oauth/callback")
  @Authorized(requiresManager = true)
  public String oauthCallback(@RequestParam(required = false) String code,
                              @RequestParam(required = false) String state,
                              @RequestParam(required = false) String error,
                              @CookieValue(name = OAuthService.STATE_COOKIE, required = false) String cookie,
                              HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, jiraReplicationOAuthService.clearedCookie().toString());
    long id;
    try {
      id = jiraReplicationOAuthService.replicationOf(cookie, state);
    } catch (ErrorCodeException ex) {
      return "redirect:" + withOAuthOutcome("/jira/replications", ex);
    }
    try {
      jiraReplicationOAuthService.completeConnection(id, cookie, state, code, error);
    } catch (ErrorCodeException ex) {
      return "redirect:" + withOAuthOutcome("/jira/replications/" + id + "/edit", ex);
    }
    return "redirect:/jira/replications/" + id + "/edit?oauth=" + OAUTH_CONNECTED;
  }

  @PostMapping("/{id}/oauth/disconnect")
  @Authorized(requiresManager = true)
  public String disconnectOAuth(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      jiraReplicationOAuthService.disconnect(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.jira.replication.oauth.message.disconnected"));
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return editRedirect(id);
  }

  /** The failure of a callback as parameters of the address: its code, and the error of the provider. */
  private static String withOAuthOutcome(String path, ErrorCodeException ex) {
    var message = ex.getMessages().stream().findFirst().orElse(null);
    var url = UriComponentsBuilder.fromPath(path)
        .queryParam("oauth", message != null ? message.getErrorCode().getCode() : SE_OAUTH_STATE_INVALID.getCode());
    if (message != null && message.getErrorCode() == SE_OAUTH_TOKEN_REQUEST_FAILED && !message.getArguments().isEmpty()) {
      url.queryParam("oauthDetail", String.valueOf(message.getArguments().getFirst()));
    }
    return url.encode().build().toUriString();
  }

  /**
   * The message of a callback, from the parameters of the address. Only the codes a callback can end
   * with are read; anything else, typed into the address by hand, shows nothing. The detail is the
   * error of the provider, an OAuth error code, and shown only if it looks like one.
   *
   * @param baseUrl the base URL of the replication, named by the message that its site is not reached
   */
  private void addOAuthOutcome(Model model, String outcome, String detail, String baseUrl) {
    if (outcome == null) {
      return;
    }
    if (OAUTH_CONNECTED.equals(outcome)) {
      model.addAttribute("toastSuccess", messages.getMessage("main.jira.replication.oauth.message.connected"));
      return;
    }
    var failure = OAUTH_FAILURES.get(outcome);
    if (failure == null) {
      return;
    }
    Object argument = switch (failure) {
      case SE_OAUTH_TOKEN_REQUEST_FAILED -> detail != null && OAUTH_ERROR.matcher(detail).matches() ? detail : "unknown";
      case JI_REPLICATION_OAUTH_SITE_NOT_ACCESSIBLE -> baseUrl;
      default -> null;
    };
    model.addAttribute("toastError", messages.getMessage(failure.messageKey(), new Object[]{argument}));
  }

  private static String editRedirect(long id) {
    return "redirect:/jira/replications/" + id + "/edit";
  }

  /**
   * Whether credentials can be stored at all, and whether the stored ones can be used (#1432). The
   * form says so in either case, and asks for them again. With OAuth (#1417): whether connecting is
   * offered, the connected account, and how the stored replication signs in — connecting works on the
   * stored replication, not on an unsaved change of the form.
   *
   * @param stored the replication as it is stored, {@code null} for a new one
   */
  private void addCredentialsModel(Model model, boolean credentialsReadable, JiraReplicationConfigInfo stored) {
    model.addAttribute("credentialsStorable", jiraReplicationConfigService.canStoreCredentials());
    model.addAttribute("credentialsReadable", credentialsReadable);
    model.addAttribute("oauthAvailable", jiraReplicationConfigService.canConnectOAuth());
    model.addAttribute("storedAuthMethod", stored != null ? stored.authMethod() : null);
    model.addAttribute("oauthConnection", stored != null ? stored.oauthConnection() : null);
  }

  private void addFormModel(Model model, JiraReplicationConfigForm form) {
    model.addAttribute("replicationForm", form);
    model.addAttribute("apiFlavors", JiraApiFlavor.values());
    model.addAttribute("authMethods", JiraAuthMethod.values());
    model.addAttribute("isEdit", !form.isNew());
    model.addAttribute("customerorders",
        customerorderService.getSelectableCustomerorders(customerorderSignOf(form)));
    model.addAttribute("suborders", subordersOf(form));
  }

  /** The sign of the stored order, so that the select keeps it once it is hidden (#1005). */
  private String customerorderSignOf(JiraReplicationConfigForm form) {
    var id = form.getCustomerorderId();
    return id == null ? null : customerorderService.getCustomerorderSignsByIds(List.of(id)).get(id);
  }

  /**
   * The suborders of the selected order, at any depth — empty while none is selected. The suborder
   * a replication already points at stays in the list once it is hidden, so that an edit cannot drop
   * the scope and silently write back whatever the browser preselected instead (→ AGENTS.md, #1005).
   */
  private List<Suborder> subordersOf(JiraReplicationConfigForm form) {
    var customerorderId = form.getCustomerorderId();
    return customerorderId == null ? List.of()
        : suborderService.getSelectableSubordersByCustomerorderId(customerorderId, form.getSuborderId());
  }

  private List<String> toMessages(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream().map(message -> message.resolved()).toList();
  }

  private String firstMessageOf(ErrorCodeException ex) {
    return toMessages(ex).stream().findFirst().orElse(ex.getMessage());
  }
}
