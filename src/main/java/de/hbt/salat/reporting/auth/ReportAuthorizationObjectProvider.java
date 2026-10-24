package de.hbt.salat.reporting.auth;

import static java.lang.String.valueOf;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.reporting.service.ReportService;

/**
 * What the rule editor may offer for the category {@code REPORT_DEFINITION} (#1074).
 *
 * <p>The id is the database id of the report, because that is what {@link ReportAuthorization} compares against — the
 * name would read better in a rule but would not match. Hence also the one format rule here: anything that is not a
 * number cannot be a report id, whatever the data says.
 *
 * <p>The list the service returns is the one the asking person may execute. That takes nothing away here: the editor
 * is open to the management alone, and {@code ReportAuthorization} lets a manager through for every report.
 *
 * <p>Each report comes with its group (#1523), in the order of the report list: titles repeat across groups, and the
 * editor lists them under it.
 */
@Component
@RequiredArgsConstructor
public class ReportAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final ReportService reportService;
  private final MessageSourceAccessor messages;

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
    var withoutGroup = messages.getMessage("main.reporting.group.none");
    return reportService.getGroupedReportDefinitions().stream()
        .map(candidate -> new AuthorizationObject(valueOf(candidate.report().getId()), candidate.report().getName(), null,
            candidate.group().isNone() ? withoutGroup : candidate.group().name()))
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
