package de.hbt.salat.achievement.persistence;

import static de.hbt.salat.common.achievement.AchievementDefinition.NO_YEAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.RecordedLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The recorded achievements against the database (#1501): what the job and the popup ask. */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class AchievementRepositoryTest {

  private static final LocalDateTime SEEN = LocalDateTime.of(2026, 10, 20, 8, 0);

  @Autowired
  private AchievementRepository repository;

  @Autowired
  private EmployeeRepository employeeRepository;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Employee anna;
  private Employee bert;

  @BeforeEach
  void setUp() {
    anna = employee("aa");
    bert = employee("bb");
  }

  @Test
  void the_job_reads_what_is_recorded_as_values() {
    repository.save(achievement(anna, 1, "2017-03-01"));
    repository.save(achievement(bert, 5, "2021-03-01"));

    assertThat(repository.findAllRecordedLevels()).containsExactlyInAnyOrder(
        new RecordedLevel(anna.getId(), "tenure", NO_YEAR, 1), new RecordedLevel(bert.getId(), "tenure", NO_YEAR, 5));
  }

  @Test
  void a_level_is_recorded_once_per_person() {
    repository.saveAndFlush(achievement(anna, 1, "2017-03-01"));

    assertThatThrownBy(() -> repository.saveAndFlush(achievement(anna, 1, "2017-03-01")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /* #1512: a yearly achievement records the same level once per year. */
  @Test
  void a_level_of_a_yearly_achievement_is_recorded_once_per_year() {
    repository.saveAndFlush(yearly(anna, 1, "2025-03-01"));
    repository.saveAndFlush(yearly(anna, 1, "2026-03-01"));

    assertThat(repository.findAllRecordedLevels()).containsExactlyInAnyOrder(
        new RecordedLevel(anna.getId(), "training-year", 2025, 1), new RecordedLevel(anna.getId(), "training-year", 2026, 1));
    assertThatThrownBy(() -> repository.saveAndFlush(yearly(anna, 1, "2026-05-01")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void the_popup_asks_for_the_unseen_achievements_of_the_person_only() {
    repository.save(achievement(anna, 1, "2017-03-01"));
    repository.save(achievement(anna, 5, "2021-03-01"));
    repository.save(achievement(bert, 1, "2026-02-01"));

    assertThat(repository.existsUnseenByEmployeeId(anna.getId())).isTrue();
    assertThat(repository.findUnseenByEmployeeId(anna.getId()))
        .extracting(Achievement::getAchievementLevel).containsExactly(1, 5);

    assertThat(repository.markSeen(anna.getId(), SEEN)).isEqualTo(2);

    assertThat(repository.existsUnseenByEmployeeId(anna.getId())).isFalse();
    assertThat(repository.findUnseenByEmployeeId(anna.getId())).isEmpty();
    assertThat(repository.existsUnseenByEmployeeId(bert.getId())).isTrue();
    assertThat(repository.findAllByEmployeeId(anna.getId())).hasSize(2);
  }

  private Employee employee(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    return employeeRepository.save(employee);
  }

  private static Achievement yearly(Employee employee, int level, String day) {
    var achievement = achievement(employee, level, day);
    achievement.setAchievementKey("training-year");
    achievement.setAchievementYear(achievement.getAchievedOn().getYear());
    return achievement;
  }

  private static Achievement achievement(Employee employee, int level, String day) {
    var achievement = new Achievement();
    achievement.setEmployee(employee);
    achievement.setAchievementKey("tenure");
    achievement.setAchievementLevel(level);
    achievement.setAchievedOn(LocalDate.parse(day));
    return achievement;
  }
}
