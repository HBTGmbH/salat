package org.tb.dailyreport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.dailyreport.controller.TimereportController.trainingDefaultOf;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The default state of the training switch derived from the preselected suborder (#836).
 */
class TimereportControllerTest {

  private static final SuborderOption TRAINING =
      new SuborderOption(1L, "FORTBILDUNG", "HBT", false, true);
  private static final SuborderOption PROJECT =
      new SuborderOption(2L, "ALPHA-DEV", "Contoso", false, false);

  @Test
  void suborder_with_the_training_flag_preselects_the_switch() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 1L)).isTrue();
  }

  @Test
  void suborder_without_the_training_flag_leaves_the_switch_off() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 2L)).isFalse();
  }

  @Test
  void nothing_preselected_leaves_the_switch_off() {
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), null)).isFalse();
  }

  @Test
  void a_suborder_outside_the_offered_options_leaves_the_switch_off() {
    // the deeplink parameter suborderId is not validated against the employee's orders here
    assertThat(trainingDefaultOf(List.of(PROJECT, TRAINING), 99L)).isFalse();
  }

}
