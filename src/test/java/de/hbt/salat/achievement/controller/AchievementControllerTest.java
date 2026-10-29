package de.hbt.salat.achievement.controller;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.hbt.salat.achievement.domain.AchievementProfile;
import de.hbt.salat.achievement.service.AchievementService;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.filter.UiStateFilter;
import de.hbt.salat.common.web.LoginSignProvider;
import de.hbt.salat.common.web.UiState;
import de.hbt.salat.common.web.UiStateKeyRegistry;
import jakarta.servlet.http.Cookie;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** The profile's sort choice, including the real UiState cookie and request precedence (#1553). */
class AchievementControllerTest {

  private final AchievementService service = mock(AchievementService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    var login = mock(LoginSignProvider.class);
    when(login.getEffectiveLoginSign()).thenReturn("tst");
    var filter = new UiStateFilter(new UiState(),
        new UiStateKeyRegistry(List.of(new AchievementUiStateKeyContributor())), login, new SalatProperties());
    mvc = MockMvcBuilders.standaloneSetup(new AchievementController(service)).addFilters(filter).build();
    when(service.getProfileOfCurrentPerson(any())).thenReturn(new AchievementProfile(List.of(), List.of()));
  }

  @Test
  void date_is_the_default_and_opening_the_profile_still_marks_achievements_seen() throws Exception {
    mvc.perform(get("/profile")).andExpect(status().isOk()).andExpect(model().attribute("achievementSort", "date"));

    verify(service).markSeenForCurrentPerson();
  }

  @ParameterizedTest
  @ValueSource(strings = {"date", "", "unknown"})
  void an_explicit_default_empty_or_unknown_value_shows_the_date_order(String value) throws Exception {
    mvc.perform(get("/profile").param("fAchievementSort", value).cookie(remembered("type")))
        .andExpect(status().isOk()).andExpect(model().attribute("achievementSort", "date"));
  }

  @Test
  void the_type_order_is_selected_explicitly() throws Exception {
    var response = mvc.perform(get("/profile").param("fAchievementSort", "type"))
        .andExpect(status().isOk()).andExpect(model().attribute("achievementSort", "type"))
        .andReturn().getResponse();

    org.assertj.core.api.Assertions.assertThat(new String(Base64.getUrlDecoder().decode(
        response.getCookie("salat_uistate").getValue()), UTF_8)).contains("achievement.Sort=type");
  }

  @Test
  void the_remembered_choice_is_used_without_a_parameter() throws Exception {
    mvc.perform(get("/profile").cookie(remembered("type")))
        .andExpect(status().isOk()).andExpect(model().attribute("achievementSort", "type"));
  }

  @Test
  void an_explicit_choice_overrides_the_cookie() throws Exception {
    mvc.perform(get("/profile").param("fAchievementSort", "type").cookie(remembered("date")))
        .andExpect(status().isOk()).andExpect(model().attribute("achievementSort", "type"));
  }

  private static Cookie remembered(String value) {
    return new Cookie("salat_uistate", Base64.getUrlEncoder().withoutPadding()
        .encodeToString(("_ls=tst&achievement.Sort=" + value).getBytes(UTF_8)));
  }
}
