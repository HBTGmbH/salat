package de.hbt.salat.customer.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.customer.domain.Customer;

/** Wie ein Kunde in jeder Auswahl heißt (#1266): {@code Kurzname - Name}, der Name nie doppelt. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CustomerLabelViewHelperTest {

  private final CustomerLabelViewHelper viewHelper = new CustomerLabelViewHelper();

  @Test
  void should_name_the_short_name_first_then_the_name() {
    assertThat(viewHelper.label(customer("ACME", "Acme Corporation"))).isEqualTo("ACME - Acme Corporation");
  }

  /**
   * {@code Customer#getShortname()} erfindet ohne eigenen Kurznamen einen aus dem Namen. Davor
   * gesetzt stünde der Name zweimal da.
   */
  @Test
  void should_name_the_customer_once_without_a_short_name_of_its_own() {
    assertThat(viewHelper.label(customer(null, "Acme"))).isEqualTo("Acme");
    assertThat(viewHelper.label(customer("", "Acme Corporation Ltd"))).isEqualTo("Acme Corporation Ltd");
  }

  @Test
  void should_name_a_record_with_both_names_the_same_way() {
    assertThat(viewHelper.label("ACME", "Acme Corporation")).isEqualTo("ACME - Acme Corporation");
    assertThat(viewHelper.label(null, "Acme Corporation")).isEqualTo("Acme Corporation");
  }

  @Test
  void should_fall_back_to_the_short_name_without_a_name() {
    assertThat(viewHelper.label("ACME", null)).isEqualTo("ACME");
  }

  private static Customer customer(String shortname, String name) {
    var customer = new Customer();
    customer.setShortname(shortname);
    customer.setName(name);
    return customer;
  }

}
