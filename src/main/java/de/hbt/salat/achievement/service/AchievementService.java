package de.hbt.salat.achievement.service;

import static de.hbt.salat.achievement.service.AchievementNotificationKinds.ACHIEVEMENT_REACHED;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;

import de.hbt.salat.achievement.domain.Achievement;
import de.hbt.salat.achievement.domain.AchievementProfile;
import de.hbt.salat.achievement.domain.RecordedLevel;
import de.hbt.salat.achievement.domain.UnseenAchievement;
import de.hbt.salat.achievement.persistence.AchievementEmployeeReferences;
import de.hbt.salat.achievement.persistence.AchievementRepository;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.achievement.AchievementDefinition;
import de.hbt.salat.common.achievement.AchievementPerson;
import de.hbt.salat.common.achievement.ReachedAchievement;
import de.hbt.salat.common.util.ClockProvider;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.employee.domain.AuthorizedEmployee;
import de.hbt.salat.employee.domain.EmployeeLogin;
import de.hbt.salat.employee.service.EmployeeService;
import de.hbt.salat.notification.domain.NotificationMessage;
import de.hbt.salat.notification.service.NotificationService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *
 * <p>Every new level is announced, in the order it was reached: whoever has been with the company
 * for ten years sees one year and five years first, then ten - the way they happened.
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

  /** A reached level, resolved to the person's login. */
  private record Fresh(EmployeeLogin login, String key, int level, LocalDate achievedOn) {
  }

  /**
   * Records every level reached by {@code today} that is not recorded yet, and announces it - one
   * notification per person and run. The first run therefore records the history with the days it
   * was reached, and a run without anything new changes nothing.
   *
   * @return how many levels were recorded
   */
  @Authorized(requiresManager = true)
  public int recordReached(LocalDate today) {
    var reported = registry.contributors().stream()
        .flatMap(contributor -> contributor.findReached(today).stream())
        .filter(reached -> registry.isKnown(reached.key(), reached.level()) && !reached.achievedOn().isAfter(today))
        .toList();
    if (reported.isEmpty()) {
      return 0;
    }
    var recorded = new HashSet<>(achievementRepository.findAllRecordedLevels());
    var fresh = new ArrayList<Fresh>();
    for (var reached : resolve(reported)) {
      if (recorded.add(new RecordedLevel(reached.login().employeeId(), reached.key(), reached.level()))) {
        fresh.add(reached);
      }
    }
    var byEmployee = fresh.stream().collect(groupingBy(reached -> reached.login().employeeId(), TreeMap::new, Collectors.toList()));
    for (var reachedOfPerson : byEmployee.values()) {
      var login = reachedOfPerson.getFirst().login();
      boolean announce = preferenceService.isAnnouncedTo(login.salatUserId());
      reachedOfPerson.stream()
          .sorted(comparing(Fresh::achievedOn).thenComparing(Fresh::level))
          .forEach(reached -> achievementRepository.save(toEntity(reached, announce)));
      announce(login.salatUserId(), reachedOfPerson, announce);
    }
    log.info("Recorded {} achievement level(s) for {} person(s)", fresh.size(), byEmployee.size());
    return fresh.size();
  }

  /**
   * The achievements of the person on the profile page - the logged-in one, or the one acted as:
   * every level reached, in the order it was reached, and the next level of each achievement.
   */
  @Transactional(readOnly = true)
  public AchievementProfile getProfileOfCurrentPerson(LocalDate today) {
    var employeeId = authorizedEmployee.getEmployeeId();
    var salatUserId = authorizedEmployee.getSalatUserId();
    if (employeeId == null || salatUserId == null) {
      return new AchievementProfile(List.of(), List.of());
    }
    var person = new AchievementPerson(employeeId, salatUserId);
    var recorded = achievementRepository.findAllByEmployeeId(employeeId).stream()
        .filter(achievement -> registry.isKnown(achievement.getAchievementKey(), achievement.getAchievementLevel()))
        .toList();
    var reached = recorded.stream()
        .map(achievement -> {
          var definition = registry.find(achievement.getAchievementKey()).orElseThrow();
          return new AchievementProfile.Reached(definition, achievement.getAchievementLevel(),
              definition.tierOf(achievement.getAchievementLevel()), achievement.getAchievedOn());
        })
        .sorted(comparing(AchievementProfile.Reached::achievedOn)
            .thenComparing(item -> item.definition().getPosition())
            .thenComparing(AchievementProfile.Reached::level))
        .toList();
    var highest = recorded.stream().collect(groupingBy(Achievement::getAchievementKey,
        Collectors.mapping(Achievement::getAchievementLevel, Collectors.maxBy(Integer::compare))));
    var next = registry.all().stream()
        .flatMap(definition -> next(definition, highest.getOrDefault(definition.getKey(), Optional.empty())
            .orElse(null), person, today).stream())
        .toList();
    return new AchievementProfile(reached, next);
  }

  /**
   * Whether the popup has something to announce. Asked on every full page, so it is a single small
   * query; while somebody acts as another person, nothing is announced - the achievement belongs to
   * the other person.
   */
  @Transactional(readOnly = true)
  public boolean hasUnseenForCurrentPerson() {
    var employeeId = announcedPerson();
    return employeeId != null && achievementRepository.existsUnseenByEmployeeId(employeeId);
  }

  /** Every unseen level of the known achievements, in the order it was reached - for the popup. */
  @Transactional(readOnly = true)
  public List<UnseenAchievement> getUnseenForCurrentPerson() {
    var employeeId = announcedPerson();
    if (employeeId == null) {
      return List.of();
    }
    return achievementRepository.findUnseenByEmployeeId(employeeId).stream()
        .filter(achievement -> registry.isKnown(achievement.getAchievementKey(), achievement.getAchievementLevel()))
        .sorted(comparing(Achievement::getAchievedOn).thenComparing(Achievement::getAchievementLevel))
        .map(achievement -> {
          var definition = registry.find(achievement.getAchievementKey()).orElseThrow();
          return new UnseenAchievement(definition, achievement.getAchievementLevel(),
              definition.tierOf(achievement.getAchievementLevel()), achievement.getAchievedOn());
        })
        .toList();
  }

  /** Everything announced counts as seen - the popup was closed, or the profile page opened. */
  public void markSeenForCurrentPerson() {
    var employeeId = announcedPerson();
    if (employeeId != null) {
      achievementRepository.markSeen(employeeId, ClockProvider.now());
    }
  }

  /**
   * The reported levels with the person's login, the one by employee, the other by login. A person
   * without a login, with a restricted one or the technical administrator is left out.
   */
  private List<Fresh> resolve(List<ReachedAchievement> reported) {
    var byEmployee = logins(employeeService.getLoginsOfEmployees(ids(reported, ReachedAchievement::employeeId)),
        EmployeeLogin::employeeId);
    var byLogin = logins(employeeService.getLoginsBySalatUserIds(ids(reported, ReachedAchievement::salatUserId)),
        EmployeeLogin::salatUserId);
    var resolved = new ArrayList<Fresh>();
    for (var reached : reported) {
      var login = reached.employeeId() != null ? byEmployee.get(reached.employeeId()) : byLogin.get(reached.salatUserId());
      if (login != null) {
        resolved.add(new Fresh(login, reached.key(), reached.level(), reached.achievedOn()));
      }
    }
    return resolved;
  }

  private static Collection<Long> ids(List<ReachedAchievement> reported, Function<ReachedAchievement, Long> id) {
    return reported.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
  }

  private static Map<Long, EmployeeLogin> logins(List<EmployeeLogin> logins, Function<EmployeeLogin, Long> key) {
    return logins.stream()
        .filter(login -> !login.isRestricted() && !login.isAdministrator())
        .collect(toMap(key, Function.identity(), (first, second) -> first));
  }

  private Long announcedPerson() {
    if (authorizedUser.getImpersonateLoginSign() != null) {
      return null;
    }
    return authorizedEmployee.getEmployeeId();
  }

  /** The level after the highest reached one, with how far it is; empty once every level is reached. */
  private Optional<AchievementProfile.Next> next(AchievementDefinition definition, Integer highest,
      AchievementPerson person, LocalDate today) {
    return definition.nextLevelAfter(highest)
        .map(level -> registry.contributorOf(definition).progress(person, definition, level, today)
            .map(progress -> new AchievementProfile.Next(definition, level,
                progress.expectedOn() != null && progress.expectedOn().isAfter(today) ? progress.expectedOn() : null,
                progress.remaining()))
            .orElse(new AchievementProfile.Next(definition, level, null, null)));
  }

  private Achievement toEntity(Fresh reached, boolean announce) {
    var achievement = new Achievement();
    achievement.setEmployee(employeeReferences.employee(reached.login().employeeId()));
    achievement.setAchievementKey(reached.key());
    achievement.setAchievementLevel(reached.level());
    achievement.setAchievedOn(reached.achievedOn());
    // without announcements there is no popup to wait for
    achievement.setSeenAt(announce ? null : ClockProvider.now());
    return achievement;
  }

  /**
   * The bell, with the popup, only where the person wants achievements announced; the mail wherever
   * the person wants them mailed (#828), also without the popup.
   */
  private void announce(long salatUserId, List<Fresh> announced, boolean inBell) {
    var label = message("main.achievement.notification.action", null);
    var sorted = announced.stream().sorted(comparing(Fresh::achievedOn).thenComparing(Fresh::level)).toList();
    var notification = NotificationMessage.builder().actionUrl(PROFILE_URL).actionLabel(label);
    if (sorted.size() == 1) {
      var achievement = sorted.getFirst();
      notification.titleKey("main.achievement.notification.one.title").titleParams(List.of(name(achievement)))
          .descriptionKey("main.achievement.notification.one.description")
          .descriptionParams(List.of(DateUtils.format(achievement.achievedOn(), DATE_PATTERN)));
    } else {
      notification.titleKey("main.achievement.notification.many.title").titleParams(List.of(String.valueOf(sorted.size())))
          .descriptionKey("main.achievement.notification.many.description")
          .descriptionParams(List.of(sorted.stream().map(this::name).collect(Collectors.joining(", "))));
    }
    if (inBell) {
      notificationService.emitNotification(ACHIEVEMENT_REACHED, List.of(salatUserId), notification.build());
    } else {
      notificationService.emitMail(ACHIEVEMENT_REACHED, List.of(salatUserId), notification.build());
    }
  }

  /** „Urgestein · 10 Jahre bei HBT“: the level's name and what it stands for. */
  private String name(Fresh reached) {
    var definition = registry.find(reached.key()).orElseThrow();
    return message(definition.rankKey(reached.level()), null) + " · "
        + message(definition.titleKey(), new Object[] {reached.level()});
  }

  private String message(String key, Object[] arguments) {
    return messageSource.getMessage(key, arguments, NOTIFICATION_LOCALE);
  }
}
