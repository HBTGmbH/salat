package de.hbt.salat.achievement.controller;

import de.hbt.salat.common.web.UiStateKey;
import de.hbt.salat.common.web.UiStateKeyContributor;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The chosen order of the profile's reached achievements (#1553). */
@Component
public class AchievementUiStateKeyContributor implements UiStateKeyContributor {

  public static final UiStateKey ACHIEVEMENT_SORT = new UiStateKey("achievement.Sort");

  @Override
  public Map<String, UiStateKey> getParamToKeyMappings() {
    return Map.of("fAchievementSort", ACHIEVEMENT_SORT);
  }
}
