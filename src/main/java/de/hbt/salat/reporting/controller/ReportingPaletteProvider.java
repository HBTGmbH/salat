package de.hbt.salat.reporting.controller;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.joining;
import static de.hbt.salat.auth.domain.AccessLevel.WRITE;
import static de.hbt.salat.common.palette.PaletteKind.REPORT;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteCommand;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteLink;
import de.hbt.salat.common.palette.PaletteParameter;
import de.hbt.salat.common.palette.PaletteProvider;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteSuggestion;
import de.hbt.salat.common.palette.PaletteSuggestionRequest;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.service.ReportParameters;
import de.hbt.salat.reporting.service.ReportParameters.Kind;
import de.hbt.salat.reporting.service.ReportService;

/**
 * Reports for the object search of the command palette (#1232, ADR-0031), found by their name.
 *
 * <p>The service offers only the reports the person may execute, which is what the page of the
 * first target asks. "Edit" asks what the edit page asks: the role people lead, and write access to
 * the report — an employee granted write access by a rule would get 403 there.
 *
 * <p>The line names the parameters the report expects, so whoever presses Enter knows whether the
 * result comes or the parameter page first. The palette never runs a report with values it chose
 * itself: the parameter page shows them prefilled, and one more Enter runs it.
 *
 * <p>The command {@code report} (#1493, ADR-0032) offers the same reports as values, each with the
 * parameters the palette reads after it: name and kind, so that the browser knows a month from a
 * year without a rule of its own ({@link ReportParameters#kindOf}).
 */
@Component
@RequiredArgsConstructor
public class ReportingPaletteProvider implements PaletteProvider {

  private final ReportService reportService;
  private final ReportAuthorization reportAuthorization;
  private final AuthorizedUser authorizedUser;

  @Override
  public List<PaletteHit> search(PaletteQuery query) {
    return reportService.getPaletteCandidates(query).stream()
        .map(report -> hit(query, report))
        .toList();
  }

  @Override
  public List<PaletteSuggestion> suggest(PaletteSuggestionRequest request) {
    if (!request.is(PaletteCommand.REPORT, PaletteParameter.REPORT)) {
      return List.of();
    }
    var query = request.query();
    // the best match first; the candidates come by name, and a stable sort keeps that order below it
    return reportService.getPaletteCandidates(query).stream()
        .sorted(comparingInt((ReportDefinition report) -> query.match(report.getName())).reversed())
        .map(report -> suggestion(query, report))
        .toList();
  }

  private PaletteSuggestion suggestion(PaletteQuery query, ReportDefinition report) {
    var parameters = paletteOrder(ReportParameters.names(report.getSql()));
    return new PaletteSuggestion(String.valueOf(report.getId()), report.getName(), null, parametersNote(parameters),
        false, false, query.isKey(report.getName()), encoded(parameters));
  }

  /**
   * The parameters as the browser reads them after the report, in the order it asks for them: the
   * suggestion of the command brings them along, and so does the hit of the search, which Tab takes
   * into the command (#1493).
   */
  private static String encodedParameters(ReportDefinition report) {
    return encoded(paletteOrder(ReportParameters.names(report.getSql())));
  }

  private static String encoded(List<String> parameters) {
    return parameters.stream()
        .map(name -> name + ":" + ReportParameters.kindOf(name).name().toLowerCase(Locale.ROOT))
        .collect(joining(","));
  }

  /**
   * The parameters in the order the palette asks for them: as the SQL names them, but the year right
   * behind the month. A month typed without its year brings the year along, and the year typed next
   * replaces it (#1493) — which works only where the year comes next.
   */
  static List<String> paletteOrder(List<String> names) {
    var month = names.stream().filter(name -> ReportParameters.kindOf(name) == Kind.MONTH).findFirst();
    var year = names.stream().filter(name -> ReportParameters.kindOf(name) == Kind.YEAR).findFirst();
    if (month.isEmpty() || year.isEmpty()) {
      return names;
    }
    var ordered = new ArrayList<>(names);
    ordered.remove(year.get());
    ordered.add(ordered.indexOf(month.get()) + 1, year.get());
    return ordered;
  }

  private static PaletteText parametersNote(List<String> parameters) {
    return parameters.isEmpty() ? null
        : PaletteText.of("main.palette.context.report.parameters", String.join(", ", parameters));
  }

  private PaletteHit hit(PaletteQuery query, ReportDefinition report) {
    var targets = new ArrayList<PaletteTarget>();
    targets.add(new PaletteTarget(PaletteText.of("main.palette.target.report.execute"),
        PaletteLink.to("/reporting/reports/execute").param("id", report.getId()).build(), PaletteTarget.OPEN));
    if (authorizedUser.isPeopleLead() && reportAuthorization.isAuthorized(report, WRITE)) {
      targets.add(new PaletteTarget(PaletteText.of("main.palette.target.report.edit"),
          PaletteLink.to("/reporting/reports/edit").param("id", report.getId()).build(), 1));
    }
    targets.add(new PaletteTarget(PaletteText.of("main.palette.target.report.list"),
        PaletteLink.to("/reporting/reports").param("fReportFilter", report.getName()).build(), 2));
    var context = parametersNote(ReportParameters.names(report.getSql()));
    return new PaletteHit(REPORT, String.valueOf(report.getId()), report.getName(), null, context,
        false, false, query.match(report.getName()), targets, encodedParameters(report));
  }
}
