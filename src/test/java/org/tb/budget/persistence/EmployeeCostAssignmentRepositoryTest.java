package org.tb.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.budget.domain.EmployeeCostAssignment;

/**
 * Following an employee sign (#966): the assignments name their person by it, and a sign that
 * moves has to take them along — one left behind resolves to nothing and costs the work 0 EUR in
 * controlling without a word (#922).
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeCostAssignmentRepositoryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2026, 12, 31);

  @Autowired
  private EmployeeCostAssignmentRepository assignmentRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  public void carries_every_assignment_of_a_sign_over_to_the_new_one() {
    givenAssignment("testy");
    givenAssignment("testy");

    assertThat(assignmentRepository.updateEmployeeSign("testy", "newby")).isEqualTo(2);
    assertThat(assignmentRepository.findDistinctEmployeeSigns()).containsExactly("newby");
  }

  /** Following one sign must not drag the assignments of anybody else along. */
  @Test
  public void leaves_the_assignments_of_other_signs_alone() {
    givenAssignment("testy");
    givenAssignment("boss");

    assignmentRepository.updateEmployeeSign("testy", "newby");

    assertThat(assignmentRepository.findDistinctEmployeeSigns())
        .containsExactlyInAnyOrder("newby", "boss");
  }

  /** Several assignments on one sign are one entry, not one per row — the caller compares signs. */
  @Test
  public void names_a_sign_only_once() {
    givenAssignment("testy");
    givenAssignment("testy");

    assertThat(assignmentRepository.findDistinctEmployeeSigns()).containsExactly("testy");
  }

  private void givenAssignment(String employeeSign) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeCostName("Standard");
    assignment.setEmployeeSign(employeeSign);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    assignmentRepository.save(assignment);
  }

}
