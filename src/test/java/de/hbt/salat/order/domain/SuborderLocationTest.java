package de.hbt.salat.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.common.domain.AuditedEntity;

/** Where a suborder sits, as plain values for another module (#1322). */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SuborderLocationTest {

  @Test
  void the_path_runs_from_the_top_level_down_to_the_suborder() {
    var order = new Customerorder();
    setId(order, 1L);
    order.setSign("CO");
    var top = suborder(11L, order, null, "01");
    var middle = suborder(12L, order, top, "A");
    var leaf = suborder(13L, order, middle, "X");

    var location = SuborderLocation.of(leaf);

    assertThat(location.path()).containsExactly(11L, 12L, 13L);
    assertThat(location.customerorderId()).isEqualTo(1L);
    assertThat(location.completeOrderSign()).isEqualTo("CO/01/A/X");
    assertThat(location.liesWithin(11L)).isTrue();
    assertThat(location.liesWithin(13L)).isTrue();
    assertThat(SuborderLocation.of(top).liesWithin(13L)).isFalse();
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
