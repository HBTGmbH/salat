package de.hbt.salat.order.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;

/**
 * How every order select names an order and, underneath, its customer (#1266, → ADR-0017).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class CustomerorderViewHelperTest {

  private final CustomerorderViewHelper viewHelper = new CustomerorderViewHelper();

  @Test
  public void should_name_the_order_by_sign_and_short_description() {
    var customerorder = order("ACME", "Acme Corporation");
    customerorder.setSign("4711");
    customerorder.setShortdescription("Plattform");
    assertThat(viewHelper.label(customerorder)).isEqualTo("4711 - Plattform");
  }

  @Test
  public void should_stand_on_the_sign_alone_without_a_description() {
    var customerorder = order("ACME", "Acme Corporation");
    customerorder.setSign("4711");
    assertThat(viewHelper.label(customerorder)).isEqualTo("4711");
  }

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
    assertThat(viewHelper.customerLabel((Customerorder) null)).isNull();
  }

  // --- an order read as option (#1283) ---------------------------------------------------------

  @Test
  public void should_name_an_option_like_the_order() {
    assertThat(viewHelper.label(option(null, null, "BSP", "Beispielkunde"))).isEqualTo("4711");
    assertThat(viewHelper.label(option("Plattform", "Plattform und Betrieb", "BSP", "Beispielkunde")))
        .isEqualTo("4711 - Plattform");
  }

  /** The option carries the stored short name, not the one {@code Customer#getShortname()} makes up. */
  @Test
  public void should_name_the_customer_of_an_option_like_that_of_the_order() {
    assertThat(viewHelper.customerLabel(option(null, null, "BSP", "Beispielkunde"))).isEqualTo("BSP - Beispielkunde");
    assertThat(viewHelper.customerLabel(option(null, null, null, "A very long customer name")))
        .isEqualTo("A very long customer name");
  }

  /** An order without a customer — the query joins the customer optionally. */
  @Test
  public void should_report_no_label_for_an_option_without_a_customer() {
    assertThat(viewHelper.customerLabel(option(null, null, null, null))).isNull();
    assertThat(viewHelper.customerLabel((CustomerorderOption) null)).isNull();
  }

  /**
   * Without a short description the entity answers the description, cut to twenty characters — the
   * option, read from the bare columns, must show the same (#1283).
   */
  @Test
  public void should_name_an_option_without_short_description_by_its_description_as_the_order_does() {
    var order = order("BSP", "Beispielkunde");
    order.setSign("4711");
    order.setDescription("Betrieb und Weiterentwicklung");

    assertThat(viewHelper.label(option(null, "Betrieb und Weiterentwicklung", "BSP", "Beispielkunde")))
        .isEqualTo(viewHelper.label(order))
        .isEqualTo("4711 - Betrieb und Weite...");
    assertThat(viewHelper.label(option("", "Wartung", "BSP", "Beispielkunde"))).isEqualTo("4711 - Wartung");
  }

  private static CustomerorderOption option(String shortdescription, String description, String customerShortname,
      String customerName) {
    return new CustomerorderOption(1L, "4711", shortdescription, description, customerShortname, customerName, false);
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
