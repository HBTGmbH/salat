package org.tb.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.customer.domain.Customer;
import org.tb.customer.domain.CustomerDTO;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.customer.persistence.CustomerRepository;

/**
 * Die Testdaten sind so gewählt, dass eine Sortierung nach {@code name} oder eine
 * Sortierung mit Beachtung der Groß-/Kleinschreibung eine andere Reihenfolge ergibt
 * als die erwartete Sortierung nach Kurzname ohne Beachtung der Groß-/Kleinschreibung.
 */
@DataJpaTest
@Import({
    AuthorizedUserAuditorAware.class,
    CustomerDAO.class,
    CustomerService.class
})
@DisplayNameGeneration(ReplaceUnderscores.class)
public class CustomerServiceTest {

  @Autowired
  private CustomerService customerService;

  @Autowired
  private CustomerRepository customerRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  public void initAuthorizedUser() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.isManager()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  public void customers_are_sorted_by_shortname_ignoring_case() {
    createCustomer("charlie", "Xena GmbH", false);
    createCustomer("Beta", "Yota GmbH", false);
    createCustomer("alpha", "Zeta GmbH", false);

    assertThat(shortNames(false)).containsExactly("alpha", "Beta", "charlie");
  }

  @Test
  public void hidden_customers_are_sorted_by_shortname_ignoring_case_as_well() {
    createCustomer("charlie", "Xena GmbH", false);
    createCustomer("Beta", "Yota GmbH", true);
    createCustomer("alpha", "Zeta GmbH", false);

    assertThat(shortNames(true)).containsExactly("alpha", "Beta", "charlie");
  }

  @Test
  public void hidden_customers_are_excluded_unless_requested() {
    createCustomer("visible", "Visible GmbH", false);
    createCustomer("Hidden", "Hidden GmbH", true);

    assertThat(shortNames(false)).containsExactly("visible");
  }

  @Test
  public void filtered_customers_are_sorted_by_shortname_ignoring_case() {
    createCustomer("ACME-z", "Alpha GmbH", false);
    createCustomer("acme-a", "Zeta GmbH", false);
    createCustomer("other", "Other GmbH", false);

    var shortNames = customerService.getAllCustomerDTOsByFilter("acme", false)
        .stream().map(CustomerDTO::getShortName).toList();

    assertThat(shortNames).containsExactly("acme-a", "ACME-z");
  }

  /**
   * Ein ausgeblendeter Auftraggeber fällt aus den Auswahllisten — nur der, den ein Datensatz bereits
   * trägt, bleibt drin. Ohne ihn kann die Auswahl ihren gespeicherten Wert nicht markieren und der
   * Browser zeigt den alphabetisch ersten Auftraggeber an, also einen fremden (#1005).
   */
  @Test
  public void hidden_customers_are_left_out_of_the_selection() {
    createCustomer("visible", "Visible GmbH", false);
    createCustomer("Hidden", "Hidden GmbH", true);

    assertThat(selectableShortNames(null)).containsExactly("visible");
  }

  @Test
  public void the_stored_customer_stays_in_the_selection_even_when_hidden() {
    createCustomer("visible", "Visible GmbH", false);
    var hidden = createCustomer("Hidden", "Hidden GmbH", true);

    assertThat(selectableShortNames(hidden.getId())).containsExactly("Hidden", "visible");
  }

  @Test
  public void another_hidden_customer_stays_out_of_the_selection() {
    var hidden = createCustomer("Hidden", "Hidden GmbH", true);
    createCustomer("alsoHidden", "Also Hidden GmbH", true);

    assertThat(selectableShortNames(hidden.getId())).containsExactly("Hidden");
  }

  private List<String> selectableShortNames(Long keepId) {
    return customerService.getSelectableCustomers(keepId)
        .stream().map(Customer::getShortname).toList();
  }

  private List<String> shortNames(boolean showHidden) {
    return customerService.getAllCustomerDTOsByFilter(null, showHidden)
        .stream().map(CustomerDTO::getShortName).toList();
  }

  private Customer createCustomer(String shortname, String name, boolean hide) {
    Customer customer = new Customer();
    customer.setShortname(shortname);
    customer.setName(name);
    customer.setAddress(name + " Street 1");
    customer.setHide(hide);
    return customerRepository.save(customer);
  }

}
