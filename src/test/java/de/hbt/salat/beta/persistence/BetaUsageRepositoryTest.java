package de.hbt.salat.beta.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.beta.domain.BetaUsage;
import de.hbt.salat.beta.domain.BetaVariant;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;

/**
 * The counter of a beta (#1447) against the database: the increment and the unique key that settles
 * two requests creating the same row at once.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class BetaUsageRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 25);

  @Autowired
  private BetaUsageRepository repository;

  @Autowired
  private EmployeeRepository employeeRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employee employee;

  @BeforeEach
  void setUp() {
    employee = new Employee();
    employee.setSign("bt");
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee = employeeRepository.save(employee);
  }

  @Test
  void the_increment_finds_no_row_before_the_first_use_and_raises_it_after() {
    assertThat(repository.increment("test-beta", "applied", employee.getId(), DAY, BetaVariant.BETA)).isZero();

    repository.saveAndFlush(usage(BetaVariant.BETA));
    var raised = repository.increment("test-beta", "applied", employee.getId(), DAY, BetaVariant.BETA);

    assertThat(raised).isEqualTo(1);
    assertThat(repository.sumUsesWithBeta("test-beta", employee.getId())).isEqualTo(2);
    assertThat(repository.findRows("test-beta", DAY)).singleElement()
        .satisfies(row -> assertThat(row.useCount()).isEqualTo(2));
  }

  @Test
  void the_comparison_group_does_not_add_to_the_uses_with_the_beta() {
    repository.saveAndFlush(usage(BetaVariant.CLASSIC));

    assertThat(repository.sumUsesWithBeta("test-beta", employee.getId())).isZero();
  }

  @Test
  void a_second_row_for_the_same_day_is_refused() {
    repository.saveAndFlush(usage(BetaVariant.BETA));

    assertThatThrownBy(() -> repository.saveAndFlush(usage(BetaVariant.BETA)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private BetaUsage usage(BetaVariant variant) {
    var usage = new BetaUsage();
    usage.setFeatureKey("test-beta");
    usage.setEventKey("applied");
    usage.setEmployee(employee);
    usage.setUsageDate(DAY);
    usage.setVariant(variant);
    usage.setUseCount(1);
    return usage;
  }
}
