package org.tb.jira.controller;

import lombok.Getter;
import lombok.Setter;
import org.tb.jira.domain.JiraApiFlavor;
import org.tb.jira.domain.JiraReplicationConfigInfo;

/**
 * The form behind the replication config page (#984).
 *
 * <p>{@link #password} is only ever filled by the user. {@link #of(JiraReplicationConfigInfo)}
 * cannot fill it — the info record it maps from does not carry one — so an edit starts with an empty
 * field, which the service reads as "keep the stored password".
 */
@Getter
@Setter
public class JiraReplicationConfigForm {

  private Long id;
  private String name;
  private String customerorderSign;
  private String baseUrl;

  /** Preselected as Server: that is what a config without an explicit flavor has always meant. */
  private JiraApiFlavor apiFlavor = JiraApiFlavor.SERVER;

  private String username;
  private String password;
  private String jql;
  private String parentFieldNames;
  private Integer pageSize;

  /** A new replication is switched on, otherwise creating it would have no visible effect. */
  private boolean enabled = true;

  public boolean isNew() {
    return id == null;
  }

  public static JiraReplicationConfigForm of(JiraReplicationConfigInfo info) {
    var form = new JiraReplicationConfigForm();
    form.setId(info.id());
    form.setName(info.name());
    form.setCustomerorderSign(info.customerorderSign());
    form.setBaseUrl(info.baseUrl());
    form.setApiFlavor(info.apiFlavor());
    form.setUsername(info.username());
    form.setJql(info.jql());
    form.setParentFieldNames(info.parentFieldNames());
    form.setPageSize(info.pageSize());
    form.setEnabled(info.enabled());
    return form;
  }
}
