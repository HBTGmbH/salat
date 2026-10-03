package de.hbt.salat.etl.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AuthorizationObject;
import de.hbt.salat.auth.domain.AuthorizationObjectProvider;
import de.hbt.salat.auth.domain.ObjectJudgement;
import de.hbt.salat.etl.domain.ETLDefinitionOption;
import de.hbt.salat.etl.service.ETLService;

/**
 * What the rule editor may offer for the category {@code ETL} (#1074).
 *
 * <p>The id is the database id of the definition — exactly what {@link ETLAuthorization} compares against, and part
 * of {@link ETLDefinitionOption} for that reason. Not the name (#1204): a renamed definition would lose its rules, and
 * a new one under the old name would inherit them. Anything that is not a number cannot be such an id; an id nobody
 * knows any more is left to the default judgement, which calls it unknown — the definition was deleted since.
 *
 * <p>The list the service returns is the one the asking person may execute. That takes nothing away here: the editor
 * is open to the management alone, and {@code ETLAuthorization} lets a manager through for every definition.
 */
@Component
@RequiredArgsConstructor
public class ETLAuthorizationObjectProvider implements AuthorizationObjectProvider {

  private final ETLService etlService;

  @Override
  public String category() {
    return "ETL";
  }

  @Override
  public String labelKey() {
    return "main.auth.rule.category.etl";
  }

  @Override
  public String objectHintKey() {
    return "main.auth.rule.object.hint.etl";
  }

  @Override
  public List<AuthorizationObject> objects() {
    return etlService.getExecutableDefinitions().stream()
        .map(definition -> new AuthorizationObject(String.valueOf(definition.id()), definition.name()))
        .toList();
  }

  @Override
  public ObjectJudgement judge(String objectId) {
    if (objectId.isEmpty() || !objectId.chars().allMatch(Character::isDigit)) {
      return ObjectJudgement.MALFORMED;
    }
    return AuthorizationObjectProvider.super.judge(objectId);
  }

}
