package de.hbt.salat.dailyreport.controller;

import static de.hbt.salat.common.exception.ErrorCode.RL_REVIEWED_PERIOD_CHANGED;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.ui.Model;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.viewhelper.ErrorCodeViewHelper;
import de.hbt.salat.dailyreport.domain.TimereportReview;
import de.hbt.salat.dailyreport.viewhelper.ReviewLinks;
import de.hbt.salat.dailyreport.viewhelper.TimereportReviewViewHelper;

/**
 * Was die Übersichten vor der Freigabe (#760) und vor der Abnahme (#1122) in
 * {@link ReleaseController} und {@link AcceptanceController} gleich machen: den gewählten Monat
 * lesen, die Seite befüllen und nach dem Abschicken die eine Meldung wählen.
 *
 * <p>Scheitert das Abschicken, landet die Person wieder in der Übersicht, und die zeigt die Befunde
 * an ihrem Tag oder über dem Zeitraum. Ein Toast je Befund wiederholte sie nur (#760: nicht als
 * Toast); er sagt deshalb nur, dass nichts freigegeben oder abgenommen wurde. Einzig die geänderte
 * Übersicht ({@code RL-0008}) nennt er selbst — sie erklärt, warum die Seite jetzt anders aussieht
 * als vor dem Klick.
 */
final class ReviewPage {

  static final String RELEASE_VIEW_NAME = "dailyreport/release-review";
  static final String ACCEPTANCE_VIEW_NAME = "dailyreport/acceptance-review";

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

  private ReviewPage() {
  }

  /**
   * Der gewählte Monat, {@code YYYY-MM}. Fehlt er oder ist er keiner, gibt es keine Übersicht — nie
   * „bis heute", wie es das Freigeben früher bei einem leeren Feld tat.
   */
  static Optional<YearMonth> month(String until) {
    if (until == null || until.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(YearMonth.parse(until.trim()));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /**
   * Befüllt die Seite. Der Knopf „Neue Buchung" der Kopfzeile (#1156) führt nach dem Speichern und
   * beim Abbrechen in diese Übersicht zurück. Er nennt die Person der Übersicht nur, wenn für sie
   * angelegt werden darf ({@code canCreate}, wie die Verweise an den Tagen ohne Buchung) — sonst
   * öffnete das Formular für jemanden, für den das Speichern scheitert, und es bucht auf die gemerkte
   * Auswahl. Einen Tag nennt er nicht: die Übersicht zeigt einen Zeitraum.
   */
  static void addReview(Model model, TimereportReview review, ReviewLinks links, String view,
      ErrorCodeViewHelper errorCodeViewHelper) {
    model.addAttribute("review", TimereportReviewViewHelper.from(review, errorCodeViewHelper));
    model.addAttribute("reviewLinks", links);
    model.addAttribute("reviewView", view);
    model.addAttribute("newBookingUrl", TimereportController.newBookingUrl(null,
        review.canCreate() ? review.employeecontractId() : null, links.currentUrl()));
  }

  /** Die Meldung nach dem Freigeben: der Zeitraum, der freigegeben wurde. */
  static String releasedMessage(MessageSourceAccessor messages, LocalDate begin, LocalDate end) {
    return messages.getMessage("main.release.review.success.release.text",
        new Object[] {DATE.format(begin), DATE.format(end)});
  }

  /** Die Meldung nach dem Abnehmen: der Zeitraum, der abgenommen wurde (#1122). */
  static String acceptedMessage(MessageSourceAccessor messages, LocalDate begin, LocalDate end) {
    return messages.getMessage("main.release.review.success.accept.text",
        new Object[] {DATE.format(begin), DATE.format(end)});
  }

  /**
   * Die eine Meldung, wenn das Abschicken scheitert: die geänderte Übersicht beim Namen, alles andere
   * unter {@code genericKey} — die Übersicht zeigt es ohnehin.
   */
  /** Ein Tag, wie die Meldungen der Übersicht ihn nennen. */
  static String formatDate(LocalDate date) {
    return DATE.format(date);
  }

  static String failureMessage(ErrorCodeException ex, ErrorCodeViewHelper errorCodeViewHelper,
      MessageSourceAccessor messages, String genericKey) {
    return ex.getMessages().stream()
        .filter(message -> message.getErrorCode() == RL_REVIEWED_PERIOD_CHANGED)
        .findFirst()
        .map(message -> errorCodeViewHelper.toViewMessage(message).resolved())
        .orElseGet(() -> messages.getMessage(genericKey));
  }
}
