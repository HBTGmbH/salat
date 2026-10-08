package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;

/** The progress entries of a plan as its page lists them (#1435). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderBudgetScopeEntriesTest {

  @Test
  void lists_the_progress_entries_by_date_whatever_order_they_were_made_in() {
    var plan = new OrderBudget();
    entry(plan, 1L, LocalDate.of(2026, 10, 8), 23);
    entry(plan, 2L, LocalDate.of(2026, 10, 1), 10);
    entry(plan, 3L, LocalDate.of(2026, 11, 2), 40);

    assertThat(plan.getScopeEntriesByDate())
        .extracting(OrderBudgetScopeEntry::getPercent)
        .containsExactly(10, 23, 40);
  }

  /** Two entries of one day keep the order they were made in. */
  @Test
  void lists_two_entries_of_one_day_in_the_order_they_were_made() {
    var plan = new OrderBudget();
    entry(plan, 5L, LocalDate.of(2026, 10, 1), 15);
    entry(plan, 4L, LocalDate.of(2026, 10, 1), 10);

    assertThat(plan.getScopeEntriesByDate())
        .extracting(OrderBudgetScopeEntry::getPercent)
        .containsExactly(10, 15);
  }

  private static void entry(OrderBudget plan, long id, LocalDate refdate, int percent) {
    var entry = new OrderBudgetScopeEntry();
    setId(entry, id);
    entry.setOrderBudget(plan);
    entry.setRefdate(refdate);
    entry.setPercent(percent);
    plan.getScopeEntries().add(entry);
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
