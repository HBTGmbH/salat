package de.hbt.salat.budget.domain;

import static de.hbt.salat.testutils.ReferenceTestUtils.suborderWithId;
import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The select of budget plans names each plan's scope by the sign it has today (#1212), read through
 * the plan's references (#1367).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class SelectablePlansTest {

  @Test
  public void names_each_plan_by_the_sign_its_scope_has_today() {
    var orderWide = plan(1L, null);
    var onSuborder = plan(2L, 10L);

    var selectable = SelectablePlans.of(List.of(orderWide, onSuborder), List.of(), null)
        .withScopeSigns();

    assertThat(selectable.scopeSigns()).containsOnly(entry(1L, "co"), entry(2L, "co/01"));
  }

  private static OrderBudget plan(long id, Long suborderId) {
    var plan = new OrderBudget();
    ReflectionTestUtils.setField(plan, "id", id);
    var customerorder = customerorderWithId(7L);
    customerorder.setSign("co");
    plan.setCustomerorder(customerorder);
    var suborder = suborderWithId(suborderId);
    if (suborder != null) {
      ReflectionTestUtils.setField(suborder, "completeOrderSign", "co/01");
    }
    plan.setSuborder(suborder);
    return plan;
  }
}
