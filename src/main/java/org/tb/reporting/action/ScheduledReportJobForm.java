package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

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

  @Override
  public void reset(ActionMapping mapping, HttpServletRequest request) {
    // Reset checkbox to false, so it will be false if not checked in form
    enabled = false;
  }

}
