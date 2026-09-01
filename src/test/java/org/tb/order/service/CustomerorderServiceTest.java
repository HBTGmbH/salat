package org.tb.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.tb.common.command.CommandPublisher;
import org.tb.customer.persistence.CustomerDAO;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.order.domain.Customerorder;
import org.tb.order.persistence.CustomerorderDAO;
import org.tb.order.persistence.CustomerorderRepository;

/**
 * Hidden customer orders must not be offered in a select box, but the one a record already
 * references has to stay in the list — otherwise that record cannot be edited any more (#895).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class CustomerorderServiceTest {

  private CustomerorderDAO customerorderDAO;
  private CustomerorderService customerorderService;

  @BeforeEach
  public void setUp() {
    customerorderDAO = mock(CustomerorderDAO.class);
    customerorderService = new CustomerorderService(
        mock(ApplicationEventPublisher.class),
        mock(CommandPublisher.class),
        customerorderDAO,
        mock(CustomerDAO.class),
        mock(EmployeeDAO.class),
        mock(CustomerorderRepository.class));
    when(customerorderDAO.getCustomerorders())
        .thenReturn(List.of(customerorder("visible", false), customerorder("hidden", true)));
  }

  @Test
  public void should_leave_out_hidden_customer_orders() {
    assertThat(signs(null)).containsExactly("visible");
  }

  @Test
  public void should_keep_a_hidden_customer_order_that_the_record_still_references() {
    assertThat(signs("hidden")).containsExactly("visible", "hidden");
  }

  @Test
  public void should_not_keep_a_hidden_customer_order_that_is_not_the_referenced_one() {
    assertThat(signs("visible")).containsExactly("visible");
  }

  private List<String> signs(String keep) {
    return customerorderService.getSelectableCustomerorders(keep).stream()
        .map(Customerorder::getSign)
        .toList();
  }

  private static Customerorder customerorder(String sign, boolean hide) {
    var customerorder = new Customerorder();
    customerorder.setSign(sign);
    customerorder.setHide(hide);
    return customerorder;
  }

}
