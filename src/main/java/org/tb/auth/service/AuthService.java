package org.tb.auth.service;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.EXECUTE;
import static org.tb.auth.domain.AccessLevel.LOGIN;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.domain.AccessLevel.WRITE;
import static org.tb.auth.domain.AuthorizationRule.Category.EMPLOYEE;
import static org.tb.auth.domain.AuthorizationRule.Category.REPORT_DEFINITION;
import static org.tb.auth.domain.AuthorizationRule.Category.TIMEREPORT;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.util.DateUtils.isInRange;
import static org.tb.common.util.DateUtils.today;

import jakarta.annotation.PostConstruct;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizationRule.Category;
import org.tb.auth.AuthorizationRuleRepository;
import org.tb.auth.AuthorizedUser;
import org.tb.common.SalatProperties;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;
import org.tb.reporting.domain.ReportDefinition;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ALL_OBJECTS = "ALL_OBJECTS";

  private final AuthorizedUser authorizedUser;
  private final AuthorizationRuleRepository authorizationRuleRepository;
  private final SalatProperties salatProperties;
  private final EmployeeRepository employeeRepository;

  private long cacheExpiryMillis;
  private Map<Category, Set<Rule>> cacheEntries = new HashMap<>();
  private long lastCacheUpdate;

  @PostConstruct
  public void init() {
    cacheExpiryMillis = salatProperties.getAuthService().getCacheExpiry().toMillis();
  }

  public void initAuthorizedUser(Principal principal, AuthorizedUser authorizedUser) {
    employeeRepository.findBySign(principal.getName()).ifPresent(authorizedUser::init);
  }

  public List<Employee> getLoginEmployees() {
    return StreamSupport
        .stream(employeeRepository.findAll().spliterator(), false)
        .filter(e -> e.getSign().equals(authorizedUser.getLoginSign()) || isAuthorized(e, LOGIN))
        .toList();
  }

  public void switchLogin(long loginEmployeeId) {
    employeeRepository.findById(loginEmployeeId).ifPresent(loginEmployee -> {
      if(isAuthorized(loginEmployee, LOGIN)) {
        authorizedUser.init(loginEmployee);
      }
    });
  }

  public boolean isAuthorizedForEmployee(long employeeId, AccessLevel accessLevel) {
    return employeeRepository
        .findById(employeeId)
        .map(e -> isAuthorized(e, accessLevel))
        .orElse(false);
  }

  public boolean isAuthorized(Employee employee, AccessLevel accessLevel) {
    if(accessLevel == LOGIN) {
      if(employee.getSign().equals(authorizedUser.getLoginSign())) return true;
      return anyRuleMatches(EMPLOYEE,
          rule -> rule.getGrantorId().equals(employee.getSign())
                  && rule.getGranteeId().equals(authorizedUser.getLoginSign())
                  && rule.getAccessLevel().satisfies(LOGIN)
                  && rule.isValid(today()));
    }

    if(authorizedUser.isManager()) return true;
    if(employee.isNew()) return false; // only managers can access newly created objects (without any id yet)
    if(employee.getSign().equals(authorizedUser.getSign())) return true;
    return false;
  }

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(authorizedUser.isManager()) return true;
    if(timereport.getEmployeecontract().getEmployee().getSign().equals(authorizedUser.getSign())) return true;

    if(accessLevel == READ) {
      // every project manager may see the time reports of her project
      if(authorizedUser.getSign().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getSign())) {
        return true;
      }
      if(authorizedUser.getSign().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getSign())) {
        return true;
      }

      // backoffice authorizedUsers may see time reports that must be invoiced
      if(authorizedUser.isBackoffice() && timereport.getSuborder().getInvoice() == YESNO_YES) {
        return true;
      }
    }

    return anyRuleMatches(TIMEREPORT,
        rule -> rule.getGrantorId().equals(timereport.getEmployeecontract().getEmployee().getSign())
                && rule.getGranteeId().equals(authorizedUser.getSign())
                && rule.getAccessLevel().satisfies(accessLevel)
                && rule.isValid(timereport.getReferenceday().getRefdate())
                && (rule.getObjectId().equals(ALL_OBJECTS) ||
                    rule.getObjectId().equals(timereport.getSuborder().getCustomerorder().getSign()) ||
                    rule.getObjectId().equals(timereport.getSuborder().getCompleteOrderSign())));
  }

  public boolean isAuthorized(ReportDefinition report, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    LocalDate today = today();
    return anyRuleMatches(REPORT_DEFINITION,
        rule -> rule.getGranteeId().equals(authorizedUser.getSign())
                && rule.getAccessLevel().satisfies(accessLevel)
                && rule.isValid(today)
                && (rule.getObjectId().equals(String.valueOf(report.getId())) ||
                    rule.getObjectId().equals(ALL_OBJECTS)));
  }

  public boolean isAuthorizedForAnyReportDefinition(AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    LocalDate today = today();
    return anyRuleMatches(REPORT_DEFINITION, rule -> rule.getGranteeId().equals(
        authorizedUser.getSign()) && rule.getAccessLevel().satisfies(accessLevel) && rule.isValid(today));
  }

  public Set<AccessLevel> getAccessLevels(ReportDefinition report) {
    if (authorizedUser.isManager()) {
      return Set.of(EXECUTE, READ, WRITE, DELETE);
    }
    LocalDate today = today();
    return collectAccessLevels(REPORT_DEFINITION,
        rule -> rule.getGranteeId().equals(authorizedUser.getSign())
                && rule.isValid(today)
                && (rule.getObjectId().equals(String.valueOf(report.getId())) ||
                    rule.getObjectId().equals(ALL_OBJECTS)));
  }

  private Set<AccessLevel> collectAccessLevels(Category category, Predicate<Rule> rulePredicate) {
    ensureUpToDateCache();
    return cacheEntries.get(category).stream().filter(rulePredicate).map(Rule::getAccessLevel)
        .collect(toSet());
  }

  private boolean anyRuleMatches(Category category, Predicate<Rule> rulePredicate) {
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
                      rule.getValidFrom(),
                      rule.getValidUntil(),
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
                        rule.getValidFrom(),
                        rule.getValidUntil(),
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
  private static class Rule {
    private final Category category;
    private final String grantorId;
    private final String granteeId;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String objectId;
    private final AccessLevel accessLevel;

    public boolean isValid(LocalDate date) {
      return isInRange(date, validFrom, validUntil);
    }

  }

}
