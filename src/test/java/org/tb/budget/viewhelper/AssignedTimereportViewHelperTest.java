package org.tb.budget.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.dailyreport.domain.TimereportDTO;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class AssignedTimereportViewHelperTest {

  private static final LocalDate JAN_01 = LocalDate.of(2026, 1, 1);

  @Test
  public void should_list_the_youngest_booking_first() {
    var rows = AssignedTimereportViewHelper.newestFirst(List.of(
        report(1, JAN_01, "abc"),
        report(2, JAN_01.plusDays(10), "abc"),
        report(3, JAN_01.plusDays(5), "abc")), 10);

    assertThat(rows).extracting(AssignedTimereportViewHelper::day)
        .containsExactly("11.01.2026", "06.01.2026", "01.01.2026");
  }

  /** Same day, same order every time — otherwise the list reshuffles itself between two reloads. */
  @Test
  public void should_order_bookings_of_the_same_day_by_employee_then_suborder_then_id() {
    var rows = AssignedTimereportViewHelper.newestFirst(List.of(
        report(9, JAN_01, "def", "CO/02"),
        report(8, JAN_01, "abc", "CO/02"),
        report(7, JAN_01, "abc", "CO/01"),
        report(6, JAN_01, "abc", "CO/01")), 10);

    assertThat(rows).extracting(AssignedTimereportViewHelper::id).containsExactly(6L, 7L, 8L, 9L);
  }

  /**
   * The point of #997: the bookings arrive ordered by employee sign, so capping them unsorted showed
   * every booking of the alphabetically first person and none of anybody else.
   */
  @Test
  public void should_sort_before_the_cap_so_that_the_cap_keeps_the_youngest_bookings() {
    var reports = new ArrayList<TimereportDTO>();
    // The order the DAO delivers: employee sign first, then date. "abc" booked long ago and often,
    // "xyz" booked yesterday — and would be cut off entirely by an unsorted cap.
    for (int i = 0; i < 200; i++) {
      reports.add(report(100 + i, JAN_01.plusDays(i), "abc"));
    }
    reports.add(report(999, JAN_01.plusDays(300), "xyz"));

    var rows = AssignedTimereportViewHelper.newestFirst(reports, 200);

    assertThat(rows).hasSize(200);
    assertThat(rows.get(0).id()).isEqualTo(999L);
    assertThat(rows).extracting(AssignedTimereportViewHelper::employeeSign).contains("xyz");
    // The oldest booking is the one that fell off, not the one of the person late in the alphabet.
    assertThat(rows).extracting(AssignedTimereportViewHelper::id).doesNotContain(100L);
  }

  @Test
  public void should_return_every_booking_when_there_are_fewer_than_the_cap_allows() {
    var rows = AssignedTimereportViewHelper.newestFirst(List.of(
        report(1, JAN_01, "abc"),
        report(2, JAN_01.plusDays(1), "abc")), 200);

    assertThat(rows).hasSize(2);
  }

  @Test
  public void should_format_the_columns_of_a_booking() {
    var rows = AssignedTimereportViewHelper.newestFirst(List.of(report(1, JAN_01, "abc")), 10);

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row.day()).isEqualTo("01.01.2026");
      assertThat(row.suborderSign()).isEqualTo("CO/01");
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.duration()).isEqualTo("2:30");
      assertThat(row.taskDescription()).isEqualTo("task");
    });
  }

  private static TimereportDTO report(long id, LocalDate day, String employeeSign) {
    return report(id, day, employeeSign, "CO/01");
  }

  private static TimereportDTO report(long id, LocalDate day, String employeeSign, String suborderSign) {
    return TimereportDTO.builder()
        .id(id)
        .referenceday(day)
        .employeeSign(employeeSign)
        .completeOrderSign(suborderSign)
        .duration(Duration.ofMinutes(150))
        .taskdescription("task")
        .build();
  }

}
