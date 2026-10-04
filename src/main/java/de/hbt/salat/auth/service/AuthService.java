package de.hbt.salat.auth.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static de.hbt.salat.auth.domain.AccessLevel.LOGIN;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.util.DateUtils.today;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthUiStateKeyContributor;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.event.AuthorizedUserChangedEvent;
import de.hbt.salat.auth.persistence.AuthorizationRuleRepository;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.SalatProperties;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.web.UiState;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  /**
   * The wildcard of a rule, as it is written into the database. Public because a caller may build a composite object
   * value that carries it — see {@code TimereportAuthorization}.
   */
  public static final String ANY_MATCH = "*";

  /**
   * Marks a value that could not be assigned to a record when rules moved from login names and signs to ids (#1204).
   * Kept rather than dropped, so that the editor can show what was meant; it never matches, because an id never
   * starts with it.
   */
  public static final String UNRESOLVED_PREFIX = "?";

  private final AuthorizedUser authorizedUser;
  private final AuthorizationRuleRepository authorizationRuleRepository;
  private final SalatUserRepository salatUserRepository;
  private final SalatProperties salatProperties;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final UiState uiState;

  private long cacheExpiryMillis;
  // written by request threads on refresh and by clearCache() — the map is always replaced as a whole,
  // so volatile is enough to make the change visible to other threads; no locking needed.
  private volatile Map<String, Set<Rule>> cacheEntries = new HashMap<>();
  // login name -> ids of its SalatUser, and back; refreshed together with the rules (#1204)
  private volatile Map<String, Set<String>> userIdsByLoginname = new HashMap<>();
  private volatile Map<String, String> loginnameByUserId = new HashMap<>();
  private volatile long lastCacheUpdate;

  @PostConstruct
  public void init() {
    cacheExpiryMillis = salatProperties.getAuthService().getCacheExpiry().toMillis();
  }

  @EventListener
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    SecurityContextHolder.getContext().setAuthentication(event.getAuthentication()); // ensure Security Context is set
    applicationEventPublisher.publishEvent(new AuthorizedUserChangedEvent(this));
  }

  @Authorized
  public void switchLogin(String loginname) {
    uiState.clearAll();
    if (!authorizedUser.getLoginSign().equals(loginname)) {
      // the object of a rule of category EMPLOYEE is the login that may be taken over, by its id (#1204)
      if (!isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, userIdsOf(loginname).toArray(String[]::new))) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
      var login = findLogin(loginname).orElse(null);
      uiState.setValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_SIGN, loginname);
      uiState.setValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_STATUS, login == null ? null : login.getStatus());
      uiState.setValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_ID, login == null ? null : String.valueOf(login.getId()));
    }
    applicationEventPublisher.publishEvent(new AuthorizedUserChangedEvent(this));
  }

  /**
   * Invalidates the authorization rule cache. The rules are reloaded lazily by the next authorization check,
   * so a rule maintained directly in the database takes effect without waiting for the cache to expire.
   */
  @Authorized(requiresManager = true)
  public void clearCache() {
    lastCacheUpdate = 0;
    log.info("Authorization rule cache cleared by {}", authorizedUser.getLoginSign());
  }

  /**
   * The login with this name — what an authentication turns into roles and the login id (#1330), and what a switch of
   * login records. Read from the database every time, not from the cache of the rules.
   */
  public Optional<SalatUser> findLogin(String loginname) {
    return salatUserRepository.findByLoginname(loginname);
  }

  public boolean isAuthorized(String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    return isAuthorizedAs(authorizedUser.getEffectiveLoginSign(), category, date, accessLevel, objectId);
  }

  /**
   * Asks for the login the user really signed in with, not for the one they act in the name of. Only {@link
   * AccessLevel#LOGIN} uses this: whoever took over somebody else's login must not use it to grant themselves the
   * next takeover.
   */
  public boolean isAuthorizedForOwnLogin(String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    return isAuthorizedAs(authorizedUser.getLoginSign(), category, date, accessLevel, objectId);
  }

  public boolean isAuthorizedAnyObject(String category, LocalDate date, AccessLevel accessLevel) {
    return anyRuleMatches(category, rule -> {
      if(!matchesGrantee(rule, userIdsOf(authorizedUser.getEffectiveLoginSign()))) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return true;
    });
  }

  private boolean isAuthorizedAs(String loginname, String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    var userIds = userIdsOf(loginname);
    return anyRuleMatches(category, rule -> {
      if(!matchesGrantee(rule, userIds)) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return ANY_MATCH.equals(rule.getObjectId()) || Arrays.stream(objectId).anyMatch(rule.getObjectId()::equals);
    });
  }

  public List<Rule> getAuthRules(String category, String objectId) {
    ensureUpToDateCache();
    return cacheEntries.getOrDefault(category, Set.of()).stream()
        .filter(r -> ANY_MATCH.equals(r.getObjectId()) || objectId.equals(r.getObjectId()))
        .toList();
  }

  /**
   * Every rule of a category that grants the current user something at that date — the objects, not the answer to one
   * question about one object (#1092). A list cannot ask {@code isAuthorized} per row: it has to turn the rules into a
   * condition of its query, and for that it needs to read them. The rules come from the in-memory cache, so this costs
   * no statement.
   *
   * <p>The objects are returned as they stand in the database. What a value means belongs to the category — the caller
   * that wrote the object is the one that can read it (see {@code TimereportAuthorization#objectsOf}).
   */
  public List<Rule> getRulesForCurrentUser(String category, LocalDate date, AccessLevel accessLevel) {
    return getRulesForCurrentUser(category, new LocalDateRange(date, date), accessLevel);
  }

  /**
   * The same for a period: a list spans days, so a rule counts as soon as its validity overlaps that period. Whether
   * a single booking inside the period is covered stays the question of {@code isAuthorized}.
   */
  public List<Rule> getRulesForCurrentUser(String category, LocalDateRange period, AccessLevel accessLevel) {
    ensureUpToDateCache();
    var userIds = userIdsOf(authorizedUser.getEffectiveLoginSign());
    return cacheEntries.getOrDefault(category, Set.of()).stream()
        .filter(rule -> matchesGrantee(rule, userIds))
        .filter(rule -> rule.getAccessLevel().satisfies(accessLevel))
        .filter(rule -> rule.getValidity().overlaps(period))
        .toList();
  }

  /**
   * Matches the grantee of a rule against a user. {@value #ANY_MATCH} stands for every authenticated user, the same
   * way it does for the object of a rule — that is how a report is shared with everybody without maintaining a list
   * of signs. A rule without any grantee matches nobody: leaving the grantee out must not grant to all.
   *
   * <p>A grantee is the id of a {@link de.hbt.salat.auth.domain.SalatUser}, not a login name (#1204): a login name can
   * be changed or anonymized, and whoever got the old one next would inherit the rights.
   */
  private boolean matchesGrantee(Rule rule, Set<String> userIds) {
    return ANY_MATCH.equals(rule.getGranteeId()) || userIds.contains(rule.getGranteeId());
  }

  /**
   * The ids of the {@link de.hbt.salat.auth.domain.SalatUser} signed in under this login name — as of the last refresh
   * of the cache, so a renamed login is followed within {@code salat.auth-service.cache-expiry}. Usually one; nothing
   * keeps the login name unique, and where it is not, each of them answers for it, as before.
   */
  public Set<String> userIdsOf(String loginname) {
    ensureUpToDateCache();
    return loginname == null ? Set.of() : userIdsByLoginname.getOrDefault(loginname, Set.of());
  }

  /**
   * The login name to show for the grantee of a rule. The wildcard and a value that could not be assigned are shown as
   * they are stored.
   */
  public String loginnameOf(String granteeId) {
    ensureUpToDateCache();
    return loginnameByUserId.getOrDefault(granteeId, granteeId);
  }

  private boolean anyRuleMatches(String category, Predicate<Rule> rulePredicate) {
    ensureUpToDateCache();
    return cacheEntries.getOrDefault(category, Set.of()).stream().anyMatch(rulePredicate);
  }

  private void ensureUpToDateCache() {
    if (isCacheOutdated()) {
      final var rules = new HashSet<Rule>();
      authorizationRuleRepository.findAll().forEach(rule -> {
        rule.getAccessLevels().forEach(accessLevel -> {
          rule.getGranteeId().forEach(granteeId -> {
            if(rule.getObjectId().isEmpty()) {
              rules.add(
                  new Rule(
                      rule.getCategory(),
                      granteeId,
                      new LocalDateRange(rule.getValidFrom(), rule.getValidUntil()),
                      ANY_MATCH,
                      accessLevel
                  )
              );
            } else {
              rule.getObjectId().forEach(objectId -> {
                rules.add(
                    new Rule(
                        rule.getCategory(),
                        granteeId,
                        new LocalDateRange(rule.getValidFrom(), rule.getValidUntil()),
                        objectId,
                        accessLevel
                    )
                );
              });
            }
          });
        });
      });
      var idsByLoginname = new HashMap<String, Set<String>>();
      var loginnameById = new HashMap<String, String>();
      salatUserRepository.findAll().forEach(user -> {
        if (user.getLoginname() == null) return;
        idsByLoginname.computeIfAbsent(user.getLoginname(), key -> new HashSet<>()).add(String.valueOf(user.getId()));
        loginnameById.put(String.valueOf(user.getId()), user.getLoginname());
      });
      userIdsByLoginname = idsByLoginname;
      loginnameByUserId = loginnameById;
      cacheEntries = rules.stream().collect(groupingBy(Rule::getCategory, mapping(identity(), toSet())));
      lastCacheUpdate = Clock.systemUTC().millis();
    }
  }

  private boolean isCacheOutdated() {
    return lastCacheUpdate == 0 || lastCacheUpdate + cacheExpiryMillis < Clock.systemUTC().millis();
  }

  @Data
  @RequiredArgsConstructor
  public static class Rule {
    private final String category;
    private final String granteeId;
    private final LocalDateRange validity;
    private final String objectId;
    private final AccessLevel accessLevel;

    public boolean isValid(LocalDate date) {
      return validity.contains(date);
    }

  }

}
