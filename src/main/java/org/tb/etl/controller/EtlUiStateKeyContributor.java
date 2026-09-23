package org.tb.etl.controller;

import static java.util.Map.of;

import java.util.Map;
import org.springframework.stereotype.Component;
import org.tb.common.web.UiStateKey;
import org.tb.common.web.UiStateKeyContributor;

@Component
public class EtlUiStateKeyContributor implements UiStateKeyContributor {

  public static final UiStateKey ETL_RUN_LIMIT = new UiStateKey("etl.Run.Limit");
  public static final UiStateKey ETL_RUN_FAILED_ONLY = new UiStateKey("etl.Run.FailedOnly");

  @Override
  public Map<String, UiStateKey> getParamToKeyMappings() {
    return of(
        "fEtlRunLimit", ETL_RUN_LIMIT,
        "fEtlRunFailedOnly", ETL_RUN_FAILED_ONLY
    );
  }
}
