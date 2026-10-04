package de.hbt.salat.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;

/**
 * The sign column next to the id (#968): the application resolves by {@code employee_id}, but views,
 * ETL definitions and reports still join on {@code employee_sign}, so a sign change has to reach it
 * — for the person's rows and for nobody else's.
 *
 * <p>And the steps of the resolution as JPQL (#1343): suborder, customer order, general — each step its
 * own scope, for the overlap check as much as for finding the effective assignment.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostAssignmentRepositoryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);
  private static final long TESTY = 1L;
  private static final long BOSS = 2L;

  @Autowired
  private EmployeeCostAssignmentRepository assignmentRepository;

  @Autowired
  private CostCategoryRepository categoryRepository;

  /** The update is a bulk statement the persistence context does not see; clearing it reads what is stored. */
  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  public void writes_the_new_sign_into_every_assignment_of_the_person() {
    givenAssignment(TESTY, "testy");
    givenAssignment(TESTY, "testy");

    assertThat(assignmentRepository.updateEmployeeSign(TESTY, "newby")).isEqualTo(2);
    assertThat(signs()).containsExactly("newby", "newby");
  }

  /** Following one person must not drag the assignments of anybody else along. */
  @Test
  public void leaves_the_assignments_of_other_people_alone() {
    givenAssignment(TESTY, "testy");
    givenAssignment(BOSS, "boss");

    assignmentRepository.updateEmployeeSign(TESTY, "newby");

    assertThat(signs()).containsExactlyInAnyOrder("newby", "boss");
  }

  /**
   * An assignment the migration could not resolve carries no id, only its sign — even when a person
   * happens to have carried that very sign before. It is left as it is.
   */
  @Test
  public void leaves_an_assignment_without_a_person_alone() {
    givenAssignment(null, "testy");

    assertThat(assignmentRepository.updateEmployeeSign(TESTY, "newby")).isZero();
    assertThat(signs()).containsExactly("testy");
  }

  // --- the steps of the resolution (#1343) ---------------------------------------------------------

  private static final long ORDER = 10L;
  private static final long SUBORDER = 100L;

  @Test
  public void finds_the_general_assignment_only_among_those_for_neither_order_nor_suborder() {
    var general = givenScopedAssignment(null, null);
    givenScopedAssignment(ORDER, null);
    givenScopedAssignment(null, SUBORDER);

    assertThat(assignmentRepository.findEffectiveGeneral(TESTY, FROM)).extracting(EmployeeCostAssignment::getId)
        .containsExactly(general.getId());
  }

  @Test
  public void finds_the_assignment_to_the_order() {
    givenScopedAssignment(null, null);
    var toOrder = givenScopedAssignment(ORDER, null);

    assertThat(assignmentRepository.findEffectiveCustomerorderSpecific(TESTY, ORDER, FROM))
        .extracting(EmployeeCostAssignment::getId).containsExactly(toOrder.getId());
    assertThat(assignmentRepository.findEffectiveCustomerorderSpecific(TESTY, ORDER + 1, FROM)).isEmpty();
  }

  @Test
  public void counts_and_finds_the_assignments_to_an_order() {
    var toOrder = givenScopedAssignment(ORDER, null);
    givenScopedAssignment(null, SUBORDER);

    assertThat(assignmentRepository.countByCustomerorderId(ORDER)).isEqualTo(1);
    assertThat(assignmentRepository.findByCustomerorderId(ORDER)).extracting(EmployeeCostAssignment::getId)
        .containsExactly(toOrder.getId());
  }

  @Test
  public void checks_overlaps_within_one_step_only() {
    var general = givenScopedAssignment(null, null);
    var toOrder = givenScopedAssignment(ORDER, null);
    var toSuborder = givenScopedAssignment(null, SUBORDER);

    assertThat(overlapping(null, null)).containsExactly(general.getId());
    assertThat(overlapping(ORDER, null)).containsExactly(toOrder.getId());
    assertThat(overlapping(null, SUBORDER)).containsExactly(toSuborder.getId());
    assertThat(overlapping(ORDER + 1, null)).isEmpty();
  }

  private List<Long> overlapping(Long customerorderId, Long suborderId) {
    return assignmentRepository.findOverlapping(TESTY, customerorderId, suborderId, FROM, UNTIL, null).stream()
        .map(EmployeeCostAssignment::getId)
        .toList();
  }

  private EmployeeCostAssignment givenScopedAssignment(Long customerorderId, Long suborderId) {
    var assignment = new EmployeeCostAssignment();
    assignment.setCategory(categoryRepository.findByName("Standard")
        .orElseGet(() -> categoryRepository.save(new CostCategory("Standard"))));
    assignment.setEmployeeId(TESTY);
    assignment.setEmployeeSign("testy");
    assignment.setCustomerorderId(customerorderId);
    assignment.setSuborderId(suborderId);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    return assignmentRepository.save(assignment);
  }

  private List<String> signs() {
    entityManager.clear();
    return StreamSupport.stream(assignmentRepository.findAll().spliterator(), false)
        .map(EmployeeCostAssignment::getEmployeeSign)
        .toList();
  }

  private void givenAssignment(Long employeeId, String employeeSign) {
    var assignment = new EmployeeCostAssignment();
    assignment.setCategory(categoryRepository.findByName("Standard")
        .orElseGet(() -> categoryRepository.save(new CostCategory("Standard"))));
    assignment.setEmployeeId(employeeId);
    assignment.setEmployeeSign(employeeSign);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    assignmentRepository.save(assignment);
  }

}
