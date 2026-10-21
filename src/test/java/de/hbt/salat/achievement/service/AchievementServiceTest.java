package de.hbt.salat.achievement.service;

import static de.hbt.salat.achievement.service.AchievementNotificationKinds.ACHIEVEMENT_REACHED;
import static de.hbt.salat.common.achievement.AchievementDefinition.NO_YEAR;
import static de.hbt.salat.common.achievement.AchievementLevel.level;
import static de.hbt.salat.common.achievement.AchievementTier.BRONZE;
import static de.hbt.salat.common.achievement.AchievementTier.GOLD;
import static de.hbt.salat.common.achievement.AchievementTier.SILVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.AchievementProfile;
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
import de.hbt.salat.notification.domain.NotificationMessage;
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
  private static final AchievementDefinition YEARLY = AchievementDefinition.yearly("yearly", "book", 20,
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
  private List<AchievementDefinition> achievements = List.of(TEST);

  private AchievementService service;
  private AchievementContributor contributor;
  private StaticMessageSource messages;

  @BeforeEach
  void setUp() {
    var contributor = new AchievementContributor() {
      @Override
      public List<AchievementDefinition> getAchievements() {
        return achievements;
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
    messages.addMessage("main.achievement.yearly.title", Locale.GERMAN, "Jahresstufe {0}");
    messages.addMessage("main.achievement.yearly.rank.1", Locale.GERMAN, "Neuling");
    messages.addMessage("main.achievement.yearly.rank.5", Locale.GERMAN, "Kenner");
    messages.addMessage("main.achievement.notification.action", Locale.GERMAN, "Zum Profil");
    this.contributor = contributor;
    this.messages = messages;
    service = newService();

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
  void the_first_run_records_every_level_with_its_day_and_announces_them_in_order() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    reported.add(reached(ANNA, 5, "2021-03-01"));
    reported.add(reached(BERT, 1, "2026-02-01"));

    assertThat(service.recordReached(TODAY)).isEqualTo(3);

    assertThat(saved).extracting(a -> a.getEmployee().getId(), Achievement::getAchievementLevel,
            Achievement::getAchievedOn, Achievement::getSeenAt, Achievement::getAchievementYear)
        .containsExactly(
            tuple(ANNA, 1, LocalDate.parse("2017-03-01"), null, NO_YEAR),
            tuple(ANNA, 5, LocalDate.parse("2021-03-01"), null, NO_YEAR),
            tuple(BERT, 1, LocalDate.parse("2026-02-01"), null, NO_YEAR));
    verify(notificationService).emitNotification(ACHIEVEMENT_REACHED, List.of(ANNA_LOGIN), NotificationMessage.builder()
        .titleKey("main.achievement.notification.many.title").titleParams(List.of("2"))
        .descriptionKey("main.achievement.notification.many.description")
        .descriptionParams(List.of("Neuling · Stufe 1, Kenner · Stufe 5"))
        .actionUrl(AchievementService.PROFILE_URL).actionLabel("Zum Profil").build());
    verify(notificationService).emitNotification(ACHIEVEMENT_REACHED, List.of(BERT_LOGIN), NotificationMessage.builder()
        .titleKey("main.achievement.notification.one.title").titleParams(List.of("Neuling · Stufe 1"))
        .descriptionKey("main.achievement.notification.one.description").descriptionParams(List.of("01.02.2026"))
        .actionUrl(AchievementService.PROFILE_URL).actionLabel("Zum Profil").build());
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
    when(repository.findAllRecordedLevels()).thenReturn(List.of(new RecordedLevel(ANNA, "test", NO_YEAR, 1)));

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
  void with_announcements_switched_off_the_level_is_recorded_as_seen_and_not_announced_in_the_bell() {
    reported.add(reached(ANNA, 1, "2017-03-01"));
    when(preferenceService.isAnnouncedTo(ANNA_LOGIN)).thenReturn(false);

    service.recordReached(TODAY);

    assertThat(saved).singleElement().extracting(Achievement::getSeenAt).isEqualTo(NOW);
    verify(notificationService, never()).emitNotification(any(), any(), any());
    // whether it is mailed is the person's own choice since #828, decided by the notification module
    verify(notificationService).emitMail(eq(ACHIEVEMENT_REACHED), eq(List.of(ANNA_LOGIN)), any());
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
    verifyNoInteractions(notificationService);
  }

  @Test
  void the_profile_shows_the_reached_levels_in_the_order_they_were_reached_and_when_the_next_one_comes() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(recorded(5, "2021-03-01"), recorded(1, "2017-03-01")));
    progress = AchievementProgress.on(LocalDate.parse("2031-03-01"));

    var profile = service.getProfileOfCurrentPerson(TODAY);

    assertThat(profile.reached()).containsExactly(
        new AchievementProfile.Reached(TEST, 1, BRONZE, LocalDate.parse("2017-03-01")),
        new AchievementProfile.Reached(TEST, 5, SILVER, LocalDate.parse("2021-03-01")));
    assertThat(profile.next()).containsExactly(new AchievementProfile.Next(TEST, 10, LocalDate.parse("2031-03-01"), null));
    assertThat(askedFor).isEqualTo(new AchievementPerson(ANNA, ANNA_LOGIN));
  }

  @Test
  void the_next_level_may_say_how_much_is_still_missing() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of());
    progress = AchievementProgress.missing(37);

    assertThat(service.getProfileOfCurrentPerson(TODAY).next()).containsExactly(new AchievementProfile.Next(TEST, 1, null, 37));
  }

  @Test
  void the_next_level_has_no_progress_where_it_cannot_be_told() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of());
    progress = null;

    assertThat(service.getProfileOfCurrentPerson(TODAY).next()).containsExactly(new AchievementProfile.Next(TEST, 1, null, null));
  }

  @Test
  void after_the_highest_level_there_is_no_next_one() {
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(recorded(10, "2027-03-01")));

    assertThat(service.getProfileOfCurrentPerson(TODAY).next()).isEmpty();
  }

  /* #1512: a yearly achievement reaches its levels again every calendar year. */
  @Test
  void a_level_of_a_yearly_achievement_is_recorded_once_per_year_with_its_year() {
    yearlyOnly();
    reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 1, LocalDate.parse("2025-03-01")));
    reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 1, LocalDate.parse("2026-02-01")));
    when(repository.findAllRecordedLevels()).thenReturn(List.of(new RecordedLevel(ANNA, "yearly", 2025, 1)));

    assertThat(service.recordReached(TODAY)).isEqualTo(1);

    assertThat(saved).singleElement()
        .extracting(Achievement::getAchievementYear, Achievement::getAchievedOn)
        .containsExactly(2026, LocalDate.parse("2026-02-01"));
  }

  /* The first run records the yearly levels of every year since a person started - for a long tenure
     dozens, whose names overflowed the one notification and stopped the whole job. Earlier years
     are history: recorded as seen, shown on the profile page, not announced. */
  @Test
  void levels_of_earlier_years_of_a_yearly_achievement_are_recorded_as_seen_and_not_announced() {
    yearlyOnly();
    for (int year = 2006; year <= 2025; year++) {
      reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 1, LocalDate.of(year, 3, 1)));
      reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 5, LocalDate.of(year, 6, 1)));
    }
    reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 1, LocalDate.parse("2026-02-01")));

    assertThat(service.recordReached(TODAY)).isEqualTo(41);

    assertThat(saved).filteredOn(a -> a.getAchievementYear() < 2026).hasSize(40)
        .allSatisfy(a -> assertThat(a.getSeenAt()).isEqualTo(NOW));
    assertThat(saved).filteredOn(a -> a.getAchievementYear() == 2026).singleElement()
        .extracting(Achievement::getSeenAt).isNull();
    verify(notificationService).emitNotification(ACHIEVEMENT_REACHED, List.of(ANNA_LOGIN), NotificationMessage.builder()
        .titleKey("main.achievement.notification.one.title").titleParams(List.of("Neuling · Jahresstufe 1"))
        .descriptionKey("main.achievement.notification.one.description").descriptionParams(List.of("01.02.2026"))
        .actionUrl(AchievementService.PROFILE_URL).actionLabel("Zum Profil").build());
  }

  @Test
  void only_levels_of_earlier_years_are_recorded_without_any_announcement() {
    yearlyOnly();
    reported.add(ReachedAchievement.ofEmployee(ANNA, "yearly", 1, LocalDate.parse("2025-03-01")));

    assertThat(service.recordReached(TODAY)).isEqualTo(1);

    verifyNoInteractions(notificationService);
  }

  @Test
  void the_next_level_of_a_yearly_achievement_is_the_next_one_of_the_current_year() {
    yearlyOnly();
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(
        recordedYearly(10, "2025-11-01"), recordedYearly(1, "2026-02-01")));

    var profile = service.getProfileOfCurrentPerson(TODAY);

    assertThat(profile.reached()).extracting(AchievementProfile.Reached::level).containsExactly(10, 1);
    assertThat(profile.next()).containsExactly(new AchievementProfile.Next(YEARLY, 5, null, null));
  }

  /* Every level of every year would flood the profile page with dozens of medals: of an earlier year
     only the highest one stands there, of the current year each. */
  @Test
  void the_profile_shows_the_highest_level_of_each_earlier_year_and_every_level_of_the_current_year() {
    yearlyOnly();
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(
        recordedYearly(1, "2024-02-01"), recordedYearly(5, "2024-06-01"),
        recordedYearly(1, "2025-03-01"), recordedYearly(5, "2025-05-01"), recordedYearly(10, "2025-11-01"),
        recordedYearly(1, "2026-02-01"), recordedYearly(5, "2026-04-01")));

    assertThat(service.getProfileOfCurrentPerson(TODAY).reached())
        .extracting(AchievementProfile.Reached::level, AchievementProfile.Reached::achievedOn)
        .containsExactly(tuple(5, LocalDate.parse("2024-06-01")), tuple(10, LocalDate.parse("2025-11-01")),
            tuple(1, LocalDate.parse("2026-02-01")), tuple(5, LocalDate.parse("2026-04-01")));
  }

  @Test
  void a_yearly_achievement_with_nothing_this_year_starts_at_its_first_level() {
    yearlyOnly();
    asPerson();
    when(repository.findAllByEmployeeId(ANNA)).thenReturn(List.of(recordedYearly(10, "2025-11-01")));

    assertThat(service.getProfileOfCurrentPerson(TODAY).next())
        .containsExactly(new AchievementProfile.Next(YEARLY, 1, null, null));
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
  void the_popup_shows_every_unseen_level_of_the_known_achievements_in_the_order_it_was_reached() {
    asPerson();
    var gone = recorded(1, "2017-03-01");
    gone.setAchievementKey("gone");
    when(repository.findUnseenByEmployeeId(ANNA))
        .thenReturn(List.of(gone, recorded(5, "2021-03-01"), recorded(1, "2017-03-01")));

    assertThat(service.getUnseenForCurrentPerson())
        .extracting(unseen -> unseen.definition().getKey(), unseen -> unseen.level(), unseen -> unseen.tier())
        .containsExactly(tuple("test", 1, BRONZE), tuple("test", 5, SILVER));
  }

  @Test
  void closing_the_popup_marks_everything_seen() {
    asPerson();

    service.markSeenForCurrentPerson();

    verify(repository).markSeen(eq(ANNA), eq(NOW));
  }

  private AchievementService newService() {
    return new AchievementService(repository, new AchievementRegistry(List.of(contributor)), employeeReferences,
        preferenceService, employeeService, notificationService, messages, authorizedUser, authorizedEmployee);
  }

  private void yearlyOnly() {
    achievements = List.of(YEARLY);
    service = newService();
  }

  private static Achievement recordedYearly(int level, String day) {
    var achievement = recorded(level, day);
    achievement.setAchievementKey("yearly");
    achievement.setAchievementYear(LocalDate.parse(day).getYear());
    return achievement;
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
