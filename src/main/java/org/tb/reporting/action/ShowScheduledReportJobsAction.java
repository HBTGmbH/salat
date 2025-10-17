package org.tb.reporting.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tb.auth.struts.LoginRequiredAction;
import org.tb.reporting.service.ScheduledReportJobService;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;

@Component
@RequiredArgsConstructor
public class ShowScheduledReportJobsAction extends LoginRequiredAction<ActionForm> {

  private final ScheduledReportJobService scheduledReportJobService;

  @Value("${salat.reporting.scheduler.cron:0 0 5 * * ?}")
  private String defaultCron;

  @Override
  protected ActionForward executeAuthenticated(ActionMapping mapping, ActionForm form,
      HttpServletRequest request, HttpServletResponse response) {

    var jobs = scheduledReportJobService.getAllJobs();
    request.getSession().setAttribute("scheduledReportJobs", jobs);

    // Build human-readable cron descriptions map
    Map<Long, String> cronDescriptions = new HashMap<>();
    for (var job : jobs) {
      String cronExpr = (job.getCronExpression() == null || job.getCronExpression().isBlank()) ? defaultCron : job.getCronExpression();
      cronDescriptions.put(job.getId(), getHumanReadableCron(cronExpr));
    }

    request.setAttribute("defaultCron", defaultCron);
    request.setAttribute("cronDescriptions", cronDescriptions);

    return mapping.findForward("success");
  }

  private String getHumanReadableCron(String cronExpr) {
    try {
      CronDefinition def = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
      CronParser parser = new CronParser(def);
      Cron cron = parser.parse(cronExpr);
      cron.validate();
      CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
      return descriptor.describe(cron);
    } catch (Exception e) {
      return "unrecognized/invalid cron pattern";
    }
  }
}
