package de.hbt.salat.jira.controller;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import de.hbt.salat.jira.domain.JiraApiFlavor;
import de.hbt.salat.jira.domain.JiraReplicationConfigInfo;

/**
 * The form behind the replication config page (#984).
 *
 * <p>{@link #password} is only ever filled by the user. {@link #of} cannot fill it — the info record
 * it maps from does not carry one — so an edit starts with an empty field, which the service reads
 * as "keep the stored password".
 *
 * <p>The scope is edited as two fields (#1025): an order to pick the suborders from, and the
 * suborder itself, which stays empty for an order-wide replication. Both carry ids (#1322).
 */
@Getter
@Setter
public class JiraReplicationConfigForm {

  private Long id;
  private String name;

  /** The customer order — on its own already a complete, order-wide scope. */
  private Long customerorderId;

  /** The chosen suborder at any depth; empty for the whole order. */
  private Long suborderId;

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

  /** Whether only bookings on invoiceable suborders are written (#1218). Off by default. */
  private boolean worklogSyncInvoiceableOnly;

  public boolean isNew() {
    return id == null;
  }

  public static JiraReplicationConfigForm of(JiraReplicationConfigInfo info) {
    var form = new JiraReplicationConfigForm();
    form.setId(info.id());
    form.setName(info.name());
    form.setCustomerorderId(info.customerorderId());
    form.setSuborderId(info.suborderId());
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
    form.setWorklogSyncInvoiceableOnly(info.worklogSyncInvoiceableOnly());
    return form;
  }

}
