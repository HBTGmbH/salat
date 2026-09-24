package org.tb.etl.auth;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AuthorizationObject;
import org.tb.auth.domain.AuthorizationObjectProvider;
import org.tb.etl.domain.ETLDefinitionOption;
import org.tb.etl.service.ETLService;

/**
 * What the rule editor may offer for the category {@code ETL} (#1074).
 *
 * <p>The id is the name of the definition — exactly what {@link ETLAuthorization} compares against, which is what
 * {@link ETLDefinitionOption} was cut for. A name has no format to get wrong, so nothing here is ever malformed; a
 * name nobody knows is left to the default judgement, which calls it unknown and lets it be saved. A rule may well
 * precede the definition it is about.
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
        .map(definition -> new AuthorizationObject(definition.name(), definition.name()))
        .toList();
  }

}
