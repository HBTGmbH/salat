package de.hbt.salat.jira.controller;

import lombok.Getter;
import lombok.Setter;
import de.hbt.salat.jira.domain.JiraTicketRow;

/**
 * The form of a ticket maintained by hand (#1386). Order and suborder are chosen with the filter of
 * the list before a ticket is created, and stay: they travel as hidden fields.
 */
@Getter
@Setter
public class JiraTicketForm {

  private Long id;
  private Long customerorderId;
  private Long suborderId;

  /** Only for showing the scope again after a failed save of an existing ticket. */
  private String scopeSign;

  private String key;
  private String summary;
  private String issueType;
  private String parentKey;

  public boolean isNew() {
    return id == null;
  }

  public static JiraTicketForm of(JiraTicketRow row) {
    var form = new JiraTicketForm();
    form.setId(row.id());
    form.setCustomerorderId(row.customerorderId());
    form.setSuborderId(row.suborderId());
    form.setScopeSign(row.scopeSign());
    form.setKey(row.key());
    form.setSummary(row.summary());
    form.setIssueType(row.issueType());
    form.setParentKey(row.parentKey());
    return form;
  }
}
