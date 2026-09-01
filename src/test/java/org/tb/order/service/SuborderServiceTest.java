package org.tb.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.tb.common.command.CommandPublisher;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.persistence.SuborderRepository;

/**
 * Budget, pricing and cost records reference their suborder by complete order sign (#889). These
 * tests pin the check that rejects a sign that does not belong to the chosen customer order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class SuborderServiceTest {

  private SuborderDAO suborderDAO;
  private CustomerorderService customerorderService;
  private SuborderService suborderService;

  @BeforeEach
  public void setUp() {
    suborderDAO = mock(SuborderDAO.class);
    customerorderService = mock(CustomerorderService.class);
    suborderService = new SuborderService(
        mock(ApplicationEventPublisher.class),
        mock(CommandPublisher.class),
        suborderDAO,
        mock(SuborderRepository.class),
        customerorderService);
  }

  @Test
  public void should_accept_the_complete_order_sign_of_a_nested_suborder() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsByCompleteOrderSign("co", "co/01/02")).isTrue();
  }

  @Test
  public void should_reject_the_bare_suborder_sign() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsByCompleteOrderSign("co", "02")).isFalse();
  }

  @Test
  public void should_reject_a_sign_that_belongs_to_another_customer_order() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsByCompleteOrderSign("co", "other/01/02")).isFalse();
  }

  @Test
  public void should_reject_when_the_customer_order_does_not_exist() {
    when(customerorderService.getCustomerorderBySign("co")).thenReturn(null);

    assertThat(suborderService.existsByCompleteOrderSign("co", "co/01/02")).isFalse();
  }

  /**
   * Pricing patterns are matched, not compared (#891), so the check that a value refers to a real
   * suborder has to apply the same rule — including the trailing slash it binds against.
   */
  @Test
  public void should_accept_a_subtree_pattern_covering_at_least_one_suborder() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsSuborderMatching("co", "co/01/")).isTrue();
    assertThat(suborderService.existsSuborderMatching("co", "co/%/02/")).isTrue();
  }

  @Test
  public void should_reject_a_pattern_covering_no_suborder() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsSuborderMatching("co", "co/07/")).isFalse();
    assertThat(suborderService.existsSuborderMatching("co", "02")).isFalse();
  }

  @Test
  public void should_accept_an_empty_pattern_as_covering_the_whole_order() {
    givenOrderWithNestedSuborder();

    assertThat(suborderService.existsSuborderMatching("co", null)).isTrue();
    assertThat(suborderService.existsSuborderMatching("co", "")).isTrue();
  }

  /**
   * Hidden suborders must not be offered, but the one a record already references has to stay in
   * the list — otherwise editing that record silently drops the reference (#895).
   */
  @Test
  public void should_leave_out_hidden_suborders() {
    givenOrderWithHiddenChild();

    assertThat(selectableSigns(null)).containsExactly("co/01");
  }

  @Test
  public void should_keep_a_hidden_suborder_that_the_record_still_references() {
    givenOrderWithHiddenChild();

    assertThat(selectableSigns("co/01/02")).containsExactly("co/01", "co/01/02");
  }

  @Test
  public void should_not_keep_a_hidden_suborder_that_is_not_the_referenced_one() {
    givenOrderWithHiddenChild();

    assertThat(selectableSigns("co/01")).containsExactly("co/01");
  }

  private List<String> selectableSigns(String keep) {
    return suborderService.getSelectableSubordersByCustomerorderId(1L, keep).stream()
        .map(Suborder::getCompleteOrderSign)
        .toList();
  }

  private void givenOrderWithHiddenChild() {
    var customerorder = mock(Customerorder.class);
    when(customerorder.getSign()).thenReturn("co");

    var parent = new Suborder();
    parent.setCustomerorder(customerorder);
    parent.setSign("01");
    var child = new Suborder();
    child.setCustomerorder(customerorder);
    child.setParentorder(parent);
    child.setSign("02");
    child.setHide(true);

    when(suborderDAO.getSubordersByCustomerorderId(anyLong(), anyBoolean()))
        .thenReturn(List.of(parent, child));
  }

  private void givenOrderWithNestedSuborder() {
    // Customerorder has no id setter, and getCompleteOrderSign() only needs the sign.
    var customerorder = mock(Customerorder.class);
    when(customerorder.getId()).thenReturn(1L);
    when(customerorder.getSign()).thenReturn("co");

    var parent = new Suborder();
    parent.setCustomerorder(customerorder);
    parent.setSign("01");
    var child = new Suborder();
    child.setCustomerorder(customerorder);
    child.setParentorder(parent);
    child.setSign("02");

    when(customerorderService.getCustomerorderBySign("co")).thenReturn(customerorder);
    when(suborderDAO.getSubordersByCustomerorderId(anyLong(), anyBoolean()))
        .thenReturn(List.of(parent, child));
  }

}
