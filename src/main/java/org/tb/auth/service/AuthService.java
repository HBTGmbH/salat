package org.tb.auth.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.util.DateUtils.today;

import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthUiStateKeyContributor;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.auth.persistence.SalatUserRepository;
import org.tb.common.LocalDateRange;
import org.tb.common.SalatProperties;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.web.UiState;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  /**
   * The wildcard of a rule, as it is written into the database. Public because a caller may build a composite object
   * value that carries it — see {@code TimereportAuthorization}.
   */
  public static final String ANY_MATCH = "*";

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
      if (!isAuthorizedForOwnLogin("EMPLOYEE", today(), LOGIN, loginname)) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
      uiState.setValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_SIGN, loginname);
      uiState.setValue(AuthUiStateKeyContributor.IMPERSONATE_LOGIN_STATUS, getStatusByLoginname(loginname));
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

  public String getStatusByLoginname(String loginname) {
    return salatUserRepository.findByLoginname(loginname)
        .map(salatUser -> salatUser.getStatus())
        .orElse(null);
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
      if(!matchesGrantee(rule, authorizedUser.getEffectiveLoginSign())) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return true;
    });
  }

  private boolean isAuthorizedAs(String userSign, String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    return anyRuleMatches(category, rule -> {
      if(!matchesGrantee(rule, userSign)) return false;
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
   * Matches the grantee of a rule against a user. {@value #ANY_MATCH} stands for every authenticated user, the same
   * way it does for the object of a rule — that is how a report is shared with everybody without maintaining a list
   * of signs. A rule without any grantee matches nobody: leaving the grantee out must not grant to all.
   */
  private boolean matchesGrantee(Rule rule, String userSign) {
    return ANY_MATCH.equals(rule.getGranteeId()) || userSign.equals(rule.getGranteeId());
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
