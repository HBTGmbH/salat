package org.tb.auth.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.auth.persistence.AuthorizationRuleRepository;
import org.tb.common.DateRange;
import org.tb.common.SalatProperties;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ALL_OBJECTS = "*";

  private final AuthorizedUser authorizedUser;
  private final AuthorizationRuleRepository authorizationRuleRepository;
  private final SalatProperties salatProperties;
  private final ApplicationEventPublisher applicationEventPublisher;

  private long cacheExpiryMillis;
  private Map<String, Set<Rule>> cacheEntries = new HashMap<>();
  private long lastCacheUpdate;

  @PostConstruct
  public void init() {
    cacheExpiryMillis = salatProperties.getAuthService().getCacheExpiry().toMillis();
  }

  @EventListener
  public void onApplicationEvent(AuthenticationSuccessEvent event) {
    Authentication authentication = event.getAuthentication();
    if(!authorizedUser.isAuthenticated() || !authorizedUser.getLoginSign().equals(authentication.getName())) {
      authorizedUser.login(authentication.getName());
      applicationEventPublisher.publishEvent(new AuthorizedUserChangedEvent(this));
    } else {
      // already logged in
    }
  }

  @Authorized(requiresManager = true)
  public void switchLogin(String sign) {
    authorizedUser.setSign(sign);
    applicationEventPublisher.publishEvent(new AuthorizedUserChangedEvent(this));
  }

  public boolean isAuthorized(String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    return anyRuleMatches(category, rule -> {
      if(!authorizedUser.getSign().equals(rule.getGranteeId())) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return ALL_OBJECTS.equals(rule.getObjectId()) || Arrays.stream(objectId).anyMatch(rule.getObjectId()::equals);
    });
  }

  public boolean isAuthorized(String grantorSign, String category, LocalDate date, AccessLevel accessLevel, String... objectId) {
    return anyRuleMatches(category, rule -> {
      if(!authorizedUser.getSign().equals(rule.getGranteeId())) return false;
      if(rule.getGrantorId() != null && !ALL_OBJECTS.equals(rule.getGrantorId()) && !grantorSign.equals(rule.getGrantorId())) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return ALL_OBJECTS.equals(rule.getObjectId()) || Arrays.stream(objectId).anyMatch(rule.getObjectId()::equals);
    });
  }

  public boolean isAuthorizedAnyObject(String grantorSign, String category, LocalDate date, AccessLevel accessLevel) {
    return anyRuleMatches(category, rule -> {
      if(!authorizedUser.getSign().equals(rule.getGranteeId())) return false;
      if(rule.getGrantorId() != null && !ALL_OBJECTS.equals(rule.getGrantorId()) && !grantorSign.equals(rule.getGrantorId())) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return true;
    });
  }

  public boolean isAuthorizedAnyObject(String category, LocalDate date, AccessLevel accessLevel) {
    return anyRuleMatches(category, rule -> {
      if(!authorizedUser.getSign().equals(rule.getGranteeId())) return false;
      if(!rule.getAccessLevel().satisfies(accessLevel)) return false;
      if(!rule.isValid(date)) return false;
      return true;
    });
  }

  public List<Rule> getAuthRules(String category, String objectId) {
    ensureUpToDateCache();
    return cacheEntries.getOrDefault(category, Set.of()).stream()
        .filter(r -> ALL_OBJECTS.equals(r.getObjectId()) || objectId.equals(r.getObjectId()))
        .toList();
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
                      rule.getGrantorId(),
                      granteeId,
                      new DateRange(rule.getValidFrom(), rule.getValidUntil()),
                      ALL_OBJECTS,
                      accessLevel
                  )
              );
            } else {
              rule.getObjectId().forEach(objectId -> {
                rules.add(
                    new Rule(
                        rule.getCategory(),
                        rule.getGrantorId(),
                        granteeId,
                        new DateRange(rule.getValidFrom(), rule.getValidUntil()),
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
    private final String grantorId;
    private final String granteeId;
    private final DateRange validity;
    private final String objectId;
    private final AccessLevel accessLevel;

    public boolean isValid(LocalDate date) {
      return validity.contains(date);
    }

  }

}
