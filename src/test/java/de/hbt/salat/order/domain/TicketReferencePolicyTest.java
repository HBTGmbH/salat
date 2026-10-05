package de.hbt.salat.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static de.hbt.salat.order.domain.TicketReferenceMode.LIMITED;
import static de.hbt.salat.order.domain.TicketReferenceMode.NONE;
import static de.hbt.salat.order.domain.TicketReferenceMode.UNLIMITED;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * How many ticket references a booking may carry (#1326): set on the order, overridden by a suborder,
 * inherited from the nearest suborder above with a setting, else from the order.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TicketReferencePolicyTest {

  private static final TicketReferencePolicy NO_REFERENCE = new TicketReferencePolicy(NONE, null);
  private static final TicketReferencePolicy AT_MOST_TWO = new TicketReferencePolicy(LIMITED, 2);
  private static final TicketReferencePolicy ANY = new TicketReferencePolicy(UNLIMITED, null);

  @Test
  void none_at_most_and_any_number() {
    assertThat(NO_REFERENCE.permits(0)).isTrue();
    assertThat(NO_REFERENCE.permits(1)).isFalse();
    assertThat(AT_MOST_TWO.permits(2)).isTrue();
    assertThat(AT_MOST_TWO.permits(3)).isFalse();
    assertThat(ANY.permits(50)).isTrue();
  }

  @Test
  void what_is_left_next_to_the_references_already_there() {
    assertThat(NO_REFERENCE.remaining(0)).isZero();
    assertThat(AT_MOST_TWO.remaining(1)).isEqualTo(1);
    assertThat(AT_MOST_TWO.remaining(3)).isZero();
    assertThat(ANY.remaining(9)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void a_new_order_allows_any_number() {
    assertThat(new Customerorder().getTicketReferencePolicy()).isEqualTo(ANY);
  }

  @Test
  void at_most_needs_a_number_of_at_least_one() {
    assertThat(TicketReferencePolicy.of(LIMITED, 0)).isNull();
    assertThat(TicketReferencePolicy.of(LIMITED, null)).isNull();
    assertThat(TicketReferencePolicy.of(null, 3)).isNull();
    assertThat(TicketReferencePolicy.of(LIMITED, 3)).isEqualTo(new TicketReferencePolicy(LIMITED, 3));
    assertThatThrownBy(() -> new TicketReferencePolicy(LIMITED, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_number_counts_for_at_most_only() {
    assertThat(TicketReferencePolicy.of(UNLIMITED, 3).limit()).isNull();
    assertThat(TicketReferencePolicy.of(NONE, 3).limit()).isNull();
  }

  @Test
  void a_suborder_without_a_setting_takes_the_orders() {
    var order = order(AT_MOST_TWO);
    var suborder = suborder(order, null, null);

    assertThat(suborder.getTicketReferencePolicy()).isNull();
    assertThat(suborder.getEffectiveTicketReferencePolicy()).isEqualTo(AT_MOST_TWO);
  }

  @Test
  void the_nearest_suborder_above_with_a_setting_wins_over_the_order() {
    var order = order(NO_REFERENCE);
    var top = suborder(order, null, ANY);
    var middle = suborder(order, top, null);
    var bottom = suborder(order, middle, null);

    assertThat(bottom.getEffectiveTicketReferencePolicy()).isEqualTo(ANY);
    middle.setTicketReferencePolicy(AT_MOST_TWO);
    assertThat(bottom.getEffectiveTicketReferencePolicy()).isEqualTo(AT_MOST_TWO);
  }

  @Test
  void the_own_setting_wins() {
    var order = order(ANY);
    var top = suborder(order, null, ANY);
    var own = suborder(order, top, NO_REFERENCE);

    assertThat(own.getEffectiveTicketReferencePolicy()).isEqualTo(NO_REFERENCE);
  }

  @Test
  void inheriting_again_drops_the_own_setting() {
    var order = order(AT_MOST_TWO);
    var suborder = suborder(order, null, ANY);

    suborder.setTicketReferencePolicy(null);

    assertThat(suborder.getEffectiveTicketReferencePolicy()).isEqualTo(AT_MOST_TWO);
  }

  /** What the suborder form shows under its field: the inherited setting and where it comes from. */
  @Test
  void the_source_of_an_inherited_setting() {
    var order = order(AT_MOST_TWO);
    order.setSign("4711");
    var top = suborder(order, null, ANY);
    top.setSign("20");
    var middle = suborder(order, top, null);
    middle.setSign("1");
    top.deriveCompleteOrderSign();
    middle.deriveCompleteOrderSign();

    var fromParent = TicketReferencePolicySource.inheritedBy(order, middle);
    assertThat(fromParent.policy()).isEqualTo(ANY);
    assertThat(fromParent.fromOrder()).isFalse();
    assertThat(fromParent.sourceSign()).isEqualTo("4711/20");

    var atTheTop = TicketReferencePolicySource.inheritedBy(order, null);
    assertThat(atTheTop.policy()).isEqualTo(AT_MOST_TWO);
    assertThat(atTheTop.fromOrder()).isTrue();
    assertThat(atTheTop.sourceSign()).isEqualTo("4711");
  }

  private static Customerorder order(TicketReferencePolicy policy) {
    var order = new Customerorder();
    order.setTicketReferencePolicy(policy);
    return order;
  }

  private static Suborder suborder(Customerorder order, Suborder parent, TicketReferencePolicy policy) {
    var suborder = new Suborder();
    suborder.setCustomerorder(order);
    suborder.setParentorder(parent);
    suborder.setTicketReferencePolicy(policy);
    return suborder;
  }
}
