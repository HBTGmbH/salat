package de.hbt.salat.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;

/** A suborder as plain values for another module, computed by this module's rules (#1338). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SuborderReadModelTest {

  @Test
  void path_and_complete_sign_are_those_of_the_entity() {
    var order = order();
    var top = suborder(11L, order, null, "01");
    var middle = suborder(12L, order, top, "A");
    var leaf = suborder(13L, order, middle, "X");

    var summary = SuborderReadModel.of(leaf, byId(top, middle, leaf));

    assertThat(summary.id()).isEqualTo(13L);
    assertThat(summary.customerorderId()).isEqualTo(1L);
    assertThat(summary.path()).containsExactly(11L, 12L, 13L);
    assertThat(summary.completeOrderSign()).isEqualTo("CO/01/A/X").isEqualTo(leaf.getCompleteOrderSign());
    assertThat(summary.liesWithin(11L)).isTrue();
    assertThat(SuborderReadModel.of(top, byId(top, middle, leaf)).liesWithin(13L)).isFalse();
  }

  /**
   * The parents come from the suborders of the order read together, by id — not from the entity's
   * parent reference, which may be an uninitialized proxy whose walk would cost a statement per level.
   */
  @Test
  void the_parents_are_read_from_the_suborders_of_the_order() {
    var order = order();
    var top = suborder(11L, order, null, "01");
    var reference = suborder(11L, order, null, "stale");
    var leaf = suborder(13L, order, reference, "X");

    var summary = SuborderReadModel.of(leaf, byId(top, leaf));

    assertThat(summary.completeOrderSign()).isEqualTo("CO/01/X");
  }

  /** A hidden parent still belongs to the path; only the hidden suborder itself is left out by the service. */
  @Test
  void a_hidden_parent_stays_on_the_path() {
    var order = order();
    var top = suborder(11L, order, null, "01");
    top.setHide(true);
    var leaf = suborder(13L, order, top, "X");

    var summary = SuborderReadModel.of(leaf, byId(top, leaf));

    assertThat(summary.path()).containsExactly(11L, 13L);
    assertThat(summary.hide()).isFalse();
  }

  @Test
  void the_values_are_those_of_the_entity() {
    var order = order();
    order.setOrderType(OrderType.BEREITSCHAFT);
    var suborder = suborder(11L, order, null, "01");
    suborder.setShortdescription("short");
    suborder.setDebithours(Duration.ofHours(40));
    suborder.setInvoice('Y');

    var summary = SuborderReadModel.of(suborder, byId(suborder));

    assertThat(summary.shortdescription()).isEqualTo("short");
    assertThat(summary.debithours()).isEqualTo(Duration.ofHours(40));
    assertThat(summary.invoiceable()).isTrue();
    // without a type of its own the suborder takes the order's
    assertThat(summary.effectiveOrderType()).isEqualTo(OrderType.BEREITSCHAFT);
  }

  private static Customerorder order() {
    var order = new Customerorder();
    setId(order, 1L);
    order.setSign("CO");
    return order;
  }

  private static Map<Long, Suborder> byId(Suborder... suborders) {
    var byId = new HashMap<Long, Suborder>();
    for (var suborder : suborders) {
      byId.put(suborder.getId(), suborder);
    }
    return byId;
  }

  private static Suborder suborder(long id, Customerorder order, Suborder parent, String sign) {
    var suborder = new Suborder();
    setId(suborder, id);
    suborder.setCustomerorder(order);
    suborder.setParentorder(parent);
    suborder.setSign(sign);
    return suborder;
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
