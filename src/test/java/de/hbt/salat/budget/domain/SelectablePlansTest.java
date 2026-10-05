package de.hbt.salat.budget.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** The select of budget plans names each plan's scope by the sign it has today, read by id (#1212). */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class SelectablePlansTest {

  @Test
  public void names_each_plan_by_the_sign_its_scope_has_today() {
    var orderWide = plan(1L, null);
    var onSuborder = plan(2L, 10L);

    var selectable = SelectablePlans.of(List.of(orderWide, onSuborder), List.of(), null)
        .withScopeSigns("co", Map.of(10L, "co/01"));

    assertThat(selectable.scopeSigns()).containsOnly(entry(1L, "co"), entry(2L, "co/01"));
  }

  @Test
  public void asks_for_the_signs_of_the_suborders_it_offers() {
    var selectable = SelectablePlans.of(List.of(plan(1L, null), plan(2L, 10L), plan(3L, 10L)), List.of(), null);

    assertThat(selectable.suborderIds()).containsExactly(10L);
  }

  private static OrderBudget plan(long id, Long suborderId) {
    var plan = new OrderBudget();
    ReflectionTestUtils.setField(plan, "id", id);
    plan.setCustomerorderId(7L);
    plan.setSuborderId(suborderId);
    return plan;
  }
}
