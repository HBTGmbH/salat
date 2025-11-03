package org.tb.reporting.web;

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.tb.reporting.service.ScheduledReportJobService;

@Controller
@RequestMapping("/reporting/jobs2")
@RequiredArgsConstructor
public class ReportingJobsController {

  private final ScheduledReportJobService jobService;

  @Value("${salat.reporting.scheduler.cron:0 0 5 * * ?}")
  private String defaultCron;

  @GetMapping
  public String list(Model model) {
    var jobs = jobService.getAllJobs();

    Map<Long, String> cronDescriptions = new HashMap<>();
    for (var job : jobs) {
      String cronExpr = (job.getCronExpression() == null || job.getCronExpression().isBlank()) ? defaultCron : job.getCronExpression();
      cronDescriptions.put(job.getId(), getHumanReadableCron(cronExpr));
    }

    model.addAttribute("pageTitle", "Scheduled Report Jobs");
    model.addAttribute("jobs", jobs);
    model.addAttribute("defaultCron", defaultCron);
    model.addAttribute("cronDescriptions", cronDescriptions);
    return "reporting/scheduled-jobs-list";
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
