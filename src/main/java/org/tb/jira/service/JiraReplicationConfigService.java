package org.tb.jira.service;

import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_INVALID;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_JQL_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_NAME_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_PASSWORD_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_SCOPE_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_USERNAME_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP;

import static java.util.Comparator.comparing;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.util.DateUtils;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraFieldCatalog;
import org.tb.jira.domain.JiraFieldOption;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraReplicationConfigData;
import org.tb.jira.domain.JiraReplicationConfigInfo;
import org.tb.jira.domain.JiraReplicationRunOutcome;
import org.tb.jira.persistence.JiraReplicationConfigRepository;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * Maintains the replication configs that used to be edited by hand via SQL (#984).
 *
 * <p>Manager level throughout, not backoffice: these rows carry the credentials of a foreign system.
 * The REST endpoint that triggers a replication draws the same line.
 *
 * <p>Everything leaving this service is a {@link JiraReplicationConfigInfo} without the password.
 * The stored password is read in exactly two places — when it is kept across an edit, and when it is
 * removed from a failure message — and is written nowhere else.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@Authorized(requiresManager = true)
public class JiraReplicationConfigService {

  /** The last segment of the JIRA plugin type key of a cascading select. */
  private static final String CASCADING_SELECT = "cascadingselect";

  private final JiraReplicationConfigRepository configRepository;
  private final JiraReplicationService jiraReplicationService;
  private final JiraSearchClients jiraSearchClients;
  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final AuthorizedUser authorizedUser;

  @Transactional(readOnly = true)
  public List<JiraReplicationConfigInfo> getAll() {
    checkManager();
    return configRepository.findAllByOrderByNameAsc().stream()
        .map(JiraReplicationConfigInfo::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public JiraReplicationConfigInfo getById(long id) {
    checkManager();
    return JiraReplicationConfigInfo.from(load(id));
  }

  public long create(JiraReplicationConfigData data) {
    checkManager();
    validate(null, data);
    if (isBlank(data.password())) {
      // On an edit an empty field means "keep what is stored"; on a new record there is nothing to
      // keep, so the replication would fail on its first run with a null password.
      throw new InvalidDataException(JI_REPLICATION_PASSWORD_REQUIRED);
    }
    var config = new JiraReplicationConfig();
    apply(data, config);
    config.setPassword(data.password().trim());
    return configRepository.save(config).getId();
  }

  public void update(long id, JiraReplicationConfigData data) {
    checkManager();
    validate(id, data);
    var config = load(id);
    apply(data, config);
    if (!isBlank(data.password())) {
      config.setPassword(data.password().trim());
    }
    configRepository.save(config);
  }

  public void delete(long id) {
    checkManager();
    // The tickets already replicated in this scope stay: jira_ticket hangs off scope_sign, not off
    // the config, and the rows are not wrong — only no longer kept up to date. The confirmation
    // before deleting says so.
    configRepository.delete(load(id));
  }

  public void setEnabled(long id, boolean enabled) {
    checkManager();
    var config = load(id);
    config.setEnabled(enabled);
    configRepository.save(config);
  }

  /**
   * Clears the watermark so the next run fetches everything the JQL matches again. This is the only
   * write on {@code last_max_updated} the user interface offers — see
   * {@link JiraReplicationConfigData}.
   */
  public void resetWatermark(long id) {
    checkManager();
    var config = load(id);
    config.setLastMaxUpdated(null);
    configRepository.save(config);
  }

  /**
   * Runs one replication right now, regardless of its {@code enabled} state — being able to try a
   * config out before switching it on is the point of the button.
   *
   * <p>Runs synchronously and outside a transaction: the replication fetches all pages one after
   * another and writes per page, so holding a transaction open across it would keep a database
   * connection busy for the whole of a foreign system's response time. The caller is expected to
   * make the wait visible.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public JiraReplicationRunOutcome runNow(long id) {
    checkManager();
    var config = load(id);
    try {
      jiraReplicationService.runReplication(config);
      return JiraReplicationRunOutcome.succeeded(config.getName());
    } catch (Exception ex) {
      log.error("Manually started JIRA replication failed: id={}, name={}", id, config.getName(), ex);
      return JiraReplicationRunOutcome.failed(config.getName(), redacted(ex, config.getPassword()));
    }
  }

  /**
   * The fields the JIRA instance behind this config knows (#1013), so the configuration can be
   * picked rather than typed from memory.
   *
   * <p>Base URL and credentials come from the stored config, addressed by its id — never from the
   * caller. A caller that could name the target would turn this into an authenticated HTTP client
   * for any address the server can reach, which is a different capability from "maintain the
   * replications". The stored password is read here and goes no further than the request.
   *
   * <p>Outside a transaction for the reason {@link #runNow} gives: a foreign system's response time
   * must not hold a database connection.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public JiraFieldCatalog getSelectableFields(long id) {
    checkManager();
    var config = load(id);
    try {
      var request = new JiraFieldsRequest(config.getBaseUrl(), config.getUsername(), config.getPassword());
      var fields = jiraSearchClients.forFlavor(config.getApiFlavor()).listFields(request);
      return JiraFieldCatalog.of(toOptions(fields));
    } catch (Exception ex) {
      log.error("Could not read the JIRA field catalogue: id={}, name={}", id, config.getName(), ex);
      return JiraFieldCatalog.failed(redacted(ex, config.getPassword()));
    }
  }

  /**
   * Sorted by name, because that is what the picker is scanned by. A cascading select gets a second
   * entry right behind it for its second level — the one case where a path is needed and the only
   * one the catalogue can recognise on its own.
   */
  private static List<JiraFieldOption> toOptions(List<JiraField> fields) {
    var options = new ArrayList<JiraFieldOption>();
    // A Collator rather than String order: comparing code points would file "Änderungsdatum" behind
    // "Zeiterfassung", which in a list somebody scans by name reads as broken. German because the
    // application is (→ ADR-0010); a Collator is stateful, hence one per call.
    var byName = Collator.getInstance(Locale.GERMAN);
    fields.stream()
        .filter(field -> field.getId() != null && !field.getId().isBlank())
        .sorted(comparing(JiraReplicationConfigService::nameOf, byName))
        .forEach(field -> {
          var type = typeOf(field);
          options.add(JiraFieldOption.of(field.getId(), nameOf(field), type));
          if (CASCADING_SELECT.equals(type)) {
            options.add(JiraFieldOption.secondLevelOf(
                field.getId() + ".child.value", nameOf(field), type));
          }
        });
    return options;
  }

  private static String nameOf(JiraField field) {
    return isBlank(field.getName()) ? field.getId() : field.getName();
  }

  /**
   * The value shape in JIRA's own words. {@code schema.custom} carries the full plugin type key and
   * only its last segment says anything, {@code schema.type} covers the standard fields.
   */
  private static String typeOf(JiraField field) {
    var schema = field.getSchema();
    if (schema == null) return null;
    if (!isBlank(schema.getCustom())) {
      var separator = schema.getCustom().lastIndexOf(':');
      return separator < 0 ? schema.getCustom() : schema.getCustom().substring(separator + 1);
    }
    return schema.getType();
  }

  private JiraReplicationConfig load(long id) {
    return configRepository.findById(id)
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_NOT_FOUND));
  }

  private void apply(JiraReplicationConfigData data, JiraReplicationConfig config) {
    config.setName(data.name().trim());
    applyScope(data, config);
    config.setBaseUrl(data.baseUrl().trim());
    config.setApiFlavor(data.apiFlavor() != null ? data.apiFlavor() : JiraApiFlavor.SERVER);
    config.setUsername(data.username().trim());
    config.setJql(data.jql().trim());
    config.setParentFieldNames(trimToNull(data.parentFieldNames()));
    applyFieldNames(data, config);
    config.setPageSize(data.pageSize());
    config.setEnabled(data.enabled());
    applyWorklogSync(data, config);
  }

  /**
   * The start date is the moment of switching on (#1007) when none is given: the first run would
   * otherwise write every booking the order ever carried into JIRA at once. It stays editable, so a
   * period can be filled in deliberately — moving it back is a decision, not an accident.
   *
   * <p>Switching the sync off keeps the date. What SALAT already wrote stays in JIRA and stays
   * remembered; switching on again picks up where it left off instead of starting a second period
   * next to the first.
   */
  private void applyWorklogSync(JiraReplicationConfigData data, JiraReplicationConfig config) {
    config.setWorklogSyncEnabled(data.worklogSyncEnabled());
    if (data.worklogSyncFrom() != null) {
      config.setWorklogSyncFrom(data.worklogSyncFrom());
    } else if (data.worklogSyncEnabled() && config.getWorklogSyncFrom() == null) {
      config.setWorklogSyncFrom(DateUtils.today());
    }
  }

  /**
   * Moving a replication to another scope resets the watermark (#1025), for the reason
   * {@link #applyFieldNames} gives: the new scope has no tickets of its own yet, and with the
   * watermark in place the search would only ever find what JIRA has touched since. The tickets
   * already replicated stay where they are — under the old scope, no longer kept up to date, just as
   * they stay when the config is deleted. The field help says so.
   */
  private void applyScope(JiraReplicationConfigData data, JiraReplicationConfig config) {
    var scopeSign = data.scopeSign().trim();
    if (!Objects.equals(scopeSign, config.getScopeSign())) {
      log.info("Scope of JIRA replication {} changed from {} to {}, resetting the watermark so the "
          + "tickets of the new scope are fetched", config.getName(), config.getScopeSign(), scopeSign);
      config.setLastMaxUpdated(null);
    }
    config.setScopeSign(scopeSign);
  }

  /**
   * Changing the additional fields resets the watermark (#881). The replication rewrites a ticket
   * whose field configuration has changed, but only if it gets to see it at all — and with the
   * watermark in place the search keeps every already replicated ticket out, so the new fields would
   * reach nothing but the tickets edited in JIRA afterwards. The field help says so.
   */
  private void applyFieldNames(JiraReplicationConfigData data, JiraReplicationConfig config) {
    var additional = trimToNull(data.additionalFieldNames());
    var inherited = trimToNull(data.inheritedFieldNames());
    if (!Objects.equals(additional, config.getAdditionalFieldNames())
        || !Objects.equals(inherited, config.getInheritedFieldNames())) {
      log.info("Field configuration of JIRA replication {} changed, resetting the watermark so the "
          + "already replicated tickets are fetched again", config.getName());
      config.setLastMaxUpdated(null);
    }
    config.setAdditionalFieldNames(additional);
    config.setInheritedFieldNames(inherited);
  }

  private void validate(Long id, JiraReplicationConfigData data) {
    requireText(data.name(), JI_REPLICATION_NAME_REQUIRED);
    requireText(data.scopeSign(), JI_REPLICATION_SCOPE_REQUIRED);
    requireText(data.baseUrl(), JI_REPLICATION_BASE_URL_REQUIRED);
    requireText(data.username(), JI_REPLICATION_USERNAME_REQUIRED);
    // The replication insists on a JQL query, so a config without one can only ever fail.
    requireText(data.jql(), JI_REPLICATION_JQL_REQUIRED);
    checkScopeExists(data.scopeSign().trim());

    var baseUrl = data.baseUrl().trim().toLowerCase();
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw new InvalidDataException(JI_REPLICATION_BASE_URL_INVALID);
    }
    if (data.pageSize() != null && data.pageSize() <= 0) {
      throw new InvalidDataException(JI_REPLICATION_PAGE_SIZE_INVALID);
    }
    checkWorklogScopeIsExclusive(id, data);
  }

  /**
   * No two worklog syncs may cover the same bookings on the same JIRA instance (#1007). Since #1025
   * an order-wide replication and one for a suborder inside it are the normal case — with worklogs
   * switched on for both, each would write its own entry on the same ticket and the same day, and
   * the time would stand twice in JIRA. Nothing in SALAT shows that, so it is refused here rather
   * than noticed there.
   *
   * <p>Only within one instance: two configs pointing at different JIRA installations share no
   * issue keys and cannot collide, however much their scopes overlap.
   */
  private void checkWorklogScopeIsExclusive(Long id, JiraReplicationConfigData data) {
    if (!data.worklogSyncEnabled()) {
      return;
    }
    var scopeSign = data.scopeSign().trim();
    var baseUrl = normalizedBaseUrl(data.baseUrl());
    var customerorderSign = customerorderSignOf(scopeSign);
    for (var other : configRepository.findAllByOrderByNameAsc()) {
      if (id != null && id.equals(other.getId())) continue;
      if (!Boolean.TRUE.equals(other.getWorklogSyncEnabled())) continue;
      if (!baseUrl.equals(normalizedBaseUrl(other.getBaseUrl()))) continue;
      // Different customer orders never share a suborder, so their branches cannot overlap - and
      // comparing the signs as strings would call 0283 and 0283/03.20 an overlap although they are
      // two orders, not an order and its suborder.
      if (!customerorderSign.equals(customerorderSignOf(other.getScopeSign()))) continue;
      if (scopesOverlap(scopeSign, other.getScopeSign(), customerorderSign)) {
        log.info("Worklog sync of scope {} refused: it overlaps with the replication {} on scope {} "
            + "at the same JIRA instance", scopeSign, other.getName(), other.getScopeSign());
        throw new InvalidDataException(JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
      }
    }
  }

  /**
   * Whether two scopes of the same customer order cover a common suborder. An order-wide scope
   * covers every branch of its order; two suborder paths overlap when one is the other or lies
   * below it, which the fully qualified sign shows at a segment boundary.
   */
  private static boolean scopesOverlap(String one, String other, String customerorderSign) {
    if (customerorderSign.equals(one) || customerorderSign.equals(other)) {
      return true;
    }
    return covers(one, other) || covers(other, one);
  }

  private static boolean covers(String outer, String inner) {
    return inner.equals(outer) || inner.startsWith(outer + "/");
  }

  /** A trailing slash and the case of the host say nothing about which instance is meant. */
  private static String normalizedBaseUrl(String baseUrl) {
    if (baseUrl == null) return "";
    var trimmed = baseUrl.trim().toLowerCase();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /**
   * Which customer order a stored scope sits under (#1025) — the order itself when the replication
   * is order-wide, the order of the suborder otherwise. The edit form needs it to load the suborders
   * to choose from.
   *
   * <p>Deliberately asked rather than parsed. Splitting the scope at its first slash looks obvious
   * and is wrong: an order sign may contain a slash itself, so {@code 0283/03.20/F&E/01} would be
   * read as the order {@code 0283}, the form would open on a different order with nothing
   * preselected, and saving it again would silently move the replication there.
   *
   * <p>An order sign wins over a suborder path that reads the same. Every row written before #1025
   * carries an order sign and must keep meaning "the whole order"; a collision the other way can
   * only arise from an order deliberately named like a path.
   */
  @Transactional(readOnly = true)
  public String customerorderSignOf(String scopeSign) {
    checkManager();
    if (isBlank(scopeSign) || customerorderService.getCustomerorderBySign(scopeSign) != null) {
      return scopeSign;
    }
    var suborder = suborderService.getSuborderByCompleteOrderSign(scopeSign);
    return suborder != null ? suborder.getCustomerorder().getSign() : scopeSign;
  }

  /**
   * A scope nobody can book on is a replication nobody reads (#1025): the suggestions resolve the
   * branch of the chosen suborder upwards, so a sign that is in no branch never matches anything.
   * The form picks the scope rather than letting it be typed, so this catches a post that bypasses
   * the select and a record whose order was renamed in between.
   *
   * <p>Both readings are tried, and the shape of the sign decides nothing — an order-wide scope on
   * the order {@code 0283/03.20} carries a slash without being a suborder path.
   *
   * <p>Hidden and expired records count as existing. {@code hide} declutters the pickers of the
   * order module; it says nothing about whether work is still being booked, and an expired order is
   * exactly the one whose tickets are still being looked at while the last bookings are corrected.
   */
  private void checkScopeExists(String scopeSign) {
    if (customerorderService.getCustomerorderBySign(scopeSign) == null
        && !suborderService.existsSuborderWithCompleteOrderSign(scopeSign)) {
      throw new InvalidDataException(JI_REPLICATION_SCOPE_NOT_FOUND);
    }
  }

  private static void requireText(String value, ErrorCode errorCode) {
    if (isBlank(value)) {
      throw new InvalidDataException(errorCode);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  /**
   * The reason a run failed, with the stored password taken out of it. A client library that puts
   * the credentials it used into its message would otherwise print them as a toast.
   */
  private static String redacted(Exception ex, String password) {
    var message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    return isBlank(password) ? message : message.replace(password, "***");
  }

  private void checkManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }
}
