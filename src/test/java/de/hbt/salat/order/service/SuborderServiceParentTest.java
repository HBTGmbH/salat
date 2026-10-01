package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.event.CustomerorderUpdateEvent;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * Where a suborder hangs (#1243). A suborder directly below its customer order has no parent, and
 * {@code parentId} says so with {@code null}; any other value names a suborder of the same order.
 * Customer orders and suborders count their ids independently, so the id of the order can just as
 * well be the id of one of its suborders — every case here has such a suborder, {@link #TWIN}.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class SuborderServiceParentTest {

  private static final long ORDER_ID = 7L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");
  /** A top-level suborder of the order that carries the order's id as its own. */
  private static final long TWIN = ORDER_ID;
  private static final long SIBLING = 9L;
  private static final long FOREIGN = 11L;

  private SuborderDAO suborderDAO;
  private CustomerorderService customerorderService;
  private SuborderService suborderService;

  private Customerorder order;
  private Suborder twin;
  private Suborder sibling;
  private Customerorder otherOrder;
  private Suborder foreign;

  @BeforeEach
  void setUp() {
    suborderDAO = mock(SuborderDAO.class);
    customerorderService = mock(CustomerorderService.class);
    suborderService = new SuborderService(
        mock(ApplicationEventPublisher.class),
        mock(CommandPublisher.class),
        suborderDAO,
        mock(SuborderRepository.class),
        customerorderService);

    order = customerorder(ORDER_ID, "co");
    twin = suborder(TWIN, order, "01");
    sibling = suborder(SIBLING, order, "02");
    otherOrder = customerorder(8L, "other");
    foreign = suborder(FOREIGN, otherOrder, "01");

    when(customerorderService.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(suborderDAO.getSuborderById(TWIN)).thenReturn(twin);
    when(suborderDAO.getSuborderById(SIBLING)).thenReturn(sibling);
    when(suborderDAO.getSuborderById(FOREIGN)).thenReturn(foreign);
    when(suborderDAO.getSubordersByCustomerorderId(ORDER_ID)).thenReturn(List.of(twin, sibling));
  }

  /**
   * Changing the order's validity re-stores every suborder. That once handed the order's id over as
   * the parent of a top-level suborder: the sibling slid below the twin, and the twin was asked to
   * become its own parent.
   */
  @Test
  void a_changed_customer_order_leaves_its_top_level_suborders_on_the_top_level() {
    order.setUntilDate(LocalDate.parse("2026-12-31"));

    assertThatCode(() -> suborderService.onCustomerorderUpdate(new CustomerorderUpdateEvent(order)))
        .doesNotThrowAnyException();

    assertThat(sibling.getParentorder()).isNull();
    assertThat(twin.getParentorder()).isNull();
  }

  @Test
  void a_new_suborder_without_parent_lands_on_the_top_level() {
    var created = createAndCapture(null);

    assertThat(created.getParentorder()).isNull();
  }

  @Test
  void a_parent_id_equal_to_the_order_id_names_the_suborder_with_that_id() {
    suborderService.update(SIBLING, dto("02", TWIN), ORDER_ID);

    assertThat(sibling.getParentorder()).isSameAs(twin);
  }

  @Test
  void moving_a_suborder_back_to_the_top_level_drops_its_parent() {
    sibling.setParentorder(twin);

    suborderService.update(SIBLING, dto("02", null), ORDER_ID);

    assertThat(sibling.getParentorder()).isNull();
  }

  @Test
  void a_parent_of_another_customer_order_is_rejected() {
    assertThatThrownBy(() -> suborderService.update(SIBLING, dto("02", FOREIGN), ORDER_ID))
        .isInstanceOfSatisfying(BusinessRuleException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(ErrorCode.SO_PARENTORDER_INVALID));
    assertThat(sibling.getParentorder()).isNull();
  }

  @Test
  void a_parent_id_naming_no_suborder_is_rejected() {
    assertThatThrownBy(() -> suborderService.update(SIBLING, dto("02", 404L), ORDER_ID))
        .isInstanceOfSatisfying(BusinessRuleException.class,
            e -> assertThat(e.getMessages().getFirst().getErrorCode()).isEqualTo(ErrorCode.SO_PARENTORDER_INVALID));
    assertThat(sibling.getParentorder()).isNull();
  }

  /** Creating hands a fresh entity to the repository; the parent is read off that one. */
  private Suborder createAndCapture(Long parentId) {
    var repository = mock(SuborderRepository.class);
    var service = new SuborderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        suborderDAO, repository, customerorderService);
    var saved = ArgumentCaptor.forClass(Suborder.class);

    service.create(dto("03", parentId), ORDER_ID);

    verify(repository).save(saved.capture());
    return saved.getValue();
  }

  private static SuborderDTO dto(String sign, Long parentId) {
    return new SuborderDTO(ORDER_ID, sign, "Leistung", "Leistung", null, 'y', false, false, false, false,
        OrderType.STANDARD, "2026-01-01", "", null, null, false, parentId);
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
