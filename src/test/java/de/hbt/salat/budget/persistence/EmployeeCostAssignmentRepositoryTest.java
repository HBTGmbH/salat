package de.hbt.salat.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.budget.domain.CostCategory;
import de.hbt.salat.budget.domain.EmployeeCostAssignment;

/**
 * The steps of the resolution as JPQL (#1343): suborder, customer order, general — each step its
 * own scope, for the overlap check as much as for finding the effective assignment.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostAssignmentRepositoryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);
  private static final long TESTY = 1L;

  @Autowired
  private EmployeeCostAssignmentRepository assignmentRepository;

  @Autowired
  private CostCategoryRepository categoryRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

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
    assignment.setCustomerorderId(customerorderId);
    assignment.setSuborderId(suborderId);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    return assignmentRepository.save(assignment);
  }

}
