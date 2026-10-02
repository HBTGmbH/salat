package de.hbt.salat.jira.controller;

import static java.util.Map.of;

import java.util.Map;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;

@Component
public class JiraUiStateKeyContributor implements UiStateKeyContributor {

  public static final UiStateKey JIRA_RUN_FAILED_ONLY = new UiStateKey("jira.Run.FailedOnly");

  @Override
  public Map<String, UiStateKey> getParamToKeyMappings() {
    return of("fJiraRunFailedOnly", JIRA_RUN_FAILED_ONLY);
  }
}
