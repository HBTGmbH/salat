package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.customer.persistence.CustomerDAO;
import de.hbt.salat.employee.persistence.EmployeeDAO;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderDTO;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderDTO;
import de.hbt.salat.order.domain.SurchargeInput;
import de.hbt.salat.order.domain.TicketReferenceMode;
import de.hbt.salat.order.persistence.CustomerorderDAO;
import de.hbt.salat.order.persistence.CustomerorderRepository;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;
import de.hbt.salat.order.persistence.TagReferences;
import de.hbt.salat.tag.domain.Tag;
import de.hbt.salat.tag.domain.TagName;
import de.hbt.salat.tag.service.TagService;

/**
 * Saving an order or suborder sets its tags from the names the form sends (#847): the module {@code tag} turns them
 * into ids, the order module takes the references. Code that rebuilds the record from the stored entity sends no
 * names and leaves the tags alone.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
class OrderTagAssignmentTest {

  private static final long ORDER_ID = 7L;
  private static final long SUBORDER_ID = 9L;
  private static final LocalDate FROM = LocalDate.parse("2026-01-01");

  private final TagService tagService = mock(TagService.class);
  private final TagReferences tagReferences = mock(TagReferences.class);
  private final Tag fixedPrice = tag(1L, "Festpreis");
  private final Tag cloud = tag(2L, "Cloud");

  private CustomerorderService customerorderService;
  private SuborderService suborderService;
  private Customerorder order;
  private Suborder suborder;

  @BeforeEach
  void setUp() {
    var customerorderDAO = mock(CustomerorderDAO.class);
    customerorderService = new CustomerorderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        customerorderDAO, mock(CustomerDAO.class), mock(EmployeeDAO.class), mock(CustomerorderRepository.class,
            invocation -> invocation.getMethod().getName().equals("save") ? invocation.getArgument(0) : null),
        mock(SpecialOrders.class), tagService, tagReferences);
    var suborderDAO = mock(SuborderDAO.class);
    var orders = mock(CustomerorderService.class);
    suborderService = new SuborderService(mock(ApplicationEventPublisher.class), mock(CommandPublisher.class),
        suborderDAO, mock(SuborderRepository.class), orders, mock(SpecialOrders.class), tagService, tagReferences);

    order = new Customerorder();
    setField(order, "id", ORDER_ID);
    order.setSign("4711");
    order.setFromDate(FROM);
    suborder = new Suborder();
    setField(suborder, "id", SUBORDER_ID);
    suborder.setCustomerorder(order);
    suborder.setSign("01");
    suborder.setFromDate(FROM);
    when(customerorderDAO.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(orders.getCustomerorderById(ORDER_ID)).thenReturn(order);
    when(suborderDAO.getSuborderById(SUBORDER_ID)).thenReturn(suborder);

    when(tagService.resolveTagIds(List.of("Festpreis", "cloud"))).thenReturn(List.of(1L, 2L));
    when(tagService.resolveTagIds(List.of())).thenReturn(List.of());
    when(tagReferences.tags(List.of(1L, 2L))).thenReturn(List.of(fixedPrice, cloud));
    when(tagReferences.tags(List.of())).thenReturn(List.of());
  }

  @Test
  void saving_an_order_sets_the_tags_of_the_names() {
    order.replaceTags(List.of(tag(3L, "Alt")));

    customerorderService.update(ORDER_ID, orderDto(List.of("Festpreis", "cloud")));

    assertThat(order.getTags()).containsExactlyInAnyOrder(fixedPrice, cloud);
  }

  @Test
  void an_order_saved_without_names_loses_its_tags() {
    order.replaceTags(List.of(fixedPrice));

    customerorderService.update(ORDER_ID, orderDto(List.of()));

    assertThat(order.getTags()).isEmpty();
  }

  @Test
  void an_order_saved_with_no_list_at_all_keeps_its_tags() {
    order.replaceTags(List.of(fixedPrice));

    customerorderService.update(ORDER_ID, orderDto(null));

    assertThat(order.getTags()).containsExactly(fixedPrice);
    verifyNoInteractions(tagService);
  }

  @Test
  void saving_a_suborder_sets_the_tags_of_the_names() {
    suborderService.update(SUBORDER_ID, suborderDto(List.of("Festpreis", "cloud")), ORDER_ID);

    assertThat(suborder.getTags()).containsExactlyInAnyOrder(fixedPrice, cloud);
  }

  /** A changed validity of the order rebuilds the suborder's record from the entity, without names. */
  @Test
  void a_suborder_saved_with_no_list_at_all_keeps_its_tags() {
    suborder.replaceTags(List.of(cloud));

    suborderService.update(SUBORDER_ID, suborderDto(null), ORDER_ID);

    assertThat(suborder.getTags()).containsExactly(cloud);
    verifyNoInteractions(tagService);
  }

  private static CustomerorderDTO orderDto(List<String> tagNames) {
    return new CustomerorderDTO(1L, FROM, null, "4711", "Auftrag", "Auftrag", null, null, null, List.of(1L), 1L,
        null, null, false, OrderType.STANDARD, TicketReferenceMode.UNLIMITED, null, SurchargeInput.NONE, tagNames);
  }

  private static SuborderDTO suborderDto(List<String> tagNames) {
    return new SuborderDTO(ORDER_ID, "01", "Leistung", "Leistung", null, 'y', false, false, false,
        OrderType.STANDARD, "2026-01-01", "", null, null, false, null, null, null, SurchargeInput.NONE, tagNames);
  }

  private static Tag tag(long id, String name) {
    var tag = new Tag(TagName.of(name));
    setField(tag, "id", id);
    return tag;
  }
}
