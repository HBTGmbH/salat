package org.tb.order.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.customer.domain.Customer;
import org.tb.order.domain.Customerorder;

/**
 * The label every order select shows underneath the order. It exists once because the same
 * concatenation with the same null guard would otherwise sit in eight templates (→ ADR-0017).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class CustomerorderViewHelperTest {

  private final CustomerorderViewHelper viewHelper = new CustomerorderViewHelper();

  @Test
  public void should_name_the_customer_short_name_first_then_the_full_name() {
    assertThat(viewHelper.customerLabel(order("HBT", "HBT Hamburger Berater Team GmbH")))
        .isEqualTo("HBT - HBT Hamburger Berater Team GmbH");
  }

  /**
   * {@code Customer#getShortname()} invents a short name from the name when none is stored — the
   * name itself for short ones, its first nine characters with an ellipsis for long ones. Prefixing
   * the name with that would print it twice.
   */
  @Test
  public void should_name_the_customer_once_when_the_short_name_comes_from_the_name() {
    assertThat(viewHelper.customerLabel(order(null, "Only Long"))).isEqualTo("Only Long");
    assertThat(viewHelper.customerLabel(order("  ", "Only Long"))).isEqualTo("Only Long");
    assertThat(viewHelper.customerLabel(order(null, "A very long customer name")))
        .isEqualTo("A very long customer name");
  }

  @Test
  public void should_fall_back_to_the_short_name_without_a_full_name() {
    assertThat(viewHelper.customerLabel(order("SHORT", null))).isEqualTo("SHORT");
  }

  /** No customer, no second line — the attribute is then left out of the option entirely. */
  @Test
  public void should_report_no_label_without_a_customer() {
    assertThat(viewHelper.customerLabel(new Customerorder())).isNull();
    assertThat(viewHelper.customerLabel(null)).isNull();
  }

  private static Customerorder order(String shortname, String name) {
    var customer = new Customer();
    customer.setShortname(shortname);
    customer.setName(name);
    var customerorder = new Customerorder();
    customerorder.setCustomer(customer);
    return customerorder;
  }

}
