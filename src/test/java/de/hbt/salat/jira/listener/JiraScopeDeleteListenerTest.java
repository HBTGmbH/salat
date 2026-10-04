package de.hbt.salat.jira.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ServiceFeedbackMessage;
import de.hbt.salat.common.exception.VetoedException;
import de.hbt.salat.jira.service.JiraScopeReferenceService;
import de.hbt.salat.order.event.CustomerorderDeleteEvent;
import de.hbt.salat.order.event.SuborderDeleteEvent;

/**
 * A replication refers to its order and suborder by id with a foreign key (#1322). Deleting what it
 * refers to is refused with a message that says so, before the key would refuse it as a failed
 * statement. Tickets and worklog rows go with the scope instead (#1323).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class JiraScopeDeleteListenerTest {

  private JiraScopeReferenceService jiraScopeReferenceService;
  private JiraScopeDeleteListener listener;

  @BeforeEach
  void setUp() {
    jiraScopeReferenceService = mock(JiraScopeReferenceService.class);
    listener = new JiraScopeDeleteListener(jiraScopeReferenceService);
  }

  @Test
  void an_order_with_a_replication_is_not_deleted() {
    when(jiraScopeReferenceService.countReplicationsOfCustomerorder(1L)).thenReturn(2L);

    assertThatThrownBy(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L)))
        .isInstanceOf(VetoedException.class)
        .satisfies(ex -> assertThat(((VetoedException) ex).getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode)
            .containsExactly(ErrorCode.JI_ORDER_HAS_REPLICATIONS));
    verify(jiraScopeReferenceService, never()).deleteScopeDataOfCustomerorder(anyLong());
  }

  @Test
  void a_suborder_with_a_replication_in_its_branch_is_not_deleted() {
    when(jiraScopeReferenceService.countReplicationsOfSuborder(11L)).thenReturn(1L);

    assertThatThrownBy(() -> listener.onSuborderDelete(new SuborderDeleteEvent(11L)))
        .isInstanceOf(VetoedException.class)
        .satisfies(ex -> assertThat(((VetoedException) ex).getMessages())
            .extracting(ServiceFeedbackMessage::getErrorCode)
            .containsExactly(ErrorCode.JI_SUBORDER_HAS_REPLICATIONS));
    verify(jiraScopeReferenceService, never()).deleteScopeDataOfSuborder(anyLong());
  }

  @Test
  void an_order_without_a_replication_may_go_and_takes_its_tickets_and_worklogs_along() {
    assertThatCode(() -> listener.onCustomerorderDelete(new CustomerorderDeleteEvent(1L))).doesNotThrowAnyException();
    assertThatCode(() -> listener.onSuborderDelete(new SuborderDeleteEvent(11L))).doesNotThrowAnyException();

    verify(jiraScopeReferenceService).deleteScopeDataOfCustomerorder(1L);
    verify(jiraScopeReferenceService).deleteScopeDataOfSuborder(11L);
  }
}
