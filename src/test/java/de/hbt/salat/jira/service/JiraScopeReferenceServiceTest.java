package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.jira.domain.JiraReplicationConfig;
import de.hbt.salat.jira.persistence.JiraReplicationConfigRepository;
import de.hbt.salat.jira.persistence.JiraTicketRepository;
import de.hbt.salat.jira.persistence.JiraWorklogSyncRepository;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.CustomerorderService;
import de.hbt.salat.order.service.SuborderService;

/**
 * Replications, tickets and worklogs follow a suborder that is moved to another order (#1322,
 * #1323). They refer to order and suborder as references (#1368); a rename needs nothing (#1372).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraScopeReferenceServiceTest {

  private final Customerorder order = new Customerorder();
  private final Customerorder otherOrder = new Customerorder();
  private final Suborder first = new Suborder();
  private final Suborder below = new Suborder();

  private JiraReplicationConfig orderWide;
  private JiraReplicationConfig onFirst;
  private JiraReplicationConfig onBelow;

  private JiraReplicationConfigRepository configRepository;
  private JiraTicketRepository ticketRepository;
  private JiraWorklogSyncRepository worklogSyncRepository;
  private SuborderService suborderService;
  private JiraScopeReferenceService classUnderTest;

  @BeforeEach
  void setUp() {
    setId(order, 1L);
    order.setSign("CO");
    setId(otherOrder, 2L);
    otherOrder.setSign("OTHER");
    setId(first, 11L);
    first.setCustomerorder(order);
    first.setSign("01");
    setId(below, 12L);
    below.setCustomerorder(order);
    below.setParentorder(first);
    below.setSign("A");
    first.setSuborders(List.of(below));
    orderWide = config(null);
    onFirst = config(first);
    onBelow = config(below);

    configRepository = mock(JiraReplicationConfigRepository.class);
    when(configRepository.findBySuborderIdIn(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return Stream.of(onFirst, onBelow).filter(config -> ids.contains(config.getSuborderId())).toList();
    });
    suborderService = mock(SuborderService.class);
    ticketRepository = mock(JiraTicketRepository.class);
    worklogSyncRepository = mock(JiraWorklogSyncRepository.class);
    classUnderTest = new JiraScopeReferenceService(configRepository, ticketRepository, worklogSyncRepository,
        new JiraScopes(mock(CustomerorderService.class), suborderService));
  }

  @Test
  void a_suborder_moved_to_another_parent_keeps_its_scope() {
    var other = new Suborder();
    setId(other, 13L);
    other.setCustomerorder(order);
    other.setSign("02");
    below.setParentorder(other);

    classUnderTest.followSuborder(below);

    assertThat(onBelow.getSuborder()).isSameAs(below);
    assertThat(onBelow.getCustomerorder()).isSameAs(order);
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_replications_along() {
    // they mean that place in the tree, not the order it used to hang under
    first.setCustomerorder(otherOrder);
    below.setCustomerorder(otherOrder);

    classUnderTest.followSuborder(first);

    assertThat(onFirst.getCustomerorder()).isSameAs(otherOrder);
    assertThat(onBelow.getCustomerorder()).isSameAs(otherOrder);
    assertThat(orderWide.getCustomerorder()).isSameAs(order);
  }

  @Test
  void a_suborder_moved_to_another_order_takes_the_tickets_and_worklogs_of_its_branch_along() {
    first.setCustomerorder(otherOrder);
    below.setCustomerorder(otherOrder);

    classUnderTest.followSuborder(first);

    verify(ticketRepository).moveBranchToCustomerorder(Set.of(11L, 12L), otherOrder);
    verify(worklogSyncRepository).moveBranchToCustomerorder(Set.of(11L, 12L), otherOrder);
  }

  @Test
  void the_tickets_and_worklogs_of_a_deleted_order_go_with_it() {
    classUnderTest.deleteScopeDataOfCustomerorder(1L);

    verify(ticketRepository).deleteByCustomerorderId(1L);
    verify(worklogSyncRepository).deleteByCustomerorderId(1L);
  }

  @Test
  void the_tickets_and_worklogs_of_a_deleted_suborder_go_with_it_for_its_whole_branch() {
    when(suborderService.getSubtreeIds(11L)).thenReturn(List.of(11L, 12L));

    classUnderTest.deleteScopeDataOfSuborder(11L);

    verify(ticketRepository).deleteBySuborderIdIn(List.of(11L, 12L));
    verify(worklogSyncRepository).deleteBySuborderIdIn(List.of(11L, 12L));
  }

  @Test
  void the_replications_of_a_suborder_include_those_of_its_branch() {
    // deleting a suborder takes its branch along
    when(suborderService.getSubtreeIds(11L)).thenReturn(List.of(11L, 12L));
    when(configRepository.countBySuborderIdIn(List.of(11L, 12L))).thenReturn(2L);

    assertThat(classUnderTest.countReplicationsOfSuborder(11L)).isEqualTo(2L);
  }

  @Test
  void a_suborder_that_is_gone_has_no_replications() {
    when(suborderService.getSubtreeIds(99L)).thenReturn(List.of());

    assertThat(classUnderTest.countReplicationsOfSuborder(99L)).isZero();
  }

  private JiraReplicationConfig config(Suborder suborder) {
    var config = new JiraReplicationConfig();
    config.setCustomerorder(order);
    config.setSuborder(suborder);
    return config;
  }

  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
