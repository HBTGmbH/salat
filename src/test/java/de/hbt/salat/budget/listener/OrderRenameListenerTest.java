package de.hbt.salat.budget.listener;

import static de.hbt.salat.testutils.ReferenceTestUtils.customerorderWithId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.budget.domain.OrderPricing;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.OrderBudgetRepository;
import de.hbt.salat.budget.persistence.OrderFlatRateRepository;
import de.hbt.salat.budget.persistence.OrderPricingRepository;
import de.hbt.salat.budget.service.OrderReferenceService;
import de.hbt.salat.common.event.SignsRenamedEvent;
import de.hbt.salat.common.exception.ErrorCode;

/**
 * The suborder patterns of the customer rates follow a renamed order or suborder (#1206). Everything
 * else in the budget module refers to the order tree by id and needs nothing on a rename.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class OrderRenameListenerTest {

  private OrderPricingRepository orderPricingRepository;
  private OrderRenameListener listener;

  @BeforeEach
  void setUp() {
    orderPricingRepository = mock(OrderPricingRepository.class);
    listener = new OrderRenameListener(new OrderReferenceService(mock(OrderBudgetRepository.class),
        mock(OrderFlatRateRepository.class), mock(EmployeeCostAssignmentRepository.class), orderPricingRepository));
  }

  /** The suborder patterns of the customer rates follow a renamed suborder (#1206). */
  @Test
  void a_renamed_suborder_rewrites_the_patterns_that_name_it() {
    var exact = pricing("CO/01");
    var below = pricing("CO/01/A%");
    var sibling = pricing("CO/010");
    var wholeOrder = pricing(null);
    givenPricings(exact, below, sibling, wholeOrder);
    var event = new SignsRenamedEvent("CO/01", "CO/X1", 1L, 1L);

    listener.onSignsRenamed(event);

    assertThat(exact.getSuborderSign()).isEqualTo("CO/X1");
    assertThat(below.getSuborderSign()).isEqualTo("CO/X1/A%");
    assertThat(sibling.getSuborderSign()).isEqualTo("CO/010");
    assertThat(wholeOrder.getSuborderSign()).isNull();
    assertThat(event.getNotices()).isEmpty();
  }

  /** A renamed order changes the beginning of every pattern of the order (#1206). */
  @Test
  void a_renamed_order_rewrites_the_beginning_of_every_pattern() {
    var pattern = pricing("CO/01/");
    givenPricings(pattern);

    listener.onSignsRenamed(new SignsRenamedEvent("CO", "NEW", 1L, 1L));

    assertThat(pattern.getSuborderSign()).isEqualTo("NEW/01/");
  }

  /** A pattern that meant a group is not guessed at; the person saving is told (#1206). */
  @Test
  void a_pattern_that_lost_the_renamed_suborder_and_cannot_be_rewritten_is_named() {
    var group = pricing("CO/0%");
    givenPricings(group);
    var event = new SignsRenamedEvent("CO/01", "CO/X1", 1L, 1L);

    listener.onSignsRenamed(event);

    assertThat(group.getSuborderSign()).isEqualTo("CO/0%");
    assertThat(event.getNotices()).singleElement()
        .satisfies(notice -> assertThat(notice.getErrorCode()).isEqualTo(ErrorCode.BU_PRICING_PATTERN_NOT_FOLLOWED));
  }

  /** A pattern that still covers the renamed suborder needs nothing (#1206). */
  @Test
  void a_pattern_that_still_covers_the_renamed_suborder_is_left_without_a_word() {
    var anySuborder = pricing("CO/%");
    givenPricings(anySuborder);
    var event = new SignsRenamedEvent("CO/01", "CO/X1", 1L, 1L);

    listener.onSignsRenamed(event);

    assertThat(anySuborder.getSuborderSign()).isEqualTo("CO/%");
    assertThat(event.getNotices()).isEmpty();
  }

  /** The rate belongs to the order it was written for; a suborder moved away is named, not followed. */
  @Test
  void a_suborder_moved_to_another_order_leaves_the_pattern_and_is_named() {
    var exact = pricing("CO/01");
    givenPricings(exact);
    var event = new SignsRenamedEvent("CO/01", "OTHER/01", 1L, 2L);

    listener.onSignsRenamed(event);

    assertThat(exact.getSuborderSign()).isEqualTo("CO/01");
    assertThat(event.getNotices()).hasSize(1);
  }

  private void givenPricings(OrderPricing... pricings) {
    when(orderPricingRepository.findByCustomerorderIdOrderByValidFromAsc(1L)).thenReturn(List.of(pricings));
  }

  private static OrderPricing pricing(String suborderPattern) {
    var pricing = new OrderPricing();
    pricing.setCustomerorder(customerorderWithId(1L));
    pricing.setSuborderSign(suborderPattern);
    return pricing;
  }

}
