package org.tb.reporting.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class ScheduledReportJobForm extends ActionForm {

  private Long id;
  private Long reportDefinitionId;
  private String name;
  private String reportParameters;
  private String recipientEmails;
  private boolean enabled = true;
  private String cronExpression;
  private String description;

}
