package de.hbt.salat.reporting.controller;

import static de.hbt.salat.auth.domain.AccessLevel.WRITE;
import static de.hbt.salat.common.palette.PaletteKind.REPORT;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.palette.PaletteHit;
import de.hbt.salat.common.palette.PaletteLink;
import de.hbt.salat.common.palette.PaletteProvider;
import de.hbt.salat.common.palette.PaletteQuery;
import de.hbt.salat.common.palette.PaletteTarget;
import de.hbt.salat.common.palette.PaletteText;
import de.hbt.salat.reporting.auth.ReportAuthorization;
import de.hbt.salat.reporting.domain.ReportDefinition;
import de.hbt.salat.reporting.service.ReportParameters;
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
    var parameters = ReportParameters.names(report.getSql());
    var context = parameters.isEmpty() ? null
        : PaletteText.of("main.palette.context.report.parameters", String.join(", ", parameters));
    return new PaletteHit(REPORT, String.valueOf(report.getId()), report.getName(), null, context,
        false, false, query.match(report.getName()), targets);
  }
}
