package de.hbt.salat.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.web.ListLimits.ListLimit;

/** The steps every list with a limit offers (#1380), read from a request parameter. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ListLimitsTest {

  private final ListLimits listLimits = new ListLimits(new SalatProperties());

  @ParameterizedTest
  @ValueSource(strings = {"50", "100", "500", "1000", " 100 "})
  void a_step_limits_to_itself(String value) {
    var step = Integer.parseInt(value.trim());

    assertThat(listLimits.parse(value)).isEqualTo(new ListLimit(step, step));
  }

  /** "Alle" is the whole filter, but not every row of it on one page (#1153). */
  @Test
  void all_is_capped_by_the_configuration() {
    var properties = new SalatProperties();
    properties.getBookingList().setAllMaxRows(250);

    assertThat(new ListLimits(properties).parse("0")).isEqualTo(new ListLimit(0, 250));
    assertThat(listLimits.parse("0")).isEqualTo(new ListLimit(0, 10_000));
  }

  /** A hand-written URL or an old remembered value falls back to the default rather than to anything it says. */
  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "7", "-1", "100000", "all", "1e3"})
  void anything_else_is_the_default(String value) {
    assertThat(listLimits.parse(value)).isEqualTo(new ListLimit(500, 500));
  }
}
