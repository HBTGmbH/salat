package org.tb.etl.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.Authorized;
import org.tb.etl.service.ETLRunHistoryService;
import org.tb.etl.viewhelper.ETLRunViewHelper;

/**
 * Zeigt die Laufhistorie des ETL (#573).
 *
 * <p>Management only, wie die JIRA-Replikationen, unter denen der Menüeintrag steht: hier steht das
 * abgesetzte SQL der Auswertungen, und der nächtliche Lauf ist Betrieb, keine Fachlichkeit.
 */
@Controller
@RequestMapping("/etl/runs")
@RequiredArgsConstructor
@Authorized(requiresManager = true)
public class ETLRunHistoryController {

  static final int DEFAULT_LIMIT = 100;
  static final int MAX_LIMIT = 1000;

  private final ETLRunHistoryService etlRunHistoryService;

  @GetMapping
  public String list(@RequestParam(required = false) Integer fEtlRunLimit,
                     @RequestParam(required = false) Boolean fEtlRunFailedOnly,
                     Model model) {
    int limit = limitOf(fEtlRunLimit);
    boolean failedOnly = Boolean.TRUE.equals(fEtlRunFailedOnly);

    model.addAttribute("runs", etlRunHistoryService.getLatestRuns(limit, failedOnly).stream()
        .map(ETLRunViewHelper::from)
        .toList());
    model.addAttribute("fEtlRunLimit", limit);
    model.addAttribute("fEtlRunFailedOnly", failedOnly);
    return "etl/run-list";
  }

  /**
   * Die Obergrenze ist nicht Bevormundung, sondern der Schutz der Seite: jede Zeile trägt die
   * Meldung des Laufs, und das Feld kommt aus der URL, also aus fremder Hand.
   */
  static int limitOf(Integer requested) {
    if (requested == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(Math.max(requested, 1), MAX_LIMIT);
  }

}
