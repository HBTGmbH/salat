package org.tb.etl.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.auth.domain.Authorized;

/**
 * Die Anzeige der ETL-Läufe (#573).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ETLRunHistoryControllerTest {

  @Test
  void the_whole_controller_is_management_only() {
    // Die Meldung eines Laufs traegt den inneren Aufbau der Auswertungen — wie bei den
    // JIRA-Replikationen daneben reicht es nicht, den Menueeintrag auszublenden.
    var authorized = ETLRunHistoryController.class.getAnnotation(Authorized.class);

    assertThat(authorized).isNotNull();
    assertThat(authorized.requiresManager()).isTrue();
  }

  @Test
  void without_a_choice_the_list_shows_the_last_hundred_runs() {
    assertThat(ETLRunHistoryController.limitOf(null)).isEqualTo(100);
  }

  @Test
  void a_chosen_number_is_kept() {
    assertThat(ETLRunHistoryController.limitOf(250)).isEqualTo(250);
  }

  @Test
  void a_number_out_of_range_is_pulled_back_into_it() {
    // Das Feld kommt aus der URL: null Zeilen waeren eine leere Seite ohne Grund, und jede Zeile
    // traegt die Meldung ihres Laufs.
    assertThat(ETLRunHistoryController.limitOf(0)).isEqualTo(1);
    assertThat(ETLRunHistoryController.limitOf(-5)).isEqualTo(1);
    assertThat(ETLRunHistoryController.limitOf(5000)).isEqualTo(1000);
  }

}
