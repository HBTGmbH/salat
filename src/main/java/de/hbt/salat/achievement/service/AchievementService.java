package de.hbt.salat.achievement.service;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.AchievementGroup;
import de.hbt.salat.achievement.domain.RecordedLevel;
import de.hbt.salat.achievement.domain.UnseenAchievement;
import de.hbt.salat.achievement.persistence.AchievementEmployeeReferences;
import de.hbt.salat.achievement.persistence.AchievementRepository;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeeLogin;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.notification.service.NotificationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records the achievements the modules report as reached, announces them and shows them to the
 * person who reached them (#837, #1501). Only positive incentives: nobody sees the achievements of
 * somebody else, and there is no ranking.
 *
 * <p>Achievements are for employees: a restricted login (externals, interns) and the technical
 * administrator get none.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
@Authorized
public class AchievementService {

  static final String PROFILE_URL = "/profile";
  /** Notifications are rendered in German whatever the reader's language (NotificationViewHelper). */
  private static final Locale NOTIFICATION_LOCALE = Locale.GERMAN;
  private static final String DATE_PATTERN = "dd.MM.yyyy";

  private final AchievementRepository achievementRepository;
  private final AchievementRegistry registry;
  private final AchievementEmployeeReferences employeeReferences;
  private final AchievementPreferenceService preferenceService;
  private final EmployeeService employeeService;
  private final NotificationService notificationService;
  private final MessageSource messageSource;
  private final AuthorizedUser authorizedUser;
  private final AuthorizedEmployee authorizedEmployee;

  /**
   * Records every level reached by {@code today} that is not recorded yet, and announces it — one
   * notification per person and run, however many levels came at once. The first run therefore
   * records the history with the days it was reached, and a run without anything new changes
   * nothing.
   *
   * @return how many levels were recorded
   */
  @Authorized(requiresManager = true)
  public int recordReached(LocalDate today) {
    var recorded = new HashSet<>(achievementRepository.findAllRecordedLevels());
    var fresh = new ArrayList<ReachedAchievement>();
    for (var contributor : registry.contributors()) {
      for (var reached : contributor.findReached(today)) {
        var level = new RecordedLevel(reached.employeeId(), reached.key(), reached.level());
        if (registry.isKnown(reached.key(), reached.level()) && !reached.achievedOn().isAfter(today)
            && recorded.add(level)) {
          fresh.add(reached);
        }
      }
    }
    if (fresh.isEmpty()) {
      return 0;
    }
    var byEmployee = fresh.stream().collect(groupingBy(ReachedAchievement::employeeId, TreeMap::new, Collectors.toList()));
    Map<Long, EmployeeLogin> logins = employeeService.getLoginsOfEmployees(byEmployee.keySet()).stream()
        .filter(login -> !login.isRestricted() && !login.isAdministrator())
        .collect(Collectors.toMap(EmployeeLogin::employeeId, Function.identity()));
    int count = 0;
    for (var entry : byEmployee.entrySet()) {
      var login = logins.get(entry.getKey());
      if (login == null) {
        continue;
      }
      var reached = entry.getValue().stream()
          .sorted(comparing(ReachedAchievement::achievedOn).thenComparing(ReachedAchievement::level))
          .toList();
      boolean announce = preferenceService.isAnnouncedTo(login.salatUserId());
      reached.forEach(achievement -> achievementRepository.save(toEntity(achievement, announce)));
      if (announce) {
        announce(login.salatUserId(), reached);
      }
      count += reached.size();
    }
    log.info("Recorded {} achievement level(s) for {} person(s)", count, logins.size());
    return count;
  }

  /**
   * The achievements of the person on the profile page — the logged-in one, or the one acted as:
   * per achievement the levels reached and the next one.
   */
  @Transactional(readOnly = true)
  public List<AchievementGroup> getProfileOfCurrentPerson(LocalDate today) {
    var employeeId = authorizedEmployee.getEmployeeId();
    if (employeeId == null) {
      return List.of();
    }
    var recorded = achievementRepository.findAllByEmployeeId(employeeId).stream()
        .filter(achievement -> registry.isKnown(achievement.getAchievementKey(), achievement.getAchievementLevel()))
        .collect(groupingBy(Achievement::getAchievementKey));
    return registry.all().stream()
        .map(definition -> group(definition, recorded.getOrDefault(definition.getKey(), List.of()), employeeId, today))
        .toList();
  }

  /**
   * Whether the popup has something to announce. Asked on every full page, so it is a single small
   * query; while somebody acts as another person, nothing is announced — the achievement belongs to
   * the other person.
   */
  @Transactional(readOnly = true)
  public boolean hasUnseenForCurrentPerson() {
    var employeeId = announcedPerson();
    return employeeId != null && achievementRepository.existsUnseenByEmployeeId(employeeId);
  }

  @Transactional(readOnly = true)
  public List<UnseenAchievement> getUnseenForCurrentPerson() {
    var employeeId = announcedPerson();
    if (employeeId == null) {
      return List.of();
    }
    return achievementRepository.findUnseenByEmployeeId(employeeId).stream()
        .flatMap(achievement -> registry.find(achievement.getAchievementKey())
            .filter(definition -> definition.hasLevel(achievement.getAchievementLevel()))
            .map(definition -> new UnseenAchievement(definition, achievement.getAchievementLevel(),
                achievement.getAchievedOn()))
            .stream())
        .toList();
  }

  /** Everything announced counts as seen — the popup was closed, or the profile page opened. */
  public void markSeenForCurrentPerson() {
    var employeeId = announcedPerson();
    if (employeeId != null) {
      achievementRepository.markSeen(employeeId, ClockProvider.now());
    }
  }

  private Long announcedPerson() {
    if (authorizedUser.getImpersonateLoginSign() != null) {
      return null;
    }
    return authorizedEmployee.getEmployeeId();
  }

  private AchievementGroup group(AchievementDefinition definition, List<Achievement> recorded, long employeeId,
      LocalDate today) {
    var reached = recorded.stream()
        .sorted(comparing(Achievement::getAchievementLevel))
        .map(achievement -> new AchievementGroup.Reached(achievement.getAchievementLevel(), achievement.getAchievedOn()))
        .toList();
    var highest = reached.isEmpty() ? null : reached.getLast().level();
    var next = definition.nextLevelAfter(highest)
        .map(level -> new AchievementGroup.Next(level, registry.contributorOf(definition)
            .forecast(employeeId, definition, level, today)
            .filter(day -> day.isAfter(today))
            .orElse(null)))
        .orElse(null);
    return new AchievementGroup(definition, reached, next);
  }

  private Achievement toEntity(ReachedAchievement reached, boolean announce) {
    var achievement = new Achievement();
    achievement.setEmployee(employeeReferences.employee(reached.employeeId()));
    achievement.setAchievementKey(reached.key());
    achievement.setAchievementLevel(reached.level());
    achievement.setAchievedOn(reached.achievedOn());
    // without announcements there is no popup to wait for
    achievement.setSeenAt(announce ? null : ClockProvider.now());
    return achievement;
  }

  private void announce(long salatUserId, List<ReachedAchievement> reached) {
    var label = message("main.achievement.notification.action");
    if (reached.size() == 1) {
      var achievement = reached.getFirst();
      notificationService.emitNotification(List.of(salatUserId),
          "main.achievement.notification.one.title", List.of(title(achievement)),
          "main.achievement.notification.one.description",
          List.of(DateUtils.format(achievement.achievedOn(), DATE_PATTERN)),
          PROFILE_URL, label);
    } else {
      notificationService.emitNotification(List.of(salatUserId),
          "main.achievement.notification.many.title", List.of(String.valueOf(reached.size())),
          "main.achievement.notification.many.description",
          List.of(reached.stream().map(this::title).collect(Collectors.joining(", "))),
          PROFILE_URL, label);
    }
  }

  private String title(ReachedAchievement reached) {
    var definition = registry.find(reached.key()).orElseThrow();
    return messageSource.getMessage(definition.titleKey(), new Object[] {reached.level()}, NOTIFICATION_LOCALE);
  }

  private String message(String key) {
    return messageSource.getMessage(key, null, NOTIFICATION_LOCALE);
  }
}
