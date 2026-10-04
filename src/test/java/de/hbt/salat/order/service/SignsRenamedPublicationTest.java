package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * Saving a renamed order, or a renamed or moved suborder, announces the new complete sign (#1206);
 * what the listeners could not follow by themselves comes back to the caller as notices.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SignsRenamedPublicationTest {

  private static final LocalDate FROM = LocalDate.parse("2026-01-01");

  private final List<Object> published = new ArrayList<>();
  private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
  private final SuborderDAO suborderDAO = mock(SuborderDAO.class);
  private final CustomerorderDAO customerorderDAO = mock(CustomerorderDAO.class);
  private final CustomerorderService customerorderService = mock(CustomerorderService.class);

  private Customerorder order;
  private Customerorder otherOrder;
  private Suborder first;
  private Suborder second;

  @BeforeEach
  void setUp() {
    doAnswer(invocation -> published.add(invocation.getArgument(0))).when(eventPublisher).publishEvent(any(Object.class));
    order = customerorder(7L, "CO");
    otherOrder = customerorder(8L, "OTHER");
    first = suborder(10L, order, "01");
    second = suborder(11L, order, "02");
    when(customerorderService.getCustomerorderById(7L)).thenReturn(order);
    when(customerorderService.getCustomerorderById(8L)).thenReturn(otherOrder);
    when(suborderDAO.getSuborderById(10L)).thenReturn(first);
    when(suborderDAO.getSuborderById(11L)).thenReturn(second);
    when(customerorderDAO.getCustomerorderById(7L)).thenReturn(order);
  }

  @Test
  void a_renamed_suborder_announces_its_old_and_new_complete_sign() {
    suborderService().update(11L, suborderDto("X2", null), 7L);

    assertThat(renames()).singleElement().satisfies(event -> {
      assertThat(event.getOldSign()).isEqualTo("CO/02");
      assertThat(event.getNewSign()).isEqualTo("CO/X2");
      assertThat(event.isMovedToAnotherOrder()).isFalse();
    });
  }

  @Test
  void a_suborder_moved_below_another_one_announces_its_new_place() {
    suborderService().update(11L, suborderDto("02", 10L), 7L);

    assertThat(renames()).singleElement().satisfies(event -> {
      assertThat(event.getOldSign()).isEqualTo("CO/02");
      assertThat(event.getNewSign()).isEqualTo("CO/01/02");
    });
  }

  @Test
  void a_suborder_moved_to_another_order_says_so() {
    suborderService().update(11L, suborderDto("02", null), 8L);

    assertThat(renames()).singleElement().satisfies(event -> {
      assertThat(event.getNewSign()).isEqualTo("OTHER/02");
      assertThat(event.isMovedToAnotherOrder()).isTrue();
    });
  }

  @Test
  void a_suborder_saved_unchanged_announces_nothing() {
    suborderService().update(11L, suborderDto("02", null), 7L);

    assertThat(renames()).isEmpty();
  }

  @Test
  void what_a_listener_could_not_follow_comes_back_as_a_notice() {
    var notice = ServiceFeedbackMessage.info(ErrorCode.RP_DEFINITIONS_NAME_OLD_SIGN, "Umsatz", "CO/02");
    doAnswer(invocation -> {
      if (invocation.getArgument(0) instanceof SignsRenamedEvent event) event.addNotice(notice);
      return null;
    }).when(eventPublisher).publishEvent(any(Object.class));

    assertThat(suborderService().update(11L, suborderDto("X2", null), 7L)).containsExactly(notice);
  }

  @Test
  void a_renamed_order_announces_its_old_and_new_sign() {
    var service = new CustomerorderService(eventPublisher, mock(CommandPublisher.class), customerorderDAO,
        mock(CustomerDAO.class), mock(EmployeeDAO.class), mock(CustomerorderRepository.class, invocation ->
            invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null));

    service.update(7L, customerorderDto("NEW"));

    assertThat(renames()).singleElement().satisfies(event -> {
      assertThat(event.getOldSign()).isEqualTo("CO");
      assertThat(event.getNewSign()).isEqualTo("NEW");
    });
  }

  @Test
  void an_order_saved_under_its_sign_announces_nothing() {
    var service = new CustomerorderService(eventPublisher, mock(CommandPublisher.class), customerorderDAO,
        mock(CustomerDAO.class), mock(EmployeeDAO.class), mock(CustomerorderRepository.class, invocation ->
            invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null));

    service.update(7L, customerorderDto("CO"));

    assertThat(renames()).isEmpty();
  }

  private SuborderService suborderService() {
    return new SuborderService(eventPublisher, mock(CommandPublisher.class), suborderDAO,
        mock(SuborderRepository.class), customerorderService);
  }

  private List<SignsRenamedEvent> renames() {
    return published.stream()
        .filter(SignsRenamedEvent.class::isInstance)
        .map(SignsRenamedEvent.class::cast)
        .toList();
  }

  private static SuborderDTO suborderDto(String sign, Long parentId) {
    return new SuborderDTO(7L, sign, "Leistung", "Leistung", null, 'y', false, false, false, false,
        OrderType.STANDARD, "2026-01-01", "", null, null, false, parentId);
  }

  private static CustomerorderDTO customerorderDto(String sign) {
    return new CustomerorderDTO(1L, FROM, null, sign, "Auftrag", "Auftrag", null, null, null, List.of(1L), 1L,
        null, null, false, OrderType.STANDARD);
  }

  private static Customerorder customerorder(long id, String sign) {
    var customerorder = new Customerorder();
    setField(customerorder, "id", id);
    customerorder.setSign(sign);
    customerorder.setFromDate(FROM);
    return customerorder;
  }

  private static Suborder suborder(long id, Customerorder customerorder, String sign) {
    var suborder = new Suborder();
    setField(suborder, "id", id);
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setFromDate(FROM);
    return suborder;
  }
}
