package org.tb.auth;

import static java.util.function.Function.identity;
import static org.tb.auth.AccessLevel.DELETE;
import static org.tb.auth.AccessLevel.EXECUTE;
import static org.tb.auth.AccessLevel.READ;
import static org.tb.auth.AccessLevel.WRITE;
import static org.tb.auth.AuthorizationRule.Category.REPORT_DEFINITION;
import static org.tb.auth.AuthorizationRule.Category.TIMEREPORT;
import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tb.auth.AuthorizationRule.Category;
import org.tb.common.configuration.SalatProperties;
import org.tb.common.util.DateUtils;
import org.tb.common.util.ValidationUtils;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.reporting.domain.ReportDefinition;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final String ALL_OBJECTS = "ALL_OBJECTS";

  private final AuthorizedUser authorizedUser;
  private final AuthorizationRuleRepository authorizationRuleRepository;
  private final SalatProperties salatProperties;

  private long cacheExpiryMillis;
  private Map<Category, Set<Rule>> cacheEntries = new HashMap<>();
  private long lastCacheUpdate;

  @PostConstruct
  public void init() {
    cacheExpiryMillis = salatProperties.getAuthService().getCacheExpiry().toMillis();
  }

  public boolean isAuthorized(Employee employee, AccessLevel accessLevel) {
    if(authorizedUser.isManager()) return true;
    if(employee.isNew()) return false; // only managers can access newly created objects (without any id yet)
    if(employee.getId() == authorizedUser.getEmployeeId()) return true;
    return false;
  }

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(authorizedUser.isManager()) return true;
    if(timereport.getEmployeecontract().getEmployee().getId() == authorizedUser.getEmployeeId()) return true;

    if(accessLevel == READ) {
      // every project manager may see the time reports of her project
      if(authorizedUser.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getId())) {
        return true;
      }
      if(authorizedUser.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getId())) {
        return true;
      }

      // backoffice authorizedUsers may see time reports that must be invoiced
      if(authorizedUser.isBackoffice() && timereport.getSuborder().getInvoice() == SUBORDER_INVOICE_YES) {
        return true;
      }
    }

    return anyRuleMatches(TIMEREPORT,
        rule -> rule.getGrantorId().equals(timereport.getEmployeecontract().getEmployee().getId())
                && rule.getGranteeId().equals(authorizedUser.getEmployeeId())
                && rule.getAccessLevel().satisfies(accessLevel)
                && rule.isValid(timereport.getReferenceday().getRefdate())
                && (rule.getObjectId().equals(ALL_OBJECTS) ||
                    rule.getObjectId().equals(timereport.getSuborder().getCustomerorder().getId())));
  }

  public boolean isAuthorized(ReportDefinition report, AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    LocalDate today = DateUtils.today();
    return anyRuleMatches(REPORT_DEFINITION,
        rule -> rule.getGranteeId().equals(authorizedUser.getSign())
                && rule.getAccessLevel().satisfies(accessLevel)
                && rule.isValid(today)
                && (rule.getObjectId().equals(report.getId()) ||
                    rule.getObjectId().equals(ALL_OBJECTS)));
  }

  public boolean isAuthorizedForAnyReportDefinition(AccessLevel accessLevel) {
    if (authorizedUser.isManager()) {
      return true;
    }
    LocalDate today = DateUtils.today();
    return anyRuleMatches(REPORT_DEFINITION, rule -> rule.getGranteeId().equals(
        authorizedUser.getSign()) && rule.getAccessLevel().satisfies(accessLevel) && rule.isValid(today));
  }

  public Set<AccessLevel> getAccessLevels(ReportDefinition report) {
    if (authorizedUser.isManager()) {
      return Set.of(EXECUTE, READ, WRITE, DELETE);
    }
    LocalDate today = DateUtils.today();
    return collectAccessLevels(REPORT_DEFINITION,
        rule -> rule.getGranteeId().equals(authorizedUser.getSign())
                && rule.isValid(today)
                && (rule.getObjectId().equals(report.getId()) ||
                    rule.getObjectId().equals(ALL_OBJECTS)));
  }

  private Set<AccessLevel> collectAccessLevels(Category category, Predicate<Rule> rulePredicate) {
    ensureUpToDateCache();
    return cacheEntries.get(category).stream().filter(rulePredicate).map(Rule::getAccessLevel)
        .collect(Collectors.toSet());
  }

  private boolean anyRuleMatches(Category category, Predicate<Rule> rulePredicate) {
    ensureUpToDateCache();
    return cacheEntries.get(category).stream().anyMatch(rulePredicate);
  }

  private void ensureUpToDateCache() {
    if (isCacheOutdated()) {
      this.cacheEntries = StreamSupport.stream(authorizationRuleRepository.findAll().spliterator(), false)
          .map(rule -> new Rule(
              rule.getCategory(),
              rule.getGrantorId(),
              rule.getGranteeId(),
              rule.getValidFrom(),
              rule.getValidUntil(),
              rule.getObjectId() != null ? rule.getObjectId() : ALL_OBJECTS,
              rule.getAccessLevel()
          ))
          .collect(
              Collectors.groupingBy(Rule::getCategory, Collectors.mapping(identity(), Collectors.toSet())));
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
      return ValidationUtils.isInRange(date, validFrom, validUntil);
    }

  }

}
