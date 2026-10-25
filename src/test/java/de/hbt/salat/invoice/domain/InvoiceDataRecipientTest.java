package de.hbt.salat.invoice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.customer.domain.Customer;

/** Whom the invoice attachment goes to (#1536): the intermediary of a mediated order, naming the end customer. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class InvoiceDataRecipientTest {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

  @Test
  void a_mediated_order_goes_to_its_intermediary_and_names_the_end_customer() {
    var customer = customer("Kunde A AG");
    var intermediary = customer("Vermittlung B GmbH");

    var data = data(customer, intermediary);

    assertThat(data.getRecipient()).isSameAs(intermediary);
    assertThat(data.getEndCustomerName()).isEqualTo("Kunde A AG");
  }

  @Test
  void an_order_without_intermediary_goes_to_its_customer_without_an_end_customer() {
    var customer = customer("Kunde C GmbH");

    var data = data(customer, null);

    assertThat(data.getRecipient()).isSameAs(customer);
    assertThat(data.getEndCustomerName()).isEmpty();
  }

  private static InvoiceData data(Customer customer, Customer intermediary) {
    return new InvoiceData(null, new LocalDateRange(DAY, DAY), "ABC-12", customer, intermediary, Duration.ZERO,
        List.of(), InvoiceGrouping.SUBORDER, List.of(), List.of(), List.of());
  }

  private static Customer customer(String name) {
    var customer = new Customer();
    customer.setName(name);
    return customer;
  }
}
