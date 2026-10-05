package de.hbt.salat.jira.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
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
 * The scope sign of replications, tickets and worklogs follows the order tree (#1322, #1323). The
 * application resolves the scope by id; the sign is what reports and ETL definitions still read.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraScopeReferenceServiceTest {

  private final Customerorder order = new Customerorder();
  private final Customerorder otherOrder = new Customerorder();
  private final Suborder first = new Suborder();
  private final Suborder below = new Suborder();

  private final JiraReplicationConfig orderWide = config(null, "CO");
  private final JiraReplicationConfig onFirst = config(11L, "CO/01");
  private final JiraReplicationConfig onBelow = config(12L, "CO/01/A");

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
    completeSignsDerived();
    first.setSuborders(List.of(below));

    configRepository = mock(JiraReplicationConfigRepository.class);
    when(configRepository.findByCustomerorderId(1L)).thenReturn(List.of(orderWide, onFirst, onBelow));
    when(configRepository.findBySuborderIdIn(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return Stream.of(onFirst, onBelow).filter(config -> ids.contains(config.getSuborderId())).toList();
    });
    suborderService = mock(SuborderService.class);
    when(suborderService.getCompleteOrderSignsByIds(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return Stream.of(first, below).filter(suborder -> ids.contains(suborder.getId()))
          .collect(Collectors.toMap(Suborder::getId, Suborder::getCompleteOrderSign));
    });
    ticketRepository = mock(JiraTicketRepository.class);
    worklogSyncRepository = mock(JiraWorklogSyncRepository.class);
    classUnderTest = new JiraScopeReferenceService(configRepository, ticketRepository, worklogSyncRepository,
        new JiraScopes(mock(CustomerorderService.class), suborderService));
  }

  @Test
  void a_renamed_order_renames_every_scope_below_it() {
    order.setSign("CO-NEW");
    completeSignsDerived();

    classUnderTest.followCustomerorder(order);

    assertThat(orderWide.getScopeSign()).isEqualTo("CO-NEW");
    assertThat(onFirst.getScopeSign()).isEqualTo("CO-NEW/01");
    assertThat(onBelow.getScopeSign()).isEqualTo("CO-NEW/01/A");
  }

  @Test
  void a_renamed_order_renames_its_tickets_and_worklogs_in_bulk() {
    when(ticketRepository.findSuborderIdsOfCustomerorder(1L)).thenReturn(List.of(12L));
    when(worklogSyncRepository.findSuborderIdsOfCustomerorder(1L)).thenReturn(List.of(12L));
    order.setSign("CO-NEW");
    completeSignsDerived();

    classUnderTest.followCustomerorder(order);

    verify(ticketRepository).mirrorOrderWide(1L, "CO-NEW");
    verify(worklogSyncRepository).mirrorOrderWide(1L, "CO-NEW");
    verify(ticketRepository).mirrorSuborder(12L, 1L, "CO-NEW/01/A");
    verify(worklogSyncRepository).mirrorSuborder(12L, 1L, "CO-NEW/01/A");
    verify(ticketRepository, never()).mirrorSuborder(11L, 1L, "CO-NEW/01");
  }

  @Test
  void a_renamed_suborder_renames_its_branch_and_leaves_the_order_wide_scope_alone() {
    first.setSign("02");
    completeSignsDerived();

    classUnderTest.followSuborder(first);

    assertThat(onFirst.getScopeSign()).isEqualTo("CO/02");
    assertThat(onBelow.getScopeSign()).isEqualTo("CO/02/A");
    assertThat(orderWide.getScopeSign()).isEqualTo("CO");
  }

  @Test
  void a_suborder_moved_to_another_parent_takes_its_scope_along() {
    var other = new Suborder();
    setId(other, 13L);
    other.setCustomerorder(order);
    other.setSign("02");
    below.setParentorder(other);
    completeSignsDerived();

    classUnderTest.followSuborder(below);

    assertThat(onBelow.getScopeSign()).isEqualTo("CO/02/A");
    assertThat(onBelow.getSuborderId()).isEqualTo(12L);
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_replications_along() {
    // they mean that place in the tree, not the order it used to hang under
    first.setCustomerorder(otherOrder);
    below.setCustomerorder(otherOrder);
    completeSignsDerived();

    classUnderTest.followSuborder(first);

    assertThat(onFirst.getCustomerorderId()).isEqualTo(2L);
    assertThat(onFirst.getScopeSign()).isEqualTo("OTHER/01");
    assertThat(onBelow.getCustomerorderId()).isEqualTo(2L);
    assertThat(onBelow.getScopeSign()).isEqualTo("OTHER/01/A");
    assertThat(orderWide.getCustomerorderId()).isEqualTo(1L);
  }

  @Test
  void a_suborder_moved_to_another_order_takes_its_tickets_and_worklogs_along() {
    when(ticketRepository.findSuborderIdsIn(any())).thenReturn(List.of(11L));
    when(worklogSyncRepository.findSuborderIdsIn(any())).thenReturn(List.of(12L));
    first.setCustomerorder(otherOrder);
    below.setCustomerorder(otherOrder);
    completeSignsDerived();

    classUnderTest.followSuborder(first);

    verify(ticketRepository).mirrorSuborder(11L, 2L, "OTHER/01");
    verify(worklogSyncRepository).mirrorSuborder(12L, 2L, "OTHER/01/A");
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

  private static JiraReplicationConfig config(Long suborderId, String scopeSign) {
    var config = new JiraReplicationConfig();
    config.setCustomerorderId(1L);
    config.setSuborderId(suborderId);
    config.setScopeSign(scopeSign);
    return config;
  }

  /** What the order module does whenever the tree changes (#1342): the stored complete signs follow. */
  private void completeSignsDerived() {
    first.deriveCompleteOrderSign();
    below.deriveCompleteOrderSign();
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
