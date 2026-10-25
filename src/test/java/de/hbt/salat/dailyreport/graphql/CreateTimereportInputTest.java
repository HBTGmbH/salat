package de.hbt.salat.dailyreport.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.dailyreport.graphql.TimereportResolver.CreateTimereportInput;

/** What makes two calls the same booking for the idempotency key (#1532). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CreateTimereportInputTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 26);

  @Test
  void no_ticket_references_and_an_empty_list_are_the_same_call() {
    var omitted = new CreateTimereportInput(7L, DAY, Duration.ofHours(1), "Wartung", null);
    var empty = new CreateTimereportInput(7L, DAY, Duration.ofHours(1), "Wartung", List.of());

    assertThat(omitted.canonical()).isEqualTo(empty.canonical());
    assertThat(omitted.canonical().toString()).isEqualTo(empty.canonical().toString());
  }

  @Test
  void another_duration_is_another_call() {
    var first = new CreateTimereportInput(7L, DAY, Duration.ofHours(1), "Wartung", null);
    var second = new CreateTimereportInput(7L, DAY, Duration.ofHours(2), "Wartung", null);

    assertThat(first.canonical().toString()).isNotEqualTo(second.canonical().toString());
  }
}
