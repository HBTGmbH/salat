package de.hbt.salat.dailyreport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.auth.TimereportVisibility.Clause;
import de.hbt.salat.dailyreport.auth.TimereportVisibilityService;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO;
import de.hbt.salat.dailyreport.persistence.TimereportListDAO.FilterValues;
import de.hbt.salat.jira.domain.JiraTicketInfo;
import de.hbt.salat.jira.service.JiraTicketService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.SuborderLocation;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Whose tickets the ticket dialog of the booking list offers (#1092) — named by the ids of orders and
 * suborders (#1323), not by their signs. An order stands for its order-wide tickets, a suborder for
 * those replicated on it; a renamed order or a moved suborder keeps its tickets.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayNameGeneration(ReplaceUnderscores.class)
class TimereportListTicketScopesTest {

  private static final long ORDER = 1L;
  private static final long OTHER_ORDER = 2L;
  private static final long SUBORDER = 12L;

  @Mock
  private TimereportListDAO timereportListDAO;
  @Mock
  private TimereportVisibilityService visibilityService;
  @Mock
  private CustomerorderService customerorderService;
  @Mock
  private SuborderService suborderService;
  @Mock
  private JiraTicketService jiraTicketService;

  @InjectMocks
  private TimereportListService timereportListService;

  @BeforeEach
  void setUp() {
    when(visibilityService.anyTime()).thenReturn(TimereportVisibility.all());
    when(jiraTicketService.getTickets(any(), any())).thenReturn(List.of(
        new JiraTicketInfo("ALPHA-1", "Login", "Story", null)));
    when(suborderService.getSuborderLocationsByIds(List.of(SUBORDER))).thenReturn(Map.of(SUBORDER,
        new SuborderLocation(SUBORDER, ORDER, List.of(SUBORDER), "ALPHA/01")));
  }

  @Test
  void a_chosen_order_offers_its_order_wide_tickets() {
    var result = timereportListService.searchTickets("", List.of(), List.of(ORDER), List.of(), 50);

    verify(jiraTicketService).getTickets(List.of(ORDER), List.of());
    assertThat(result.tickets()).extracting(row -> row.ticket().key()).containsExactly("ALPHA-1");
  }

  @Test
  void a_chosen_suborder_offers_its_own_tickets_and_those_of_its_order() {
    timereportListService.searchTickets("", List.of(), List.of(), List.of(SUBORDER), 50);

    verify(jiraTicketService).getTickets(List.of(ORDER), List.of(SUBORDER));
  }

  @Test
  void without_a_filter_a_manager_gets_the_tickets_of_every_order_not_hidden() {
    when(customerorderService.getNotHiddenCustomerorders()).thenReturn(List.of(order(ORDER), order(OTHER_ORDER)));
    when(suborderService.getNotHiddenSuborders()).thenReturn(List.of(suborder(SUBORDER)));

    timereportListService.searchTickets("", List.of(), List.of(), List.of(), 50);

    verify(jiraTicketService).getTickets(List.of(ORDER, OTHER_ORDER), List.of(SUBORDER));
  }

  @Test
  void without_a_filter_everybody_else_gets_the_tickets_of_the_orders_they_see_bookings_on() {
    var visibility = TimereportVisibility.of(List.of(Clause.forEmployees(Set.of(7L))));
    when(visibilityService.anyTime()).thenReturn(visibility);
    when(timereportListDAO.findFilterValues(visibility))
        .thenReturn(new FilterValues(List.of(7L), List.of(), List.of(OTHER_ORDER), List.of(SUBORDER)));

    timereportListService.searchTickets("", List.of(), List.of(), List.of(), 50);

    verify(jiraTicketService).getTickets(List.of(OTHER_ORDER), List.of(SUBORDER));
  }

  private static Customerorder order(long id) {
    var order = new Customerorder();
    ReflectionTestUtils.setField(order, "id", id);
    return order;
  }

  private static Suborder suborder(long id) {
    var suborder = new Suborder();
    ReflectionTestUtils.setField(suborder, "id", id);
    return suborder;
  }
}
