package org.tb.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.tb.common.command.CommandPublisher;
import org.tb.common.test.FixedClock;
import org.tb.common.util.DateUtils;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Suborder;
import org.tb.order.persistence.SuborderDAO;
import org.tb.order.persistence.SuborderRepository;

/**
 * Budget, pricing and cost records reference their suborder by complete order sign (#889). These
 * tests pin the check that rejects a sign that does not belong to the chosen customer order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-06-25T10:15:30")
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

  /**
   * The order forms address a suborder by id rather than by complete order sign (#1005). Same rule:
   * the hidden one a record already stores stays in the list, because the parent select drops what
   * it cannot show and the save then writes back whatever the browser preselected.
   */
  @Test
  public void should_leave_out_hidden_suborders_when_addressed_by_id() {
    givenOrderWithHiddenChild();

    assertThat(selectableSignsById(null)).containsExactly("co/01");
  }

  @Test
  public void should_keep_a_hidden_suborder_that_the_record_still_references_by_id() {
    givenOrderWithHiddenChild();

    assertThat(selectableSignsById(2L)).containsExactly("co/01", "co/01/02");
  }

  @Test
  public void should_not_keep_a_hidden_suborder_that_is_not_the_referenced_one_by_id() {
    givenOrderWithHiddenChild();

    assertThat(selectableSignsById(1L)).containsExactly("co/01");
  }

  /**
   * The select box of the invoice mask hides what is inactive, and inactive means the validity has
   * ended before today (#1095, ADR-0029). A suborder entered ahead of time has not ended, so it
   * stays — otherwise nobody sees it and it gets entered a second time.
   */
  @Test
  public void should_keep_a_suborder_that_only_starts_in_the_future() {
    givenSubordersAcrossTheValidityRange();

    assertThat(activeSigns()).contains("co/future");
  }

  @Test
  public void should_keep_a_suborder_without_own_start_below_a_parent_starting_in_the_future() {
    givenSubordersAcrossTheValidityRange();

    assertThat(activeSigns()).contains("co/future/inherited");
  }

  @Test
  public void should_leave_out_a_suborder_whose_validity_ended_yesterday() {
    givenSubordersAcrossTheValidityRange();

    assertThat(activeSigns()).doesNotContain("co/ended");
  }

  @Test
  public void should_keep_a_suborder_ending_today_and_one_without_an_end() {
    givenSubordersAcrossTheValidityRange();

    assertThat(activeSigns()).contains("co/endstoday", "co/openend");
  }

  @Test
  public void should_leave_out_a_hidden_suborder_regardless_of_its_validity() {
    givenSubordersAcrossTheValidityRange();

    assertThat(activeSigns()).doesNotContain("co/hidden");
    assertThat(signs(true)).doesNotContain("co/hidden");
  }

  @Test
  public void should_add_the_inactive_suborders_when_the_switch_is_on() {
    givenSubordersAcrossTheValidityRange();

    assertThat(signs(true)).contains("co/ended");
  }

  private List<String> activeSigns() {
    return signs(false);
  }

  private List<String> signs(boolean showInactive) {
    return suborderService.getSubordersByCustomerorderId(1L, showInactive).stream()
        .map(Suborder::getCompleteOrderSign)
        .toList();
  }

  /**
   * One suborder per boundary of the rule: start in the future, end yesterday, end today, open end,
   * hidden, plus a child that inherits its start from a parent starting in the future.
   */
  private void givenSubordersAcrossTheValidityRange() {
    var customerorder = mock(Customerorder.class);
    when(customerorder.getSign()).thenReturn("co");
    LocalDate today = DateUtils.today();

    var future = suborder(customerorder, "future", today.plusMonths(1), null);
    var inherited = new Suborder();
    inherited.setCustomerorder(customerorder);
    inherited.setParentorder(future);
    inherited.setSign("inherited");
    var ended = suborder(customerorder, "ended", today.minusYears(1), today.minusDays(1));
    var endsToday = suborder(customerorder, "endstoday", today.minusYears(1), today);
    var openEnd = suborder(customerorder, "openend", today.minusYears(1), null);
    var hidden = suborder(customerorder, "hidden", today.minusYears(1), null);
    hidden.setHide(true);

    when(suborderDAO.getSubordersByCustomerorderId(anyLong()))
        .thenReturn(List.of(future, inherited, ended, endsToday, openEnd, hidden));
  }

  private Suborder suborder(Customerorder customerorder, String sign, LocalDate from, LocalDate until) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setFromDate(from);
    suborder.setUntilDate(until);
    return suborder;
  }

  private List<String> selectableSigns(String keep) {
    return suborderService.getSelectableSubordersByCustomerorderId(1L, keep).stream()
        .map(Suborder::getCompleteOrderSign)
        .toList();
  }

  private List<String> selectableSignsById(Long keepId) {
    return suborderService.getSelectableSubordersByCustomerorderId(1L, keepId).stream()
        .map(Suborder::getCompleteOrderSign)
        .toList();
  }

  private void givenOrderWithHiddenChild() {
    var customerorder = mock(Customerorder.class);
    when(customerorder.getSign()).thenReturn("co");

    var parent = new Suborder();
    parent.setCustomerorder(customerorder);
    parent.setSign("01");
    setField(parent, "id", 1L);
    var child = new Suborder();
    child.setCustomerorder(customerorder);
    child.setParentorder(parent);
    child.setSign("02");
    child.setHide(true);
    setField(child, "id", 2L);

    when(suborderDAO.getSubordersByCustomerorderId(anyLong()))
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
    when(suborderDAO.getSubordersByCustomerorderId(anyLong()))
        .thenReturn(List.of(parent, child));
  }

}
