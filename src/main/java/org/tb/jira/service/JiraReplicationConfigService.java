package org.tb.jira.service;

import static org.tb.common.exception.ErrorCode.AA_NEEDS_MANAGER;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_INVALID;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_BASE_URL_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_JQL_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_NAME_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_NOT_FOUND;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_ORDER_SIGN_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_PAGE_SIZE_INVALID;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_PASSWORD_REQUIRED;
import static org.tb.common.exception.ErrorCode.JI_REPLICATION_USERNAME_REQUIRED;

import java.util.List;
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
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfig;
import org.tb.jira.domain.JiraReplicationConfigData;
import org.tb.jira.domain.JiraReplicationConfigInfo;
import org.tb.jira.domain.JiraReplicationRunOutcome;
import org.tb.jira.persistence.JiraReplicationConfigRepository;

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

  private final JiraReplicationConfigRepository configRepository;
  private final JiraReplicationService jiraReplicationService;
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
    validate(data);
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
    validate(data);
    var config = load(id);
    apply(data, config);
    if (!isBlank(data.password())) {
      config.setPassword(data.password().trim());
    }
    configRepository.save(config);
  }

  public void delete(long id) {
    checkManager();
    // The tickets already replicated under this order sign stay: jira_ticket hangs off
    // customerorder_sign, not off the config, and the rows are not wrong — only no longer kept up to
    // date. The confirmation before deleting says so.
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

  private JiraReplicationConfig load(long id) {
    return configRepository.findById(id)
        .orElseThrow(() -> new InvalidDataException(JI_REPLICATION_NOT_FOUND));
  }

  private void apply(JiraReplicationConfigData data, JiraReplicationConfig config) {
    config.setName(data.name().trim());
    config.setCustomerorderSign(data.customerorderSign().trim());
    config.setBaseUrl(data.baseUrl().trim());
    config.setApiFlavor(data.apiFlavor() != null ? data.apiFlavor() : JiraApiFlavor.SERVER);
    config.setUsername(data.username().trim());
    config.setJql(data.jql().trim());
    config.setParentFieldNames(trimToNull(data.parentFieldNames()));
    config.setPageSize(data.pageSize());
    config.setEnabled(data.enabled());
  }

  private void validate(JiraReplicationConfigData data) {
    requireText(data.name(), JI_REPLICATION_NAME_REQUIRED);
    requireText(data.customerorderSign(), JI_REPLICATION_ORDER_SIGN_REQUIRED);
    requireText(data.baseUrl(), JI_REPLICATION_BASE_URL_REQUIRED);
    requireText(data.username(), JI_REPLICATION_USERNAME_REQUIRED);
    // The replication insists on a JQL query, so a config without one can only ever fail.
    requireText(data.jql(), JI_REPLICATION_JQL_REQUIRED);

    var baseUrl = data.baseUrl().trim().toLowerCase();
    if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
      throw new InvalidDataException(JI_REPLICATION_BASE_URL_INVALID);
    }
    if (data.pageSize() != null && data.pageSize() <= 0) {
      throw new InvalidDataException(JI_REPLICATION_PAGE_SIZE_INVALID);
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
