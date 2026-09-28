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
import de.hbt.salat.budget.domain.EmployeeCostAssignment;

/**
 * The sign column next to the id (#968): the application resolves by {@code employee_id}, but views,
 * ETL definitions and reports still join on {@code employee_sign}, so a sign change has to reach it
 * — for the person's rows and for nobody else's.
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

  private List<String> signs() {
    entityManager.clear();
    return StreamSupport.stream(assignmentRepository.findAll().spliterator(), false)
        .map(EmployeeCostAssignment::getEmployeeSign)
        .toList();
  }

  private void givenAssignment(Long employeeId, String employeeSign) {
    var assignment = new EmployeeCostAssignment();
    assignment.setEmployeeCostName("Standard");
    assignment.setEmployeeId(employeeId);
    assignment.setEmployeeSign(employeeSign);
    assignment.setValidFrom(FROM);
    assignment.setValidUntil(UNTIL);
    assignmentRepository.save(assignment);
  }

}
