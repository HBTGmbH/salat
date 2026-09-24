package org.tb.reporting.auth;

import static java.lang.String.valueOf;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.auth.domain.ObjectJudgement;
import org.tb.reporting.service.ReportService;

/**
 * What the rule editor may offer for the category {@code REPORT_DEFINITION} (#1074).
 *
 * <p>The id is the database id of the report, because that is what {@link ReportAuthorization} compares against — the
 * name would read better in a rule but would not match. Hence also the one format rule here: anything that is not a
 * number cannot be a report id, whatever the data says.
 *
 * <p>The list the service returns is the one the asking person may execute. That takes nothing away here: the editor
 * is open to the management alone, and {@code ReportAuthorization} lets a manager through for every report.
 */
@Component
@RequiredArgsConstructor
public class ReportAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final ReportService reportService;

  @Override
  public String category() {
    return "REPORT_DEFINITION";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.reportdefinition";
  }

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.reportdefinition";
  }

  @Override
  public List<AuthorizationObject> objects() {
    return reportService.getReportDefinitions().stream()
        .map(report -> new AuthorizationObject(valueOf(report.getId()), report.getName()))
        .toList();
  }

  @Override
  public ObjectJudgement judge(String objectId) {
    if (!objectId.chars().allMatch(Character::isDigit)) {
      return ObjectJudgement.MALFORMED;
    }
    return AuthorizationObjectProvider.super.judge(objectId);
  }

}
