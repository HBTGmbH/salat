package org.tb.etl.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.util.DateUtils;
import org.tb.common.viewhelper.ErrorCodeViewHelper;
import org.tb.etl.service.ETLRunHistoryService;
import org.tb.etl.service.ETLRunLauncher;
import org.tb.etl.service.ETLService;
import org.tb.etl.viewhelper.ETLRunViewHelper;

/**
 * Zeigt die Laufhistorie des ETL (#573) und stößt einen Lauf an (#1071).
 *
 * <p>Wer einen ETL ausführen darf, darf auch sehen, wie die Läufe ausgegangen sind, und darf einen
 * anstoßen: Geschäftsführung und Administration wie bisher, dazu jede Anmeldung mit einer Regel der
 * Kategorie {@code ETL} und {@code EXECUTE}. Ein Entweder-oder trägt keine Annotation — die Prüfung
 * steht als Laufzeitprüfung in {@code ETLRunHistoryService} und {@code ETLService} (→ AGENTS.md,
 * ADR-0006).
 *
 * <p>Deshalb trägt <b>keine</b> der schreibenden Methoden ein eigenes
 * {@code @Authorized(requiresManager = true)}: eine Annotation an der Methode ersetzt die der Klasse
 * vollständig, und {@code requiresManager} sperrte genau die Regel aus, die hier durchkommen soll.
 * Die Klassenannotation zieht die Grenze nach unten.
 */
@Controller
@RequestMapping("/etl/runs")
@RequiredArgsConstructor
@Authorized(requireUnrestricted = true)
public class ETLRunHistoryController {

  static final int DEFAULT_LIMIT = 100;
  static final int MAX_LIMIT = 1000;

  /** Derselbe Zeitraum, mit dem der nächtliche Lauf arbeitet. */
  static final int DEFAULT_MONTHS_BACK = 3;

  /**
   * Der Wert des Sammeleintrags „alle Definitionen" im Formular.
   *
   * <p>Nicht der leere Wert: TomSelect verwirft eine Option ohne Wert und böte sie höchstens als
   * Platzhalter an — sie wäre nach einer Auswahl nicht wieder zu treffen. {@code *} ist im ETL
   * ohnehin die Schreibweise für „alle Objekte"; {@code AuthService} macht aus einem leeren Objekt
   * einer Berechtigungsregel genau dieses Zeichen. Nach innen bleibt es bei {@code null} — der
   * Dienst kennt kein Sonderzeichen.
   */
  static final String ALL_DEFINITIONS = "*";

  private final ETLRunHistoryService etlRunHistoryService;
  private final ETLService etlService;
  private final ETLRunLauncher etlRunLauncher;
  private final ErrorCodeViewHelper errorCodeViewHelper;
  private final MessageSourceAccessor messages;

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
    addRunFormModel(model);
    return "etl/run-list";
  }

  /**
   * Stößt einen Lauf an und leitet auf die Liste um, in der er sofort steht (#1071).
   *
   * <p>Die Parameter heißen nicht {@code f…}: dieses Präfix gehört den gemerkten Filtern
   * (→ ADR-0022), und der {@code UiStateFilter} schöbe einen gemerkten Wert als Rückfallwert unter —
   * bei einem Anstoßformular genau das Falsche.
   *
   * <p><b>Die fehlende Berechtigung wird nicht gefangen.</b> {@code AuthorizationException} ist eine
   * Unterklasse von {@code ErrorCodeException}; ein {@code catch} darauf machte aus der Absage eine
   * Umleitung mit Meldung — also genau das stille Nichtstun, das das Ticket ausschließt. Weitergereicht
   * beantwortet {@code AuthorizationExceptionHandler} sie mit 403.
   */
  @PostMapping("/run")
  public String run(@RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate dateFrom,
                    @RequestParam @DateTimeFormat(iso = ISO.DATE) LocalDate dateUntil,
                    @RequestParam(required = false) String definitionName,
                    RedirectAttributes redirectAttributes) {
    var chosen = chosenDefinition(definitionName);
    try {
      etlRunLauncher.startManualRun(new LocalDateRange(dateFrom, dateUntil), chosen);
      redirectAttributes.addFlashAttribute("toastSuccess", startedMessage(
          chosen, DateUtils.format(dateFrom), DateUtils.format(dateUntil)));
    } catch (AuthorizationException ex) {
      throw ex;
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/etl/runs";
  }

  /**
   * Setzt einen Lauf, der nur wegen eines Absturzes auf „Läuft" steht, auf beendet (#1071).
   *
   * <p>Zur durchgereichten {@code AuthorizationException} siehe {@link #run}.
   */
  @PostMapping("/{id}/mark-finished")
  public String markFinished(@PathVariable long id, RedirectAttributes redirectAttributes) {
    try {
      etlRunHistoryService.markFinished(id);
      redirectAttributes.addFlashAttribute("toastSuccess",
          messages.getMessage("main.etl.run.message.markedfinished"));
    } catch (AuthorizationException ex) {
      throw ex;
    } catch (ErrorCodeException ex) {
      redirectAttributes.addFlashAttribute("toastError", firstMessageOf(ex));
    }
    return "redirect:/etl/runs";
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

  private void addRunFormModel(Model model) {
    var today = DateUtils.today();
    model.addAttribute("runDateFrom", today.minusMonths(DEFAULT_MONTHS_BACK));
    model.addAttribute("runDateUntil", today);
    model.addAttribute("etlDefinitions", etlService.getExecutableDefinitions());
    model.addAttribute("runInProgress", etlRunHistoryService.isRunInProgress());
  }

  /**
   * Die Erfolgsmeldung nennt, was tatsächlich läuft — bei einer einzelnen Definition auch, dass
   * ihre Abhängigkeiten mitlaufen. Das ist die einzige Stelle, an der das steht: die Liste zeigt in
   * diesem Moment nur eine Zeile mit „Läuft" und noch keine Meldung.
   */
  /** Der Sammeleintrag und ein leeres Feld bedeuten dasselbe: alle, die ich ausführen darf. */
  private static String chosenDefinition(String definitionName) {
    if (definitionName == null || definitionName.isBlank()
        || ALL_DEFINITIONS.equals(definitionName)) {
      return null;
    }
    return definitionName;
  }

  private String startedMessage(String definitionName, String from, String until) {
    if (definitionName == null || definitionName.isBlank()) {
      return messages.getMessage("main.etl.run.message.started.all", new Object[]{from, until});
    }
    return messages.getMessage("main.etl.run.message.started.single",
        new Object[]{definitionName, from, until});
  }

  private String firstMessageOf(ErrorCodeException ex) {
    return errorCodeViewHelper.toViewMessages(ex).stream()
        .map(Object::toString)
        .findFirst()
        .orElse(ex.getMessage());
  }

}
