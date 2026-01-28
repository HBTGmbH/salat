package org.tb.reporting.controller;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.reporting.domain.ScheduledReportJob;
import org.tb.reporting.service.ReportService;
import org.tb.reporting.service.ScheduledReportJobScheduler;
import org.tb.reporting.service.ScheduledReportJobService;

@Controller
@RequestMapping("/reporting/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ScheduledReportJobController {

  private final ScheduledReportJobService jobService;
  private final ReportService reportService;
  private final ScheduledReportJobScheduler scheduledReportJobScheduler;

  @Value("${salat.reporting.scheduler.cron:0 0 5 * * ?}")
  private String defaultCron;

  @GetMapping
  public String list(Model model) {
    var jobs = jobService.getAllJobs();

    Map<Long, String> cronDescriptions = new HashMap<>();
    for (var job : jobs) {
      String cronExpr = (job.getCronExpression() == null || job.getCronExpression().isBlank()) ? defaultCron : job.getCronExpression();
      cronDescriptions.put(job.getId(), jobService.getHumanReadableCron(cronExpr));
    }

    model.addAttribute("pageTitle", "Scheduled Report Jobs");
    model.addAttribute("jobs", jobs);
    model.addAttribute("defaultCron", defaultCron);
    model.addAttribute("cronDescriptions", cronDescriptions);
    return "reporting/scheduled-jobs-list";
  }

  @GetMapping("/create")
  public String createForm(Model model) {
    model.addAttribute("pageTitle", "Create Scheduled Report Job");
    model.addAttribute("job", new ScheduledReportJobForm());
    model.addAttribute("reportDefinitions", reportService.getReportDefinitions());
    model.addAttribute("isEdit", false);
    return "reporting/scheduled-job-form";
  }

  @GetMapping("/edit")
  public String editForm(@RequestParam("id") Long id, Model model) {
    var job = jobService.getJob(id);
    var form = new ScheduledReportJobForm();
    form.setId(job.getId());
    form.setReportDefinitionId(job.getReportDefinition().getId());
    form.setName(job.getName());
    form.setReportParameters(job.getReportParameters());
    form.setRecipientEmails(job.getRecipientEmails());
    form.setEnabled(job.isEnabled());
    form.setCronExpression(job.getCronExpression());
    form.setDescription(job.getDescription());

    model.addAttribute("pageTitle", "Edit Scheduled Report Job");
    model.addAttribute("job", form);
    model.addAttribute("reportDefinitions", reportService.getReportDefinitions());
    model.addAttribute("isEdit", true);
    model.addAttribute("defaultCron", defaultCron);
    model.addAttribute("scheduledTask", scheduledReportJobScheduler.getScheduledTask(id));
    return "reporting/scheduled-job-form";
  }

  @PostMapping("/store")
  public String store(@ModelAttribute("job") ScheduledReportJobForm form, 
                      BindingResult bindingResult,
                      Model model,
                      RedirectAttributes redirectAttributes) {
    
    // Basic validation
    if (form.getName() == null || form.getName().isBlank()) {
      bindingResult.rejectValue("name", "error.name", "Name is required");
    }
    if (form.getReportDefinitionId() == null) {
      bindingResult.rejectValue("reportDefinitionId", "error.reportDefinitionId", "Report is required");
    }
    if (form.getRecipientEmails() == null || form.getRecipientEmails().isBlank()) {
      bindingResult.rejectValue("recipientEmails", "error.recipientEmails", "Recipient emails are required");
    }
    
    if (bindingResult.hasErrors()) {
      model.addAttribute("reportDefinitions", reportService.getReportDefinitions());
      boolean isEdit = form.getId() != null && form.getId() > 0;
      model.addAttribute("pageTitle", isEdit ? "Edit Scheduled Report Job" : "Create Scheduled Report Job");
      model.addAttribute("isEdit", isEdit);
      if(isEdit) {
        model.addAttribute("scheduledTask", scheduledReportJobScheduler.getScheduledTask(form.getId()));
      }
      return "reporting/scheduled-job-form";
    }

    ScheduledReportJob job;
    boolean isEdit = form.getId() != null && form.getId() > 0;
    if (isEdit) {
      job = jobService.getJob(form.getId());
    } else {
      job = new ScheduledReportJob();
    }

    var rd = reportService.getReportDefinition(form.getReportDefinitionId());
    if(rd == null) throw new ErrorResponseException(HttpStatus.BAD_REQUEST);

    job.setReportDefinition(rd);
    job.setName(form.getName());
    job.setReportParameters(form.getReportParameters());
    job.setRecipientEmails(form.getRecipientEmails());
    job.setEnabled(form.isEnabled());
    job.setCronExpression(form.getCronExpression());
    job.setDescription(form.getDescription());

    if (isEdit) {
      jobService.updateJob(job);
      redirectAttributes.addFlashAttribute("toastSuccess", "Scheduled job updated successfully");
    } else {
      jobService.createJob(job);
      redirectAttributes.addFlashAttribute("toastSuccess", "Scheduled job created successfully");
    }

    return "redirect:/reporting/jobs";
  }

  @PostMapping("/delete")
  public String delete(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
    jobService.deleteJob(id);
    redirectAttributes.addFlashAttribute("toastSuccess", "Scheduled job deleted successfully");
    return "redirect:/reporting/jobs";
  }

  @Getter
  @Setter
  public static class ScheduledReportJobForm {
    private Long id;
    private Long reportDefinitionId;
    private String name;
    private String reportParameters;
    private String recipientEmails;
    private boolean enabled = true;
    private String cronExpression;
    private String description;
  }
}
