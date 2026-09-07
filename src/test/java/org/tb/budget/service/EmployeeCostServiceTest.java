package org.tb.budget.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.tb.budget.domain.EmployeeCost;
import org.tb.budget.domain.EmployeeCostAssignment;
import org.tb.budget.domain.EmployeeCostAssignmentData;
import org.tb.budget.domain.EmployeeCostData;
import org.tb.budget.persistence.EmployeeCostAssignmentRepository;
import org.tb.budget.persistence.EmployeeCostRepository;
import org.tb.common.domain.AuditedEntity;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;

/**
 * Assignments bind their cost rate by name, and several cost records share one name to model a rate
 * that changed over time. Everything that moves a name — renaming, deleting, editing an assignment
 * — has to keep that binding intact, because a broken one is silent: {@code findEffectiveCost}
 * simply resolves nothing and the bookings fall back to 0 EUR in controlling (#922).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostServiceTest {

  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  private final List<EmployeeCost> costs = new ArrayList<>();
  private final List<EmployeeCostAssignment> assignments = new ArrayList<>();

  private EmployeeCostRepository costRepository;
  private EmployeeCostAssignmentRepository assignmentRepository;
  private EmployeeCostService service;

  @BeforeEach
  public void setUp() {
    costRepository = mock(EmployeeCostRepository.class);
    assignmentRepository = mock(EmployeeCostAssignmentRepository.class);
    stubCostRepository();
    stubAssignmentRepository();
    service = new EmployeeCostService(costRepository, assignmentRepository);
  }

  // --- editing an assignment -------------------------------------------------------------------

  @Test
  public void should_reject_an_assignment_overlapping_another_one_of_the_same_employee() {
    var edited = givenAssignment("senior", "emp", null, JUL, DEC, 1L);
    givenAssignment("junior", "emp", null, JAN, JUN, 2L);

    assertThatThrownBy(() -> service.updateAssignment(edited.getId(), assignmentData("senior", "emp", null, JAN, DEC)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_ASSIGNMENT_OVERLAP.getCode());
  }

  @Test
  public void should_accept_an_assignment_edit_in_a_period_no_other_assignment_covers() {
    var edited = givenAssignment("senior", "emp", null, JUL, DEC, 1L);
    givenAssignment("junior", "emp", null, JAN, JUN, 2L);

    service.updateAssignment(edited.getId(), assignmentData("senior", "emp", null, JUL, OPEN_END));

    assertThat(edited.getValidUntil()).isEqualTo(OPEN_END);
  }

  /** The record being edited must not block itself — that is what {@code excludeId} is for. */
  @Test
  public void should_not_let_an_assignment_conflict_with_itself_on_edit() {
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    assertThatCode(() -> service.updateAssignment(edited.getId(),
        assignmentData("senior", "emp", null, JAN, DEC))).doesNotThrowAnyException();
  }

  /** An assignment restricted to a suborder is a scope of its own, next to the general one. */
  @Test
  public void should_accept_narrowing_an_assignment_down_to_a_suborder() {
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("junior", "emp", null, JAN, DEC, 2L);
    // id 2 now holds the general scope, so the edited one has to move out of it
    assignments.get(0).setSuborderSign("co/01");

    service.updateAssignment(edited.getId(), assignmentData("senior", "emp", "co/01", JAN, DEC));

    assertThat(edited.getSuborderSign()).isEqualTo("co/01");
  }

  /** Editing keeps the record, so its audit trail survives — that is the point of #922. */
  @Test
  public void should_edit_an_assignment_in_place_rather_than_replace_it() {
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.updateAssignment(edited.getId(), assignmentData("junior", "other", "co/01", JUL, DEC));

    verify(assignmentRepository, never()).deleteById(anyLong());
    assertThat(edited.getEmployeeCostName()).isEqualTo("junior");
    assertThat(edited.getEmployeeSign()).isEqualTo("other");
    assertThat(edited.getSuborderSign()).isEqualTo("co/01");
    assertThat(edited.getValidFrom()).isEqualTo(JUL);
  }

  @Test
  public void should_store_an_open_ended_assignment_as_the_far_future_date() {
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.updateAssignment(edited.getId(), assignmentData("senior", "emp", null, JAN, null));

    assertThat(edited.getValidUntil()).isEqualTo(OPEN_END);
  }

  // --- renaming a cost rate -------------------------------------------------------------------

  @Test
  public void should_carry_the_assignments_along_when_a_cost_rate_is_renamed() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);
    var own = givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    var foreign = givenAssignment("junior", "other", null, JAN, DEC, 2L);

    service.update(cost.getId(), costData("senior consultant", 8000, JAN, null));

    assertThat(cost.getName()).isEqualTo("senior consultant");
    assertThat(own.getEmployeeCostName()).isEqualTo("senior consultant");
    assertThat(foreign.getEmployeeCostName()).isEqualTo("junior");
  }

  /**
   * A category made of several rate periods is renamed as a whole. Renaming only the edited record
   * would leave its siblings on the old name, and the assignments — which move to the new name —
   * would find no rate for the siblings' periods.
   */
  @Test
  public void should_rename_every_cost_record_sharing_the_old_name() {
    var lastYear = givenCost("senior", 7500, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 1L);
    var thisYear = givenCost("senior", 8000, JAN, OPEN_END, 2L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.update(thisYear.getId(), costData("senior consultant", 8000, JAN, null));

    assertThat(lastYear.getName()).isEqualTo("senior consultant");
    assertThat(thisYear.getName()).isEqualTo("senior consultant");
    assertThat(assignments.get(0).getEmployeeCostName()).isEqualTo("senior consultant");
  }

  /** Renaming merges the group into the target name, so the merged set must stay free of overlaps. */
  @Test
  public void should_reject_a_rename_that_would_overlap_the_target_name() {
    var edited = givenCost("senior", 8000, JAN, DEC, 1L);
    givenCost("junior", 6000, JUL, OPEN_END, 2L);

    assertThatThrownBy(() -> service.update(edited.getId(), costData("junior", 8000, JAN, DEC)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_OVERLAP.getCode());
  }

  @Test
  public void should_accept_a_rename_into_a_period_the_target_name_does_not_cover() {
    var edited = givenCost("senior", 8000, JAN, JUN, 1L);
    givenCost("junior", 6000, JUL, OPEN_END, 2L);

    service.update(edited.getId(), costData("junior", 8000, JAN, JUN));

    assertThat(edited.getName()).isEqualTo("junior");
  }

  /** A sibling must not block the rename either — it moves along, keeping its own period. */
  @Test
  public void should_reject_a_rename_whose_sibling_would_overlap_the_target_name() {
    givenCost("senior", 7500, JAN, JUN, 1L);
    var edited = givenCost("senior", 8000, JUL, DEC, 2L);
    givenCost("junior", 6000, JAN, JUN, 3L);

    assertThatThrownBy(() -> service.update(edited.getId(), costData("junior", 8000, JUL, DEC)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_OVERLAP.getCode());
  }

  /** Without a rename the record keeps checking only itself, and must not block itself. */
  @Test
  public void should_accept_editing_a_cost_rate_without_renaming_it() {
    var cost = givenCost("senior", 8000, JAN, DEC, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.update(cost.getId(), costData("senior", 8500, JAN, DEC));

    assertThat(cost.getName()).isEqualTo("senior");
    assertThat(cost.getCostCentsPerHour()).isEqualTo(8500);
  }

  @Test
  public void should_reject_an_edit_overlapping_another_record_of_the_same_name() {
    givenCost("senior", 7500, JAN, JUN, 1L);
    var edited = givenCost("senior", 8000, JUL, DEC, 2L);

    assertThatThrownBy(() -> service.update(edited.getId(), costData("senior", 8000, JAN, DEC)))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_OVERLAP.getCode());
  }

  // --- deleting a cost rate -------------------------------------------------------------------

  @Test
  public void should_refuse_to_delete_a_cost_rate_that_assignments_reference() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("senior", "other", null, JAN, DEC, 2L);

    assertThatThrownBy(() -> service.delete(cost.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_HAS_ASSIGNMENTS.getCode());
    verify(costRepository, never()).deleteById(anyLong());
  }

  /**
   * Also when a sibling record keeps the name alive: dropping an outdated rate would leave its
   * period uncovered, and the bookings in that period would silently cost 0 EUR.
   */
  @Test
  public void should_refuse_to_delete_an_outdated_rate_of_a_category_still_in_use() {
    var lastYear = givenCost("senior", 7500, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 1L);
    givenCost("senior", 8000, JAN, OPEN_END, 2L);
    givenAssignment("senior", "emp", null, LocalDate.of(2025, 1, 1), OPEN_END, 1L);

    assertThatThrownBy(() -> service.delete(lastYear.getId()))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_HAS_ASSIGNMENTS.getCode());
  }

  @Test
  public void should_name_the_number_of_blocking_assignments() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("senior", "other", null, JAN, DEC, 2L);

    assertThatThrownBy(() -> service.delete(cost.getId()))
        .isInstanceOfSatisfying(BusinessRuleException.class, ex ->
            assertThat(ex.getMessages().get(0).getArguments()).containsExactly("senior", 2L));
  }

  @Test
  public void should_delete_a_cost_rate_no_assignment_references() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("junior", "emp", null, JAN, DEC, 1L);

    service.delete(cost.getId());

    verify(costRepository).deleteById(cost.getId());
  }

  // --- category names for the select boxes -----------------------------------------------------

  @Test
  public void should_offer_every_category_name_once_regardless_of_its_rate_periods() {
    givenCost("senior", 7500, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 1L);
    givenCost("senior", 8000, JAN, OPEN_END, 2L);
    givenCost("junior", 6000, JAN, OPEN_END, 3L);

    assertThat(service.getSelectableCostNames(null)).containsExactly("junior", "senior");
  }

  /**
   * An assignment left behind by a deleted category keeps its name in the select, so that editing it
   * does not silently retarget it to whatever the select happens to preselect.
   */
  @Test
  public void should_keep_a_stored_name_that_no_cost_record_carries_any_more() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThat(service.getSelectableCostNames("orphaned")).containsExactly("orphaned", "senior");
  }

  @Test
  public void should_not_offer_a_stored_name_twice() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThat(service.getSelectableCostNames("senior")).containsExactly("senior");
  }

  // --- test fixture ---------------------------------------------------------------------------

  private EmployeeCost givenCost(String name, int cents, LocalDate from, LocalDate until, long id) {
    var cost = new EmployeeCost();
    cost.setName(name);
    cost.setCostCentsPerHour(cents);
    cost.setValidFrom(from);
    cost.setValidUntil(until);
    setId(cost, id);
    costs.add(cost);
    return cost;
  }

  private EmployeeCostAssignment givenAssignment(String costName, String employeeSign, String suborderSign,
                                                 LocalDate from, LocalDate until, long id) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeCostName(costName);
    assignment.setEmployeeSign(employeeSign);
    assignment.setSuborderSign(suborderSign);
    assignment.setValidFrom(from);
    assignment.setValidUntil(until);
    setId(assignment, id);
    assignments.add(assignment);
    return assignment;
  }

  private static EmployeeCostData costData(String name, int cents, LocalDate from, LocalDate until) {
    return new EmployeeCostData(name, cents, from, until);
  }

  private static EmployeeCostAssignmentData assignmentData(String costName, String employeeSign,
                                                           String suborderSign, LocalDate from, LocalDate until) {
    return new EmployeeCostAssignmentData(costName, employeeSign, suborderSign, from, until);
  }

  /**
   * The stubs answer off the two lists and mirror the JPQL of each query, so that the rules under
   * test are actually exercised. Handing back a fixed list instead would feed the service records
   * the query would never have returned.
   */
  private void stubCostRepository() {
    when(costRepository.findById(anyLong())).thenAnswer(invocation ->
        costs.stream().filter(c -> c.getId().equals(invocation.getArgument(0))).findFirst());
    when(costRepository.findByNameOrderByValidFromAsc(any())).thenAnswer(invocation ->
        costs.stream()
            .filter(c -> c.getName().equals(invocation.<String>getArgument(0)))
            .sorted((a, b) -> a.getValidFrom().compareTo(b.getValidFrom()))
            .toList());
    when(costRepository.findDistinctNames()).thenAnswer(invocation ->
        costs.stream().map(EmployeeCost::getName).distinct().sorted().toList());
    when(costRepository.findOverlapping(any(), any(), any(), any())).thenAnswer(invocation -> {
      String name = invocation.getArgument(0);
      LocalDate from = invocation.getArgument(1);
      LocalDate until = invocation.getArgument(2);
      Long excludeId = invocation.getArgument(3);
      return costs.stream()
          .filter(c -> c.getName().equals(name))
          .filter(c -> !c.getValidFrom().isAfter(until) && !c.getValidUntil().isBefore(from))
          .filter(c -> excludeId == null || !excludeId.equals(c.getId()))
          .toList();
    });
  }

  private void stubAssignmentRepository() {
    when(assignmentRepository.findById(anyLong())).thenAnswer(invocation ->
        assignments.stream().filter(a -> a.getId().equals(invocation.getArgument(0))).findFirst());
    when(assignmentRepository.findByEmployeeCostName(any())).thenAnswer(invocation ->
        assignments.stream()
            .filter(a -> a.getEmployeeCostName().equals(invocation.<String>getArgument(0)))
            .toList());
    when(assignmentRepository.countByEmployeeCostName(any())).thenAnswer(invocation ->
        assignments.stream()
            .filter(a -> a.getEmployeeCostName().equals(invocation.<String>getArgument(0)))
            .count());
    when(assignmentRepository.findOverlapping(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
      String employeeSign = invocation.getArgument(0);
      String suborderSign = invocation.getArgument(1);
      LocalDate from = invocation.getArgument(2);
      LocalDate until = invocation.getArgument(3);
      Long excludeId = invocation.getArgument(4);
      return assignments.stream()
          .filter(a -> a.getEmployeeSign().equals(employeeSign))
          .filter(a -> suborderSign == null
              ? a.getSuborderSign() == null
              : suborderSign.equals(a.getSuborderSign()))
          .filter(a -> !a.getValidFrom().isAfter(until) && !a.getValidUntil().isBefore(from))
          .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
          .toList();
    });
  }

  /** The id is generated, so there is no setter; a stored record always has one. */
  private static void setId(AuditedEntity entity, long id) {
    try {
      var field = AuditedEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("cannot assign an id to the test record", e);
    }
  }

}
