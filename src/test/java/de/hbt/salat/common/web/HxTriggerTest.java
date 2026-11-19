package de.hbt.salat.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(ReplaceUnderscores.class)
class HxTriggerTest {

  private static final String MESSAGE = "Für \"Wartung\" ist C:\\temp ungültig – <b>nicht</b> buchbar";

  @Test
  void the_header_carries_only_ascii() {
    var response = new MockHttpServletResponse();

    HxTrigger.showError(response, MESSAGE);

    assertThat(response.getHeader("HX-Trigger")).isNotNull().matches("\\p{ASCII}*");
  }

  @Test
  void the_header_reads_back_as_the_message() {
    var response = new MockHttpServletResponse();

    HxTrigger.showError(response, MESSAGE);

    Map<String, String> trigger = JsonMapper.shared().readValue(response.getHeader("HX-Trigger"), new TypeReference<>() {
    });
    assertThat(trigger).containsExactly(Map.entry("showError", MESSAGE));
  }
}
