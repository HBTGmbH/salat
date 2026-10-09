package de.hbt.salat.beta.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.beta.domain.FeedbackTrigger;
import de.hbt.salat.beta.service.BetaFeatureService;
import de.hbt.salat.beta.service.BetaFeedbackService;
import de.hbt.salat.beta.service.BetaUsageService;

/**
 * What a page sends about a beta (#1447): switching it on from the hint beside it, a use that
 * happens in the browser alone, and the answers to the questions about it. Open to every login, as
 * the settings are — a beta is a preference of the person.
 */
@Controller
@RequestMapping("/beta")
@RequiredArgsConstructor
@Authorized
public class BetaController {

  private final BetaFeatureService betaFeatureService;
  private final BetaUsageService betaUsageService;
  private final BetaFeedbackService betaFeedbackService;

  /**
   * Switches on a single beta feature from an in-context link, such as a hint next to the feature.
   * Answers with {@code HX-Refresh} so HTMX reloads the current page with the feature applied,
   * instead of navigating the user away from the form they were filling in.
   */
  @PostMapping("/{key}/enable")
  public ResponseEntity<Void> enable(@PathVariable String key) {
    betaFeatureService.enableForCurrentUser(key);
    return ResponseEntity.noContent().header("HX-Refresh", "true").build();
  }

  /** A use the server does not see otherwise ({@code data-beta-usage}); unknown ones are ignored. */
  @PostMapping("/usage")
  public ResponseEntity<Void> usage(@RequestParam String feature, @RequestParam String event) {
    betaUsageService.count(feature, event);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/feedback")
  public String feedback(@RequestParam String feature, @RequestParam FeedbackTrigger trigger,
      @RequestParam(required = false) Integer rating, @RequestParam(required = false) String comment) {
    betaFeedbackService.submit(feature, trigger, rating, comment);
    return "beta/feedback :: thanks";
  }

  @PostMapping("/feedback/postpone")
  public String postpone(@RequestParam String feature) {
    betaFeedbackService.postpone(feature);
    return "beta/feedback :: gone";
  }

  @PostMapping("/feedback/decline")
  public String decline(@RequestParam String feature, @RequestParam FeedbackTrigger trigger) {
    betaFeedbackService.decline(feature, trigger);
    return "beta/feedback :: gone";
  }
}
