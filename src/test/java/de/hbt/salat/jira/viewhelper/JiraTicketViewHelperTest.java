package de.hbt.salat.jira.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.jira.domain.JiraTicketDetail;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraTicketViewHelperTest {

  private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 6, 17, 40);

  private final JiraTicketViewHelper helper = new JiraTicketViewHelper();

  @Test
  void a_moment_is_date_and_time() {
    assertThat(helper.stamp(WHEN)).isEqualTo("06.10.2026 17:40");
    assertThat(helper.stamp(null)).isEqualTo("–");
  }

  @Test
  void an_import_names_its_file() {
    assertThat(helper.importStamp(detail("export.csv", WHEN))).isEqualTo("06.10.2026 17:40 · export.csv");
    assertThat(helper.importStamp(detail(null, WHEN))).isEqualTo("06.10.2026 17:40");
    assertThat(helper.importStamp(detail(null, null))).isEqualTo("–");
  }

  private static JiraTicketDetail detail(String file, LocalDateTime at) {
    return new JiraTicketDetail(null, null, List.of(), null, null, Map.of(), Map.of(), List.of(), file, at);
  }
}
