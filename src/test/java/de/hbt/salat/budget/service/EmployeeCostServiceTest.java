package de.hbt.salat.budget.service;

import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;
import de.hbt.salat.budget.domain.EmployeeCostAssignmentData;
import de.hbt.salat.budget.domain.EmployeeCostCategory;
import de.hbt.salat.budget.domain.EmployeeCostData;
import de.hbt.salat.budget.persistence.CostCategoryRepository;
import de.hbt.salat.budget.persistence.EmployeeCostAssignmentRepository;
import de.hbt.salat.budget.persistence.EmployeeCostRepository;
import de.hbt.salat.common.domain.AuditedEntity;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.service.SuborderService;

/**
 * Assignments and rate periods refer to their cost category by id (#1209); several rate periods
 * share one category to model a rate that changed over time. Everything that moves a category —
 * renaming, merging, deleting, editing an assignment — has to keep that binding intact, because a
 * broken one is silent: {@code findEffectiveCost} simply resolves nothing and the bookings fall back
 * to 0 EUR in controlling (#922).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock
public class EmployeeCostServiceTest {

  /** Matches the {@link FixedClock} fixture — the overview and a new rate depend on "today". */
  private static final LocalDate TODAY = LocalDate.of(2026, 6, 25);
  private static final LocalDate JAN = LocalDate.of(2026, 1, 1);
  private static final LocalDate JUN = LocalDate.of(2026, 6, 30);
  private static final LocalDate JUL = LocalDate.of(2026, 7, 1);
  private static final LocalDate DEC = LocalDate.of(2026, 12, 31);
  private static final LocalDate OPEN_END = LocalDate.of(2999, 12, 31);

  /**
   * The people the tests name by sign, and their ids — the assignments reference them by id since
   * #968. Writing the tests with signs keeps them readable; {@code ghost} is nobody.
   */
  private static final Map<String, Long> PEOPLE = Map.of("emp", 1L, "other", 2L);
  private static final long GHOST = 99L;

  private final List<EmployeeCost> costs = new ArrayList<>();
  private final List<EmployeeCostAssignment> assignments = new ArrayList<>();
  private final List<CostCategory> categories = new ArrayList<>();

  private EmployeeCostRepository costRepository;
  private EmployeeCostAssignmentRepository assignmentRepository;
  private CostCategoryRepository categoryRepository;
  private EmployeeService employeeService;
  private SuborderService suborderService;
  private EmployeeCostService service;

  @BeforeEach
  public void setUp() {
    costRepository = mock(EmployeeCostRepository.class);
    assignmentRepository = mock(EmployeeCostAssignmentRepository.class);
    categoryRepository = mock(CostCategoryRepository.class);
    employeeService = mock(EmployeeService.class);
    suborderService = mock(SuborderService.class);
    // The people of PEOPLE exist, nobody else does.
    when(employeeService.getEmployeeById(anyLong())).thenAnswer(invocation -> employee(invocation.getArgument(0)));
    when(employeeService.getSignsByIds(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return PEOPLE.entrySet().stream().filter(person -> ids.contains(person.getValue()))
          .collect(toMap(Map.Entry::getValue, Map.Entry::getKey));
    });
    // The suborders of SUBORDERS exist, nothing else does (#1205: referenced by id).
    when(suborderService.getSuborderById(anyLong())).thenAnswer(invocation -> suborder(invocation.getArgument(0)));
    stubCostRepository();
    stubAssignmentRepository();
    stubCategoryRepository();
    service = new EmployeeCostService(costRepository, assignmentRepository, categoryRepository, employeeService,
        suborderService);
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
    assignments.get(0).setSuborderId(SUBORDERS.get("co/01"));

    service.updateAssignment(edited.getId(), assignmentData("senior", "emp", "co/01", JAN, DEC));

    assertThat(edited.getSuborderSign()).isEqualTo("co/01");
  }

  /** Editing keeps the record, so its audit trail survives — that is the point of #922. */
  @Test
  public void should_edit_an_assignment_in_place_rather_than_replace_it() {
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    // The category it moves to has to exist since #958.
    givenCost("junior", 6000, JUL, DEC, 2L);

    service.updateAssignment(edited.getId(), assignmentData("junior", "other", "co/01", JUL, DEC));

    verify(assignmentRepository, never()).deleteById(anyLong());
    assertThat(edited.getEmployeeCostName()).isEqualTo("junior");
    assertThat(edited.getEmployeeId()).isEqualTo(PEOPLE.get("other"));
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

  // --- the references of an assignment (#958, #968) -------------------------------------------

  /**
   * The form protects the person only as long as the input comes from its select. A post with
   * another id reaches the same endpoint; the foreign key would refuse it too, but only as a failed
   * statement.
   */
  @Test
  public void should_reject_an_assignment_for_an_employee_that_does_not_exist() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThatThrownBy(() -> service.createAssignment(assignmentData("senior", "ghost", null, JAN, DEC)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.EM_NOT_FOUND.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  @Test
  public void should_reject_an_assignment_without_an_employee() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThatThrownBy(() -> service.createAssignment(
        new EmployeeCostAssignmentData("senior", null, null, JAN, DEC)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.EM_NOT_FOUND.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  /**
   * The id is the reference; the sign is written next to it only for the views, ETL definitions and
   * reports that still join on it (#968) — and it is the person's sign, not one the request sends.
   */
  @Test
  public void should_store_the_person_by_id_and_their_current_sign_next_to_it() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    service.createAssignment(assignmentData("senior", "emp", null, JAN, DEC));

    var saved = ArgumentCaptor.forClass(EmployeeCostAssignment.class);
    verify(assignmentRepository).save(saved.capture());
    assertThat(saved.getValue().getEmployeeId()).isEqualTo(PEOPLE.get("emp"));
    assertThat(saved.getValue().getEmployeeSign()).isEqualTo("emp");
  }

  /** Editing an assignment the migration could not resolve is how its person gets picked. */
  @Test
  public void should_resolve_an_unresolved_assignment_when_its_person_is_picked() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    var unresolved = givenAssignment("senior", "gone", null, JAN, DEC, 1L);
    unresolved.setEmployeeId(null);

    service.updateAssignment(unresolved.getId(), assignmentData("senior", "emp", null, JAN, DEC));

    assertThat(unresolved.getEmployeeId()).isEqualTo(PEOPLE.get("emp"));
    assertThat(unresolved.getEmployeeSign()).isEqualTo("emp");
    assertThat(unresolved.isEmployeeUnresolved()).isFalse();
  }

  @Test
  public void should_reject_an_assignment_for_a_suborder_that_does_not_exist() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThatThrownBy(() -> service.createAssignment(assignmentData("senior", "emp", "co/nope", JAN, DEC)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.SO_NOT_FOUND.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  /** No suborder is the normal case: the assignment then applies regardless of suborder. */
  @Test
  public void should_not_ask_for_a_suborder_when_the_assignment_names_none() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    service.createAssignment(assignmentData("senior", "emp", null, JAN, DEC));

    verify(suborderService, never()).getSuborderById(anyLong());
  }

  /** The suborder is stored by id, its complete order sign written from the suborder (#1205). */
  @Test
  public void should_store_the_suborder_by_id_and_write_its_sign_from_the_suborder() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    when(assignmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var stored = service.createAssignment(assignmentData("senior", "emp", "co/01", JAN, DEC));

    assertThat(stored.getSuborderId()).isEqualTo(SUBORDERS.get("co/01"));
    assertThat(stored.getSuborderSign()).isEqualTo("co/01");
  }

  @Test
  public void should_reject_an_assignment_for_a_cost_category_that_does_not_exist() {
    assertThatThrownBy(() -> service.createAssignment(assignmentData("nonexistent", "emp", null, JAN, DEC)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_NAME_UNKNOWN.getCode());
    verify(assignmentRepository, never()).save(any());
  }

  /**
   * An assignment can be the last thing carrying a category name, once the cost rates behind it are
   * deleted (#895). Editing it is the way to move it onto a name that has a rate again, so the
   * check must not stand in the way of exactly that record.
   */
  @Test
  public void should_keep_an_assignment_editable_whose_cost_category_has_no_rate_any_more() {
    var orphaned = givenAssignment("gone", "emp", null, JAN, DEC, 1L);

    service.updateAssignment(orphaned.getId(), assignmentData("gone", "emp", null, JAN, OPEN_END));

    assertThat(orphaned.getValidUntil()).isEqualTo(OPEN_END);
  }

  @Test
  public void should_reject_an_edit_that_moves_an_assignment_to_an_unknown_employee() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    var edited = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    assertThatThrownBy(() -> service.updateAssignment(edited.getId(),
        assignmentData("senior", "ghost", null, JAN, DEC)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.EM_NOT_FOUND.getCode());
    assertThat(edited.getEmployeeId()).isEqualTo(PEOPLE.get("emp"));
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

  // --- the category overview -------------------------------------------------------------------

  @Test
  public void should_list_a_category_with_several_rate_periods_only_once() {
    givenCost("senior", 7500, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 1L);
    givenCost("senior", 8000, JAN, OPEN_END, 2L);
    givenCost("junior", 6000, JAN, OPEN_END, 3L);

    assertThat(service.getCategories()).extracting(EmployeeCostCategory::name)
        .containsExactly("junior", "senior");
  }

  @Test
  public void should_name_the_employees_a_category_is_assigned_to() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("senior", "other", null, JAN, DEC, 2L);

    assertThat(service.getCategories()).singleElement()
        .extracting(EmployeeCostCategory::employeeSigns).asInstanceOf(list(String.class))
        .containsExactly("emp", "other");
  }

  /** Assigned from next month is assigned — the overview answers who is costed this way. */
  @Test
  public void should_name_an_employee_whose_assignment_starts_in_the_future() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JUL, OPEN_END, 1L);

    assertThat(service.getCategories()).singleElement()
        .extracting(EmployeeCostCategory::employeeSigns).asInstanceOf(list(String.class))
        .containsExactly("emp");
  }

  @Test
  public void should_leave_out_an_employee_whose_assignment_has_expired() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, LocalDate.of(2026, 6, 24), 1L);

    assertThat(service.getCategories()).singleElement()
        .satisfies(category -> {
          assertThat(category.employeeSigns()).isEmpty();
          assertThat(category.hasEmployees()).isFalse();
        });
  }

  /** A suborder-specific assignment next to the general one is one employee, not two. */
  @Test
  public void should_name_an_employee_once_regardless_of_the_number_of_assignments() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("senior", "emp", "co/01", JAN, DEC, 2L);

    assertThat(service.getCategories()).singleElement()
        .extracting(EmployeeCostCategory::employeeSigns).asInstanceOf(list(String.class))
        .containsExactly("emp");
  }

  /**
   * An assignment can outlive the rates of its category (#895). The category has to stay in the
   * overview, otherwise that assignment could not be reached through the UI at all any more.
   */
  @Test
  public void should_list_a_category_no_cost_record_carries_any_more() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("orphaned", "emp", null, JAN, DEC, 1L);

    assertThat(service.getCategories()).extracting(EmployeeCostCategory::name)
        .containsExactly("orphaned", "senior");
  }

  // --- creating a category ---------------------------------------------------------------------

  @Test
  public void should_create_a_category_with_a_first_rate_running_from_today_with_an_open_end() {
    service.createCategory("senior", 8000);

    assertThat(costs).singleElement().satisfies(cost -> {
      assertThat(cost.getName()).isEqualTo("senior");
      assertThat(cost.getCostCentsPerHour()).isEqualTo(8000);
      assertThat(cost.getValidFrom()).isEqualTo(TODAY);
      assertThat(cost.getValidUntil()).isEqualTo(OPEN_END);
    });
  }

  @Test
  public void should_reject_a_category_name_a_cost_record_already_carries() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);

    assertThatThrownBy(() -> service.createCategory("senior", 9000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_NAME_EXISTS.getCode());
    assertThat(costs).hasSize(1);
  }

  /** Reusing the name of an orphaned assignment would adopt that assignment unnoticed. */
  @Test
  public void should_reject_a_category_name_an_orphaned_assignment_still_carries() {
    givenAssignment("orphaned", "emp", null, JAN, DEC, 1L);

    assertThatThrownBy(() -> service.createCategory("orphaned", 9000))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_NAME_EXISTS.getCode());
  }

  // --- renaming a category ---------------------------------------------------------------------

  @Test
  public void should_carry_every_rate_period_and_assignment_when_a_category_is_renamed() {
    var lastYear = givenCost("senior", 7500, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), 1L);
    var thisYear = givenCost("senior", 8000, JAN, OPEN_END, 2L);
    var own = givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    var foreign = givenAssignment("junior", "other", null, JAN, DEC, 2L);

    service.renameCategory("senior", "senior consultant");

    assertThat(lastYear.getName()).isEqualTo("senior consultant");
    assertThat(thisYear.getName()).isEqualTo("senior consultant");
    assertThat(own.getEmployeeCostName()).isEqualTo("senior consultant");
    assertThat(foreign.getEmployeeCostName()).isEqualTo("junior");
  }

  /** Renaming onto an existing name merges the categories — only while the periods stay disjoint. */
  @Test
  public void should_reject_a_category_rename_that_would_overlap_the_target_name() {
    givenCost("senior", 8000, JAN, DEC, 1L);
    givenCost("junior", 6000, JUL, OPEN_END, 2L);

    assertThatThrownBy(() -> service.renameCategory("senior", "junior"))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_OVERLAP.getCode());
  }

  @Test
  public void should_accept_a_category_rename_into_a_period_the_target_name_does_not_cover() {
    var edited = givenCost("senior", 8000, JAN, JUN, 1L);
    givenCost("junior", 6000, JUL, OPEN_END, 2L);

    service.renameCategory("senior", "junior");

    assertThat(edited.getName()).isEqualTo("junior");
  }

  /** A category that only assignments carry has no rate to move — the assignments still move. */
  @Test
  public void should_rename_a_category_no_cost_record_carries() {
    var orphaned = givenAssignment("orphaned", "emp", null, JAN, DEC, 1L);

    service.renameCategory("orphaned", "senior");

    assertThat(orphaned.getEmployeeCostName()).isEqualTo("senior");
  }

  // --- the category as its own record (#1209) --------------------------------------------------

  /**
   * A rename is the category's own row: rate periods and assignments keep pointing at the same id.
   * Their name columns follow only as mirrors for the views, ETL definitions and reports joining on
   * them.
   */
  @Test
  public void should_rename_the_category_row_and_let_the_mirrors_follow() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);
    var assignment = givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    var category = cost.getCategory();

    service.renameCategory("senior", "senior consultant");

    assertThat(categories).containsExactly(category);
    assertThat(category.getName()).isEqualTo("senior consultant");
    assertThat(cost.getCategory()).isSameAs(category);
    assertThat(assignment.getCategory()).isSameAs(category);
    assertThat(mirror(cost, "name")).isEqualTo("senior consultant");
    assertThat(mirror(assignment, "employeeCostName")).isEqualTo("senior consultant");
  }

  /** Renaming onto an existing name merges: everything moves over, and one category is left. */
  @Test
  public void should_merge_into_the_category_that_carries_the_new_name() {
    var senior = givenCost("senior", 8000, JAN, JUN, 1L);
    var junior = givenCost("junior", 6000, JUL, OPEN_END, 2L);
    var assignment = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.renameCategory("senior", "junior");

    assertThat(categories).containsExactly(junior.getCategory());
    assertThat(senior.getCategory()).isSameAs(junior.getCategory());
    assertThat(assignment.getCategory()).isSameAs(junior.getCategory());
    assertThat(mirror(assignment, "employeeCostName")).isEqualTo("junior");
  }

  /** A rate period goes into a category that exists; a mistyped name no longer makes a new one. */
  @Test
  public void should_refuse_a_rate_period_for_a_name_no_category_carries() {
    givenCost("senior", 8000, JAN, JUN, 1L);

    assertThatThrownBy(() -> service.create(costData("senoir", 9000, JUL, null)))
        .isInstanceOf(InvalidDataException.class)
        .hasMessageContaining(ErrorCode.BU_EMPLOYEE_COST_NAME_UNKNOWN.getCode());
    assertThat(categories).extracting(CostCategory::getName).containsExactly("senior");
  }

  /** As while a category was only a name: it is listed as long as a rate period or an assignment carries it. */
  @Test
  public void should_drop_a_category_with_its_last_rate_period() {
    var cost = givenCost("senior", 8000, JAN, OPEN_END, 1L);

    service.delete(cost.getId());

    assertThat(categories).isEmpty();
    assertThat(service.getCategories()).isEmpty();
  }

  @Test
  public void should_drop_a_category_without_rate_periods_with_its_last_assignment() {
    var orphaned = givenAssignment("orphaned", "emp", null, JAN, DEC, 1L);

    service.deleteAssignment(orphaned.getId());

    assertThat(categories).isEmpty();
  }

  @Test
  public void should_keep_a_category_that_still_has_rate_periods_when_its_last_assignment_goes() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    var assignment = givenAssignment("senior", "emp", null, JAN, DEC, 1L);

    service.deleteAssignment(assignment.getId());

    assertThat(categories).extracting(CostCategory::getName).containsExactly("senior");
  }

  // --- test fixture ---------------------------------------------------------------------------

  /** The mirror column of an entity, which the application writes but never reads (#1209). */
  private static Object mirror(Object entity, String field) {
    try {
      var declared = entity.getClass().getDeclaredField(field);
      declared.setAccessible(true);
      return declared.get(entity);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  private EmployeeCost givenCost(String name, int cents, LocalDate from, LocalDate until, long id) {
    var cost = new EmployeeCost();
    cost.setCategory(category(name));
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
    assignment.setCategory(category(costName));
    assignment.setEmployeeId(idOf(employeeSign));
    assignment.setEmployeeSign(employeeSign);
    assignment.setSuborderSign(suborderSign);
    assignment.setSuborderId(suborderSign == null ? null : SUBORDERS.get(suborderSign));
    assignment.setValidFrom(from);
    assignment.setValidUntil(until);
    setId(assignment, id);
    assignments.add(assignment);
    return assignment;
  }

  /** The stored category of the name, created on first use like the migration does for every name. */
  private CostCategory category(String name) {
    return categories.stream().filter(c -> c.getName().equals(name)).findFirst().orElseGet(() -> {
      var category = new CostCategory(name);
      setId(category, 1000L + categories.size());
      categories.add(category);
      return category;
    });
  }

  private static EmployeeCostData costData(String name, int cents, LocalDate from, LocalDate until) {
    return new EmployeeCostData(name, cents, from, until);
  }

  private static EmployeeCostAssignmentData assignmentData(String costName, String employeeSign,
                                                           String suborderSign, LocalDate from, LocalDate until) {
    return new EmployeeCostAssignmentData(costName, idOf(employeeSign),
        suborderSign == null ? null : SUBORDERS.getOrDefault(suborderSign, NO_SUBORDER), from, until);
  }

  /** The suborders that exist, by complete order sign; an id nothing answers to stands for one that does not. */
  private static final Map<String, Long> SUBORDERS = Map.of("co/01", 51L);
  private static final long NO_SUBORDER = 99L;

  private static Suborder suborder(long id) {
    return SUBORDERS.entrySet().stream().filter(entry -> entry.getValue() == id).findFirst()
        .map(entry -> {
          var customerorder = new Customerorder();
          customerorder.setSign("co");
          var suborder = new Suborder();
          suborder.setCustomerorder(customerorder);
          suborder.setSign(entry.getKey().substring("co/".length()));
          setId(suborder, id);
          return suborder;
        })
        .orElse(null);
  }

  private static long idOf(String employeeSign) {
    return PEOPLE.getOrDefault(employeeSign, GHOST);
  }

  private static Employee employee(long id) {
    return PEOPLE.entrySet().stream().filter(person -> person.getValue() == id).findFirst()
        .map(person -> {
          var employee = new Employee();
          setId(employee, id);
          employee.setSign(person.getKey());
          return employee;
        })
        .orElse(null);
  }

  /**
   * The stubs answer off the two lists and mirror the JPQL of each query, so that the rules under
   * test are actually exercised. Handing back a fixed list instead would feed the service records
   * the query would never have returned.
   */
  private void stubCostRepository() {
    when(costRepository.findById(anyLong())).thenAnswer(invocation ->
        costs.stream().filter(c -> c.getId().equals(invocation.getArgument(0))).findFirst());
    when(costRepository.findByCategoryIdOrderByValidFromAsc(anyLong())).thenAnswer(invocation ->
        costs.stream()
            .filter(c -> c.getCategory().getId().equals(invocation.<Long>getArgument(0)))
            .sorted((a, b) -> a.getValidFrom().compareTo(b.getValidFrom()))
            .toList());
    when(costRepository.countByCategoryId(anyLong())).thenAnswer(invocation ->
        costs.stream().filter(c -> c.getCategory().getId().equals(invocation.<Long>getArgument(0))).count());
    when(costRepository.findDistinctNames()).thenAnswer(invocation ->
        costs.stream().map(EmployeeCost::getName).distinct().sorted().toList());
    doAnswer(invocation -> costs.removeIf(c -> c.getId().equals(invocation.<Long>getArgument(0))))
        .when(costRepository).deleteById(anyLong());
    when(costRepository.save(any())).thenAnswer(invocation -> {
      EmployeeCost saved = invocation.getArgument(0);
      if (costs.stream().noneMatch(c -> c == saved)) {
        setId(saved, costs.size() + 1L);
        costs.add(saved);
      }
      return saved;
    });
    when(costRepository.findOverlapping(anyLong(), any(), any(), any())).thenAnswer(invocation -> {
      long categoryId = invocation.getArgument(0);
      LocalDate from = invocation.getArgument(1);
      LocalDate until = invocation.getArgument(2);
      Long excludeId = invocation.getArgument(3);
      return costs.stream()
          .filter(c -> c.getCategory().getId() == categoryId)
          .filter(c -> !c.getValidFrom().isAfter(until) && !c.getValidUntil().isBefore(from))
          .filter(c -> excludeId == null || !excludeId.equals(c.getId()))
          .toList();
    });
  }

  // --- assignments the migration could not resolve (#968) --------------------------------------

  /** It costs nobody's work, so the overview names nobody for it — the category page marks it. */
  @Test
  public void should_name_nobody_for_an_assignment_whose_person_is_unresolved() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L);
    givenAssignment("senior", "gone", null, JAN, DEC, 2L).setEmployeeId(null);

    assertThat(service.getCategories()).singleElement()
        .extracting(EmployeeCostCategory::employeeSigns).asInstanceOf(list(String.class))
        .containsExactly("emp");
  }

  /** The overview shows the person's current sign, not the one stored with the assignment. */
  @Test
  public void should_name_an_employee_by_their_current_sign() {
    givenCost("senior", 8000, JAN, OPEN_END, 1L);
    givenAssignment("senior", "emp", null, JAN, DEC, 1L).setEmployeeSign("old-sign");

    assertThat(service.getCategories()).singleElement()
        .extracting(EmployeeCostCategory::employeeSigns).asInstanceOf(list(String.class))
        .containsExactly("emp");
  }

  private void stubAssignmentRepository() {
    when(assignmentRepository.findById(anyLong())).thenAnswer(invocation ->
        assignments.stream().filter(a -> a.getId().equals(invocation.getArgument(0))).findFirst());
    when(assignmentRepository.findAllByOrderByCategoryNameAscIdAsc()).thenAnswer(invocation ->
        assignments.stream()
            .sorted(Comparator.comparing(EmployeeCostAssignment::getEmployeeCostName)
                .thenComparing(EmployeeCostAssignment::getId))
            .toList());
    when(assignmentRepository.findByCategoryId(anyLong())).thenAnswer(invocation ->
        assignments.stream()
            .filter(a -> a.getCategory().getId().equals(invocation.<Long>getArgument(0)))
            .toList());
    when(assignmentRepository.countByCategoryId(anyLong())).thenAnswer(invocation ->
        assignments.stream()
            .filter(a -> a.getCategory().getId().equals(invocation.<Long>getArgument(0)))
            .count());
    doAnswer(invocation -> assignments.removeIf(a -> a.getId().equals(invocation.<Long>getArgument(0))))
        .when(assignmentRepository).deleteById(anyLong());
    when(assignmentRepository.findOverlapping(anyLong(), any(), any(), any(), any())).thenAnswer(invocation -> {
      long employeeId = invocation.getArgument(0);
      Long suborderId = invocation.getArgument(1);
      LocalDate from = invocation.getArgument(2);
      LocalDate until = invocation.getArgument(3);
      Long excludeId = invocation.getArgument(4);
      return assignments.stream()
          .filter(a -> a.getEmployeeId() != null && a.getEmployeeId() == employeeId)
          .filter(a -> suborderId == null
              ? a.getSuborderSign() == null
              : suborderId.equals(a.getSuborderId()))
          .filter(a -> !a.getValidFrom().isAfter(until) && !a.getValidUntil().isBefore(from))
          .filter(a -> excludeId == null || !excludeId.equals(a.getId()))
          .toList();
    });
  }

  private void stubCategoryRepository() {
    when(categoryRepository.findByName(any())).thenAnswer(invocation ->
        categories.stream().filter(c -> c.getName().equals(invocation.<String>getArgument(0))).findFirst());
    when(categoryRepository.existsByName(any())).thenAnswer(invocation ->
        categories.stream().anyMatch(c -> c.getName().equals(invocation.<String>getArgument(0))));
    when(categoryRepository.findAllByOrderByNameAsc()).thenAnswer(invocation ->
        categories.stream().sorted(Comparator.comparing(CostCategory::getName)).toList());
    when(categoryRepository.save(any())).thenAnswer(invocation -> {
      CostCategory saved = invocation.getArgument(0);
      if (categories.stream().noneMatch(c -> c == saved)) {
        setId(saved, 1000L + categories.size());
        categories.add(saved);
      }
      return saved;
    });
    doAnswer(invocation -> categories.remove(invocation.<CostCategory>getArgument(0)))
        .when(categoryRepository).delete(any());
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
