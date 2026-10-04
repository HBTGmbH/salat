package de.hbt.salat.jira.service;

import static de.hbt.salat.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_JQL_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_NAME_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_PASSWORD_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SCOPE_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_SCOPE_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_USERNAME_REQUIRED;
import static de.hbt.salat.common.exception.ErrorCode.JI_REPLICATION_WORKLOG_SCOPE_OVERLAP;

import static java.util.Comparator.comparing;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraFieldCatalog;
import de.hbt.salat.jira.domain.JiraFieldOption;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.domain.JiraReplicationConfigData;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.order.domain.SuborderLocation;

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
  private final JiraReplicationRunService jiraReplicationRunService;
  private final JiraSearchClients jiraSearchClients;
  private final JiraScopes scopes;
  private final AuthorizedUser authorizedUser;

  @Transactional(readOnly = true)
  public List<JiraReplicationConfigInfo> getAll() {
    checkManager();
    var configs = configRepository.findAllByOrderByNameAsc();
    var signs = scopes.signsOf(configs);
    return configs.stream()
        .map(config -> JiraReplicationConfigInfo.from(config, signs.get(config.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public JiraReplicationConfigInfo getById(long id) {
    checkManager();
    var config = load(id);
    return JiraReplicationConfigInfo.from(config,
        scopes.signOf(config.getCustomerorderId(), config.getSuborderId()));
  }

  public long create(JiraReplicationConfigData data) {
    checkManager();
    var scopeSign = validate(null, data);
    if (isBlank(data.password())) {
      // On an edit an empty field means "keep what is stored"; on a new record there is nothing to
      // keep, so the replication would fail on its first run with a null password.
      throw new InvalidDataException(JI_REPLICATION_PASSWORD_REQUIRED);
    }
    var config = new JiraReplicationConfig();
    apply(data, config, scopeSign);
    config.setPassword(data.password().trim());
    return configRepository.save(config).getId();
  }

  public void update(long id, JiraReplicationConfigData data) {
    checkManager();
    var scopeSign = validate(id, data);
    var config = load(id);
    apply(data, config, scopeSign);
    if (!isBlank(data.password())) {
      config.setPassword(data.password().trim());
    }
    configRepository.save(config);
  }

  public void delete(long id) {
    checkManager();
    // The tickets already replicated in this scope stay: jira_ticket hangs off the scope, not off
    // the config, and the rows are not wrong — only no longer kept up to date. The confirmation
    // before deleting says so. Its run history goes with it: a run nobody can name any more says
    // nothing (#1282).
    var config = load(id);
    jiraReplicationRunService.deleteRunsOf(id);
    configRepository.delete(config);
  }

  public void setEnabled(long id, boolean enabled) {
    checkManager();
    var config = load(id);
    config.setEnabled(enabled);
    configRepository.save(config);
  }

  /**
   * Clears the watermark so the next run fetches everything the JQL matches again and removes the
   * tickets it no longer matches (#1167). This is the only write on {@code last_max_updated} the
   * user interface offers — see {@link JiraReplicationConfigData}.
   */
  public void resetWatermark(long id) {
    checkManager();
    var config = load(id);
    config.setLastMaxUpdated(null);
    configRepository.save(config);
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
   * <p>Outside a transaction: a foreign system's response time must not hold a database connection.
   * Nothing is written here, so the empty transaction scope this leaves inside the request is
   * harmless — see AGENTS.md on {@code NOT_SUPPORTED} (#1282).
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
      return JiraFieldCatalog.failed(JiraCredentialRedaction.redacted(ex, config.getPassword()));
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

  private void apply(JiraReplicationConfigData data, JiraReplicationConfig config, String scopeSign) {
    config.setName(data.name().trim());
    applyScope(data, config, scopeSign);
    config.setBaseUrl(data.baseUrl().trim());
    config.setApiFlavor(data.apiFlavor() != null ? data.apiFlavor() : JiraApiFlavor.SERVER);
    config.setUsername(data.username().trim());
    applyJql(data, config);
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
    config.setWorklogSyncInvoiceableOnly(data.worklogSyncInvoiceableOnly());
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
   *
   * <p>The scope is compared by id (#1322): a renamed order is the same scope, a different order of
   * the same name is not. The sign is written alongside as a mirror for reports and ETL definitions.
   */
  private void applyScope(JiraReplicationConfigData data, JiraReplicationConfig config, String scopeSign) {
    if (!Objects.equals(data.customerorderId(), config.getCustomerorderId())
        || !Objects.equals(data.suborderId(), config.getSuborderId())) {
      log.info("Scope of JIRA replication {} changed from order {}/suborder {} to {}, resetting the "
          + "watermark so the tickets of the new scope are fetched", config.getName(),
          config.getCustomerorderId(), config.getSuborderId(), scopeSign);
      config.setLastMaxUpdated(null);
    }
    config.setCustomerorderId(data.customerorderId());
    config.setSuborderId(data.suborderId());
    config.setScopeSign(scopeSign);
  }

  /**
   * Changing the JQL resets the watermark (#1167), because only a run without it sees every ticket
   * the JQL matches. After widening, that run fetches the older tickets that match now — with the
   * watermark in place they would only arrive once edited in JIRA. After narrowing, it removes the
   * tickets that no longer match. The field help says so.
   */
  private void applyJql(JiraReplicationConfigData data, JiraReplicationConfig config) {
    var jql = data.jql().trim();
    if (!Objects.equals(jql, config.getJql())) {
      log.info("JQL of JIRA replication {} changed, resetting the watermark so the next run fetches "
          + "everything it matches and removes the tickets it no longer does", config.getName());
      config.setLastMaxUpdated(null);
    }
    config.setJql(jql);
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

  /**
   * @return the sign of the scope as the order tree carries it now, for the mirror column
   */
  private String validate(Long id, JiraReplicationConfigData data) {
    requireText(data.name(), JI_REPLICATION_NAME_REQUIRED);
    if (data.customerorderId() == null) {
      throw new InvalidDataException(JI_REPLICATION_SCOPE_REQUIRED);
    }
    requireText(data.baseUrl(), JI_REPLICATION_BASE_URL_REQUIRED);
    requireText(data.username(), JI_REPLICATION_USERNAME_REQUIRED);
    // The replication insists on a JQL query, so a config without one can only ever fail.
    requireText(data.jql(), JI_REPLICATION_JQL_REQUIRED);
    var location = checkScopeExists(data);

    var baseUrl = data.baseUrl().trim().toLowerCase();
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw new InvalidDataException(JI_REPLICATION_BASE_URL_INVALID);
    }
    if (data.pageSize() != null && data.pageSize() <= 0) {
      throw new InvalidDataException(JI_REPLICATION_PAGE_SIZE_INVALID);
    }
    checkWorklogScopeIsExclusive(id, data, location);
    return location.map(SuborderLocation::completeOrderSign)
        .orElseGet(() -> scopes.signOf(data.customerorderId(), null));
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
  private void checkWorklogScopeIsExclusive(Long id, JiraReplicationConfigData data,
                                            Optional<SuborderLocation> location) {
    if (!data.worklogSyncEnabled()) {
      return;
    }
    var baseUrl = normalizedBaseUrl(data.baseUrl());
    for (var other : configRepository.findAllByOrderByNameAsc()) {
      if (id != null && id.equals(other.getId())) continue;
      if (!Boolean.TRUE.equals(other.getWorklogSyncEnabled())) continue;
      if (!baseUrl.equals(normalizedBaseUrl(other.getBaseUrl()))) continue;
      // Different customer orders never share a suborder, so their branches cannot overlap.
      if (!data.customerorderId().equals(other.getCustomerorderId())) continue;
      if (scopesOverlap(location, other.getSuborderId())) {
        log.info("Worklog sync of JIRA replication {} refused: it overlaps with the replication {} "
            + "at the same JIRA instance", data.name(), other.getName());
        throw new InvalidDataException(JI_REPLICATION_WORKLOG_SCOPE_OVERLAP);
      }
    }
  }

  /**
   * Whether two scopes of the same customer order cover a common suborder. An order-wide scope
   * covers every branch of its order; two suborder scopes overlap when one is the other or lies
   * below it, which the path of suborder ids shows (#1322) — whatever the suborders are called and
   * wherever they were moved.
   *
   * @param location where the scope being saved sits, empty for the whole order
   * @param otherSuborderId the suborder of the other scope, {@code null} for the whole order
   */
  private boolean scopesOverlap(Optional<SuborderLocation> location, Long otherSuborderId) {
    if (location.isEmpty() || otherSuborderId == null) {
      return true;
    }
    var other = scopes.locationOf(otherSuborderId);
    return location.get().liesWithin(otherSuborderId)
        || other.map(it -> it.liesWithin(location.get().id())).orElse(false);
  }

  /** A trailing slash and the case of the host say nothing about which instance is meant. */
  private static String normalizedBaseUrl(String baseUrl) {
    if (baseUrl == null) return "";
    var trimmed = baseUrl.trim().toLowerCase();
    return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }

  /**
   * A scope nobody can book on is a replication nobody reads (#1025). The form picks order and
   * suborder rather than letting them be typed, so this catches a post that bypasses the selects,
   * a record deleted in between, and a suborder that belongs to a different order than the one
   * named.
   *
   * <p>Hidden and expired records count as existing. {@code hide} declutters the pickers of the
   * order module; it says nothing about whether work is still being booked, and an expired order is
   * exactly the one whose tickets are still being looked at while the last bookings are corrected.
   *
   * @return where the suborder of the scope sits, empty for an order-wide scope
   */
  private Optional<SuborderLocation> checkScopeExists(JiraReplicationConfigData data) {
    if (!scopes.customerorderExists(data.customerorderId())) {
      throw new InvalidDataException(JI_REPLICATION_SCOPE_NOT_FOUND);
    }
    if (data.suborderId() == null) {
      return Optional.empty();
    }
    var location = scopes.locationOf(data.suborderId())
        .filter(it -> it.customerorderId() == data.customerorderId())
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_SCOPE_NOT_FOUND));
    return Optional.of(location);
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

  private void checkManager() {
    if (!authorizedUser.isManager()) {
      throw new AuthorizationException(AA_NEEDS_MANAGER);
    }
  }
}
