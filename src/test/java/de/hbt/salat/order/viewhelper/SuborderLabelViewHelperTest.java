package de.hbt.salat.order.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * Wie ein Unterauftrag in jeder Auswahl heißt (#1266): {@code Auftrag/Unterauftrag -
 * Kurzbeschreibung}, darunter {@code Auftrag-Kurzbeschreibung · Kunde}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SuborderLabelViewHelperTest {

  private final SuborderLabelViewHelper viewHelper = new SuborderLabelViewHelper();

  @Test
  void should_name_the_complete_sign_then_the_short_description() {
    assertThat(viewHelper.label(suborder("Entwicklung"))).isEqualTo("4711/01 - Entwicklung");
  }

  @Test
  void should_stand_on_the_sign_alone_without_a_description() {
    assertThat(viewHelper.label(suborder(null))).isEqualTo("4711/01");
    assertThat(SuborderLabelViewHelper.of("4711/01", " ")).isEqualTo("4711/01");
  }

  @Test
  void should_name_order_and_customer_underneath() {
    assertThat(viewHelper.subtext(suborder("Entwicklung"))).isEqualTo("Plattform · ACME - Acme Corporation");
  }

  @Test
  void should_leave_out_what_is_not_there() {
    assertThat(SuborderLabelViewHelper.subtextOf(null, "ACME - Acme Corporation")).isEqualTo("ACME - Acme Corporation");
    assertThat(SuborderLabelViewHelper.subtextOf("Plattform", null)).isEqualTo("Plattform");
    assertThat(SuborderLabelViewHelper.subtextOf("", null)).isNull();
  }

  private static Suborder suborder(String shortdescription) {
    var customer = new Customer();
    customer.setShortname("ACME");
    customer.setName("Acme Corporation");
    var customerorder = new Customerorder();
    customerorder.setSign("4711");
    customerorder.setShortdescription("Plattform");
    customerorder.setCustomer(customer);
    var suborder = new Suborder();
    suborder.setSign("01");
    suborder.setShortdescription(shortdescription);
    suborder.setDescription(shortdescription);
    suborder.setCustomerorder(customerorder);
    return suborder;
  }

}
