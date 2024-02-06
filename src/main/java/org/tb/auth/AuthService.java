package org.tb.auth;

import static org.tb.common.GlobalConstants.SUBORDER_INVOICE_YES;
import static org.tb.common.util.SecureHashUtils.legacyPasswordMatches;
import static org.tb.common.util.SecureHashUtils.passwordMatches;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

  public static final int ALL_CUSTOMER_ORDERS = -1;
  private final EmployeeRepository employeeRepository;
  private final EmployeeToEmployeeAuthorizationRuleRepository employeeToEmployeeAuthorizationRuleRepository;

  @Value("${salat.auth-service.cache-expiry:5m}")
  private Duration cacheExpiry = Duration.ofMinutes(5);
  private long cacheExpiryMillis;
  private Set<AuthCacheEntry> cacheEntries = new HashSet<>();
  private long lastCacheUpdate;

  @PostConstruct
  public void init() {
    cacheExpiryMillis = cacheExpiry.toMillis();
  }

  public Optional<Employee> authenticate(String loginname, String password) {
    var employeeResult = employeeRepository.findByLoginname(loginname);
    if(employeeResult.isPresent()) {
      var employee = employeeResult.get();
      var passwordMatches = passwordMatches(password, employee.getPassword());
      if (!passwordMatches) {
        // Fallback to legacy password matching - some users may not have been migrated yet!
        var legacyPasswordMatches = legacyPasswordMatches(password, employee.getPassword());
        if (legacyPasswordMatches) {
          // employee still has old password form
          // store password again with new hashing algorithm
          employee.changePassword(password);
          employeeRepository.save(employee);
          passwordMatches = true;
        }
      }
      if(passwordMatches) {
        return Optional.of(employee);
      }
    }
    return Optional.empty();
  }

  public boolean isAuthorized(Employee employee, AuthorizedUser user, AccessLevel accessLevel) {
    if(user.isManager()) return true;
    if(employee.isNew()) return false; // only managers can access newly created objects (without any id yet)
    if(employee.getId() == user.getEmployeeId()) return true;
    return false;
  }

  public boolean isAuthorized(Timereport timereport, AuthorizedUser user, AccessLevel accessLevel) {
    if(user.isManager()) return true;
    if (timereport.getEmployeecontract().getEmployee().getId().equals(user.getEmployeeId())) {
      return true;
    }

    if(accessLevel == AccessLevel.READ) {
      // every project manager may see the time reports of her project
      if(user.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getResponsible_hbt().getId())) {
        return true;
      }
      if(user.getEmployeeId().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getId())) {
        return true;
      }

      // backoffice users may see time reports that must be invoiced
      if(user.isBackoffice() && timereport.getSuborder().getInvoice() == SUBORDER_INVOICE_YES) {
        return true;
      }
    }

    updateCache();
    return cacheEntries.stream()
            .filter(rule -> rule.getGrantorId() == timereport.getEmployeecontract().getEmployee().getId())
            .filter(rule -> rule.getRecipientId() == user.getEmployeeId())
            .filter(rule -> rule.getAccessLevel().satisfies(accessLevel))
            .filter(rule -> rule.isValid(timereport.getReferenceday().getRefdate()))
            .filter(rule -> rule.getCustomerOrderId() == ALL_CUSTOMER_ORDERS ||
                    rule.getCustomerOrderId() == timereport.getSuborder().getCustomerorder().getId())
            .map(rule -> true)
            .findAny().orElse(false);
  }

  private void updateCache() {
    if(isCacheOutdated()) {
      this.cacheEntries = StreamSupport.stream(employeeToEmployeeAuthorizationRuleRepository.findAll().spliterator(), false)
              .map(rule -> new AuthCacheEntry(
                      rule.getGrantor().getId(),
                      rule.getRecipient().getId(),
                      rule.getValidFrom(),
                      rule.getValidUntil(),
                      rule.getCustomerOrder() != null ? rule.getCustomerOrder().getId() : ALL_CUSTOMER_ORDERS,
                      rule.getAccessLevel()
              ))
              .collect(Collectors.toSet());
      lastCacheUpdate = Clock.systemUTC().millis();
    }
  }

  private boolean isCacheOutdated() {
    return lastCacheUpdate == 0 || lastCacheUpdate + cacheExpiryMillis < Clock.systemUTC().millis();
  }

  @Data
  @RequiredArgsConstructor
  private static class AuthCacheEntry {
    private final long grantorId;
    private final long recipientId;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final long customerOrderId;
    private final AccessLevel accessLevel;

    public boolean isValid(LocalDate date) {
      return !date.isBefore(validFrom) && !date.isAfter(validUntil);
    }

  }

}
