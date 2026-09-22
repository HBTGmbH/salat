package org.tb.jira.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfigInfo;

/**
 * The form behind the replication config page (#984).
 *
 * <p>{@link #password} is only ever filled by the user. {@link #of} cannot fill it — the info record
 * it maps from does not carry one — so an edit starts with an empty field, which the service reads
 * as "keep the stored password".
 *
 * <p>The scope is stored as one sign but edited as two fields (#1025): an order to pick the
 * suborders from, and the suborder itself, which stays empty for an order-wide replication.
 */
@Getter
@Setter
public class JiraReplicationConfigForm {

  private Long id;
  private String name;

  /** Sign of the customer order — on its own already a complete, order-wide scope. */
  private String customerorderSign;

  /**
   * Fully qualified sign of the chosen suborder, {@code AUFTRAG/01/02}; empty for the whole order.
   * The suborder sign alone would not do: {@code AUFTRAG/A/01} and {@code AUFTRAG/B/01} may both
   * exist, so what identifies the scope is the path, not the last segment.
   */
  private String suborderSign;

  private String baseUrl;

  /** Preselected as Server: that is what a config without an explicit flavor has always meant. */
  private JiraApiFlavor apiFlavor = JiraApiFlavor.SERVER;

  private String username;
  private String password;
  private String jql;
  private String parentFieldNames;
  private String additionalFieldNames;
  private String inheritedFieldNames;
  private Integer pageSize;

  /** A new replication is switched on, otherwise creating it would have no visible effect. */
  private boolean enabled = true;

  /**
   * Whether the booked hours are written back to JIRA as worklogs (#1007). Off by default, on a new
   * config as well: writing into a foreign system is never a side effect of creating a replication.
   */
  private boolean worklogSyncEnabled;

  /**
   * First day the worklog sync covers. Left empty while the switch is turned on, the service fills
   * it with today — the first run would otherwise write the whole history of the order at once.
   */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate worklogSyncFrom;

  public boolean isNew() {
    return id == null;
  }

  /** What the two fields amount to: the suborder if one is chosen, the order itself otherwise. */
  public String getScopeSign() {
    return suborderSign == null || suborderSign.isBlank() ? customerorderSign : suborderSign;
  }

  /**
   * @param customerorderSign the order the stored scope sits under, as
   *     {@code JiraReplicationConfigService.customerorderSignOf} resolved it. Passed in rather than
   *     derived here: telling an order-wide scope from a suborder path takes a look at the order
   *     tree, because an order sign may contain a slash itself.
   */
  public static JiraReplicationConfigForm of(JiraReplicationConfigInfo info, String customerorderSign) {
    var form = new JiraReplicationConfigForm();
    form.setId(info.id());
    form.setName(info.name());
    form.applyScope(info.scopeSign(), customerorderSign);
    form.setBaseUrl(info.baseUrl());
    form.setApiFlavor(info.apiFlavor());
    form.setUsername(info.username());
    form.setJql(info.jql());
    form.setParentFieldNames(info.parentFieldNames());
    form.setAdditionalFieldNames(info.additionalFieldNames());
    form.setInheritedFieldNames(info.inheritedFieldNames());
    form.setPageSize(info.pageSize());
    form.setEnabled(info.enabled());
    form.setWorklogSyncEnabled(info.worklogSyncEnabled());
    form.setWorklogSyncFrom(info.worklogSyncFrom());
    return form;
  }

  /**
   * Puts a stored scope into the two fields. A scope equal to the order is order-wide and leaves
   * the suborder empty; anything else is the fully qualified sign the suborder select offers as its
   * option values, and is kept whole.
   */
  private void applyScope(String scopeSign, String customerorderSign) {
    this.customerorderSign = customerorderSign;
    this.suborderSign = scopeSign == null || scopeSign.equals(customerorderSign) ? null : scopeSign;
  }
}
