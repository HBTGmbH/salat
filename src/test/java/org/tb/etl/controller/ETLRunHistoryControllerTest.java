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
  void the_controller_keeps_the_restricted_out_and_leaves_the_rest_to_the_service() {
    // Wer ausfuehren darf, darf auch die Laeufe sehen: Geschaeftsfuehrung oder eine Regel der
    // Kategorie ETL. Ein Entweder-oder traegt keine Annotation — es steht als Laufzeitpruefung im
    // Service. Was die Annotation hier leistet, ist die Grenze nach unten.
    var authorized = ETLRunHistoryController.class.getAnnotation(Authorized.class);

    assertThat(authorized).isNotNull();
    assertThat(authorized.requireUnrestricted()).isTrue();
    assertThat(authorized.requiresManager()).isFalse();
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
