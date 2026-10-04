package de.hbt.salat.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import de.hbt.salat.common.command.CommandPublisher;
import de.hbt.salat.order.domain.SuborderSearchRow;
import de.hbt.salat.order.domain.SuborderSignRow;
import de.hbt.salat.order.persistence.SuborderDAO;
import de.hbt.salat.order.persistence.SuborderRepository;

/**
 * The complete sign of a suborder the palette found ({@code ORDER/SUB/SUBSUB}, as
 * {@code Suborder#getCompleteOrderSign()} builds it), from plain rows instead of entities (#1157).
 * The parent chains of all rows are read together, one query per level rather than one per suborder
 * and level; a parent that is one of the rows or was read on an earlier level is not read again.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class CompleteOrderSignsTest {

  private static final String ORDER_SIGN = "MUSTER-01";

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private CommandPublisher commandPublisher;

  @Mock
  private SuborderDAO suborderDAO;

  @Mock
  private SuborderRepository suborderRepository;

  @Mock
  private CustomerorderService customerorderService;

  private SuborderService suborderService;

  @BeforeEach
  void setUp() {
    suborderService = new SuborderService(eventPublisher, commandPublisher, suborderDAO, suborderRepository,
        customerorderService, mock(SpecialOrders.class));
  }

  @Test
  void puts_the_order_sign_before_a_top_level_suborder_without_asking_the_database() {
    var signs = suborderService.getCompleteOrderSigns(List.of(row(1L, "01", null)));

    assertThat(signs).isEqualTo(Map.of(1L, "MUSTER-01/01"));
    verifyNoInteractions(suborderRepository);
  }

  @Test
  void reads_the_parent_chain_one_level_at_a_time() {
    when(suborderRepository.findSignRows(Set.of(2L))).thenReturn(List.of(new SuborderSignRow(2L, "02", 1L)));
    when(suborderRepository.findSignRows(Set.of(1L))).thenReturn(List.of(new SuborderSignRow(1L, "01", null)));

    var signs = suborderService.getCompleteOrderSigns(List.of(row(3L, "03", 2L)));

    assertThat(signs).isEqualTo(Map.of(3L, "MUSTER-01/01/02/03"));
    var inOrder = inOrder(suborderRepository);
    inOrder.verify(suborderRepository).findSignRows(Set.of(2L));
    inOrder.verify(suborderRepository).findSignRows(Set.of(1L));
    verifyNoMoreInteractions(suborderRepository);
  }

  /**
   * Two rows below the same parent and one below another: one query for the level, not one per
   * row, and the grandparent they share is read once.
   */
  @Test
  void reads_a_level_once_for_all_rows_together() {
    when(suborderRepository.findSignRows(Set.of(2L, 5L))).thenReturn(List.of(
        new SuborderSignRow(2L, "02", 1L), new SuborderSignRow(5L, "05", 1L)));
    when(suborderRepository.findSignRows(Set.of(1L))).thenReturn(List.of(new SuborderSignRow(1L, "01", null)));

    var signs = suborderService.getCompleteOrderSigns(List.of(row(10L, "A", 2L), row(11L, "B", 2L), row(12L, "C", 5L)));

    assertThat(signs).isEqualTo(Map.of(
        10L, "MUSTER-01/01/02/A",
        11L, "MUSTER-01/01/02/B",
        12L, "MUSTER-01/01/05/C"));
    verify(suborderRepository).findSignRows(Set.of(2L, 5L));
    verify(suborderRepository).findSignRows(Set.of(1L));
    verifyNoMoreInteractions(suborderRepository);
  }

  @Test
  void takes_a_parent_from_the_rows_without_reading_it() {
    var signs = suborderService.getCompleteOrderSigns(List.of(row(1L, "01", null), row(2L, "02", 1L)));

    assertThat(signs).isEqualTo(Map.of(1L, "MUSTER-01/01", 2L, "MUSTER-01/01/02"));
    verifyNoInteractions(suborderRepository);
  }

  @Test
  void returns_no_signs_for_no_rows() {
    assertThat(suborderService.getCompleteOrderSigns(List.of())).isEmpty();
    verifyNoInteractions(suborderRepository);
  }

  /** The data holds no cycle, but should one appear, the walk ends rather than running forever. */
  @Test
  @Timeout(value = 5, threadMode = SEPARATE_THREAD)
  void ends_the_walk_along_a_cyclic_parent_chain() {
    when(suborderRepository.findSignRows(Set.of(2L))).thenReturn(List.of(new SuborderSignRow(2L, "02", 1L)));

    var signs = suborderService.getCompleteOrderSigns(List.of(row(1L, "01", 2L)));

    assertThat(signs.get(1L)).startsWith("MUSTER-01/").endsWith("/02/01");
  }

  private static SuborderSearchRow row(long id, String sign, Long parentId) {
    return new SuborderSearchRow(id, sign, "Leistung", parentId, 100L, ORDER_SIGN, "Wartungsvertrag", "MK",
        false, false, null);
  }
}
