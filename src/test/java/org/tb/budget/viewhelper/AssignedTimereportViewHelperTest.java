package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.AssignedBooking;

/**
 * Formatting only. The order of the rows comes from the query (#997,
 * {@code AssignedBookingRepositoryTest}) and is deliberately not re-established here — hence no
 * sorting test in this class.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class AssignedTimereportViewHelperTest {

  private static final LocalDate JAN_01 = LocalDate.of(2026, 1, 1);

  @Test
  public void formats_the_columns_of_a_booking() {
    var rows = AssignedTimereportViewHelper.from(List.of(booking(1, JAN_01)));

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.id()).isEqualTo(1L);
      assertThat(row.day()).isEqualTo("01.01.2026");
      assertThat(row.suborderSign()).isEqualTo("CO/01");
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.duration()).isEqualTo("2:30");
      assertThat(row.taskDescription()).isEqualTo("task");
    });
  }

  /** The rows are handed on in the order they arrived — no second opinion about it. */
  @Test
  public void keeps_the_order_it_was_given() {
    var rows = AssignedTimereportViewHelper.from(List.of(
        booking(1, JAN_01.plusDays(5)),
        booking(2, JAN_01),
        booking(3, JAN_01.plusDays(2))));

    assertThat(rows).extracting(AssignedTimereportViewHelper::id).containsExactly(1L, 2L, 3L);
  }

  @Test
  public void formats_an_empty_list_as_an_empty_list() {
    assertThat(AssignedTimereportViewHelper.from(List.<AssignedBooking>of())).isEmpty();
  }

  private static AssignedBooking booking(long id, LocalDate day) {
    return new AssignedBooking(id, day, 7L, "CO/01", "abc", "Abc Person",
        Duration.ofMinutes(150), "task");
  }

}
