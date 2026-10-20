package de.hbt.salat.achievement.service;

import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.AchievementGroup;
import de.hbt.salat.achievement.domain.RecordedLevel;
import de.hbt.salat.achievement.persistence.AchievementEmployeeReferences;
import de.hbt.salat.achievement.persistence.AchievementRepository;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.achievement.AchievementContributor;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.AchievementProgress;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.common.test.FixedClock;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeeLogin;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.notification.service.NotificationService;
import de.hbt.salat.testutils.ReferenceTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * Recording, announcing and showing achievements (#1501), with a stand-in achievement so that the
 * rules of the module are tested apart from the rules of each achievement.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
@FixedClock("2026-10-20T04:30:00")
class AchievementServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 4, 30);
  private static final AchievementDefinition TEST = new AchievementDefinition("test", "flag", 10,
      List.of(level(1, BRONZE), level(5, SILVER), level(10, GOLD)));
  private static final long ANNA = 1L;
  private static final long BERT = 2L;
  private static final long ANNA_LOGIN = 11L;
  private static final long BERT_LOGIN = 12L;

  private final AchievementRepository repository = mock(AchievementRepository.class);
  private final AchievementEmployeeReferences employeeReferences = mock(AchievementEmployeeReferences.class);
  private final AchievementPreferenceService preferenceService = mock(AchievementPreferenceService.class);
  private final EmployeeService employeeService = mock(EmployeeService.class);
  private final NotificationService notificationService = mock(NotificationService.class);
  private final AuthorizedUser authorizedUser = mock(AuthorizedUser.class);
  private final AuthorizedEmployee authorizedEmployee = mock(AuthorizedEmployee.class);
  private final List<ReachedAchievement> reported = new ArrayList<>();
  private final List<Achievement> saved = new ArrayList<>();
  private AchievementProgress progress;
  private AchievementPerson askedFor;

  private AchievementService service;

  @BeforeEach
  void setUp() {
    var contributor = new AchievementContributor() {
      @Override
      public List<AchievementDefinition> getAchievements() {
        return List.of(TEST);
      }

      @Override
      public List<ReachedAchievement> findReached(LocalDate today) {
        return reported;
      }

      @Override
      public Optional<AchievementProgress> progress(AchievementPerson person, AchievementDefinition achievement,
          int level, LocalDate today) {
        askedFor = person;
        return Optional.ofNullable(progress);
      }
    };
    var messages = new StaticMessageSource();
    messages.addMessage("main.achievement.test.title", Locale.GERMAN, "Stufe {0}");
    messages.addMessage("main.achievement.test.rank.1", Locale.GERMAN, "Neuling");
    messages.addMessage("main.achievement.test.rank.5", Locale.GERMAN, "Kenner");
    messages.addMessage("main.achievement.test.rank.10", Locale.GERMAN, "Meister");
    messages.addMessage("main.achievement.notification.action", Locale.GERMAN, "Zum Profil");
    service = new AchievementService(repository, new AchievementRegistry(List.of(contributor)), employeeReferences,
        preferenceService, employeeService, notificationService, messages, authorizedUser, authorizedEmployee);

    when(repository.findAllRecordedLevels()).thenReturn(List.of());
    when(repository.save(any())).thenAnswer(invocation -> {
      saved.add(invocation.getArgument(0));
      return invocation.getArgument(0);
    });
    when(employeeReferences.employee(anyLong()))
        .thenAnswer(invocation -> ReferenceTestUtils.employeeWithId(invocation.getArgument(0)));
    when(employeeService.getLoginsOfEmployees(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return ids.stream().map(id -> new EmployeeLogin(id, id + 10, "ma")).toList();
    });
    when(employeeService.getLoginsBySalatUserIds(any())).thenAnswer(invocation -> {
      Collection<Long> ids = invocation.getArgument(0);
      return ids.stream().map(id -> new EmployeeLogin(id - 10, id, "ma")).toList();
    });
    when(preferenceService.isAnnouncedTo(anyLong())).thenReturn(true);
  }

  @Test
  void the_first_run_records_every_level_with_its_day_and_announces_the_highest_per_achievement() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    reported.add(reached(ANNA, 5, "2021-03-01"));
    reported.add(reached(BERT, 1, "2026-02-01"));

    assertThat(service.recordReached(TODAY)).isEqualTo(3);

    assertThat(saved).extracting(a -> a.getEmployee().getId(), Achievement::getAchievementLevel,
            Achievement::getAchievedOn, Achievement::getSeenAt)
        .containsExactly(
            tuple(ANNA, 1, LocalDate.parse("2017-03-01"), null),
            tuple(ANNA, 5, LocalDate.parse("2021-03-01"), null),
            tuple(BERT, 1, LocalDate.parse("2026-02-01"), null));
    verify(notificationService).emitNotification(List.of(ANNA_LOGIN), "main.achievement.notification.one.title",
        List.of("Kenner · Stufe 5"), "main.achievement.notification.one.description", List.of("01.03.2021"),
        AchievementService.PROFILE_URL, "Zum Profil");
    verify(notificationService).emitNotification(List.of(BERT_LOGIN), "main.achievement.notification.one.title",
        List.of("Neuling · Stufe 1"), "main.achievement.notification.one.description", List.of("01.02.2026"),
        AchievementService.PROFILE_URL, "Zum Profil");
  }

  @Test
  void a_level_reported_by_login_is_recorded_for_the_person_of_the_login() {
    reported.add(ReachedAchievement.ofLogin(ANNA_LOGIN, "test", 1, LocalDate.parse("2026-10-01")));

    assertThat(service.recordReached(TODAY)).isEqualTo(1);

    assertThat(saved).singleElement().extracting(a -> a.getEmployee().getId()).isEqualTo(ANNA);
  }

  @Test
  void a_level_already_recorded_is_neither_recorded_nor_announced_again() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    when(repository.findAllRecordedLevels()).thenReturn(List.of(new RecordedLevel(ANNA, "test", 1)));

    assertThat(service.recordReached(TODAY)).isZero();

    assertThat(saved).isEmpty();
    verifyNoInteractions(notificationService);
  }

  @Test
  void a_level_reported_twice_is_recorded_once() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    reported.add(ReachedAchievement.ofLogin(ANNA_LOGIN, "test", 1, LocalDate.parse("2017-03-01")));

    assertThat(service.recordReached(TODAY)).isEqualTo(1);
  }

  @Test
  void externals_interns_and_the_administrator_get_no_achievements() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    reported.add(reached(BERT, 1, "2017-03-01"));
    doReturn(List.of(new EmployeeLogin(ANNA, ANNA_LOGIN, "restricted"), new EmployeeLogin(BERT, BERT_LOGIN, "adm")))
        .when(employeeService).getLoginsOfEmployees(any());

    assertThat(service.recordReached(TODAY)).isZero();

    assertThat(saved).isEmpty();
    verifyNoInteractions(notificationService);
  }

  @Test
  void with_announcements_switched_off_the_level_is_recorded_as_seen_and_not_announced() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    when(preferenceService.isAnnouncedTo(ANNA_LOGIN)).thenReturn(false);

    service.recordReached(TODAY);

    assertThat(saved).singleElement().extracting(Achievement::getSeenAt).isEqualTo(NOW);
    verifyNoInteractions(notificationService);
  }

  @Test
  void an_unknown_achievement_or_level_and_a_day_still_to_come_are_left_out() {
    reported.add(ReachedAchievement.ofEmployee(ANNA, "gone", 1, LocalDate.parse("2017-03-01")));
    reported.add(reached(ANNA, 2, "2017-03-01"));
    reported.add(reached(ANNA, 1, "2026-10-21"));

    assertThat(service.recordReached(TODAY)).isZero();

    verify(repository, never()).save(any());
  }

  @Test
  void without_anything_reported_nobody_is_asked_for_logins() {
    service.recordReached(TODAY);

    verify(employeeService, never()).getLoginsOfEmployees(any());
    verify(preferenceService, never()).isAnnouncedTo(anyLong());
    verify(notificationService, never()).emitNotification(any(), anyString(), any(), any(), any(), any(), any());
  }

  @Test
  void the_profile_shows_the_reached_levels_with_their_tier_and_when_the_next_one_comes() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(recorded(5, "2021-03-01"), recorded(1, "2017-03-01")));
    progress = AchievementProgress.on(LocalDate.parse("2031-03-01"));

    var group = service.getProfileOfCurrentPerson(TODAY).getFirst();

    assertThat(group.definition()).isEqualTo(TEST);
    assertThat(group.reached()).containsExactly(
        new AchievementGroup.Reached(1, BRONZE, LocalDate.parse("2017-03-01")),
        new AchievementGroup.Reached(5, SILVER, LocalDate.parse("2021-03-01")));
    assertThat(group.next()).isEqualTo(new AchievementGroup.Next(10, LocalDate.parse("2031-03-01"), null));
    assertThat(askedFor).isEqualTo(new AchievementPerson(ANNA, ANNA_LOGIN));
  }

  @Test
  void the_next_level_may_say_how_much_is_still_missing() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of());
    progress = AchievementProgress.missing(37);

    assertThat(service.getProfileOfCurrentPerson(TODAY).getFirst().next()).isEqualTo(new AchievementGroup.Next(1, null, 37));
  }

  @Test
  void the_next_level_has_no_progress_where_it_cannot_be_told() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of());
    progress = null;

    assertThat(service.getProfileOfCurrentPerson(TODAY).getFirst().next()).isEqualTo(new AchievementGroup.Next(1, null, null));
  }

  @Test
  void after_the_highest_level_there_is_no_next_one() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(recorded(10, "2027-03-01")));

    assertThat(service.getProfileOfCurrentPerson(TODAY).getFirst().next()).isNull();
  }

  @Test
  void nothing_is_announced_while_somebody_acts_as_another_person() {
    when(authorizedUser.getImpersonateLoginSign()).thenReturn("bt");
    asPerson();

    assertThat(service.hasUnseenForCurrentPerson()).isFalse();
    service.markSeenForCurrentPerson();

    verify(repository, never()).existsUnseenByEmployeeId(anyLong());
    verify(repository, never()).markSeen(anyLong(), any());
  }

  @Test
  void the_popup_shows_the_highest_unseen_level_of_each_known_achievement() {
    asPerson();
    var gone = recorded(1, "2017-03-01");
    gone.setAchievementKey("gone");
    when(repository.findUnseenByEmployeeId(ANNA))
        .thenReturn(List.of(gone, recorded(1, "2017-03-01"), recorded(5, "2021-03-01")));

    assertThat(service.getUnseenForCurrentPerson())
        .extracting(unseen -> unseen.definition().getKey(), unseen -> unseen.level(), unseen -> unseen.tier())
        .containsExactly(tuple("test", 5, SILVER));
  }

  @Test
  void closing_the_popup_marks_everything_seen() {
    asPerson();

    service.markSeenForCurrentPerson();

    verify(repository).markSeen(eq(ANNA), eq(NOW));
  }

  private void asPerson() {
    when(authorizedEmployee.getEmployeeId()).thenReturn(ANNA);
    when(authorizedEmployee.getSalatUserId()).thenReturn(ANNA_LOGIN);
  }

  private static ReachedAchievement reached(long employeeId, int level, String day) {
    return ReachedAchievement.ofEmployee(employeeId, "test", level, LocalDate.parse(day));
  }

  private static Achievement recorded(int level, String day) {
    var achievement = new Achievement();
    achievement.setEmployee(ReferenceTestUtils.employeeWithId(ANNA));
    achievement.setAchievementKey("test");
    achievement.setAchievementLevel(level);
    achievement.setAchievedOn(LocalDate.parse(day));
    return achievement;
  }
}
