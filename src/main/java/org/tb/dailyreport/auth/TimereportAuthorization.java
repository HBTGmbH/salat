package org.tb.dailyreport.auth;

import static org.tb.auth.domain.AccessLevel.DELETE;
import static org.tb.auth.domain.AccessLevel.READ;
import static org.tb.auth.service.AuthService.ANY_MATCH;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.YESNO_YES;
import static org.tb.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employeecontract;

@Component
@RequiredArgsConstructor
public class TimereportAuthorization {

  private static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";
  private static final String SIGN_SEPARATOR = ":";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;

  public boolean isAuthorized(Timereport timereport, AccessLevel accessLevel) {
    if(accessLevel == READ && authorizedUser.isManager()) return true;
    if(accessLevel == READ && authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract())) return true;
    var isOwner = timereport.getEmployeecontract().getEmployee().getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign());
    if(isOwner && accessLevel == READ) return true;

    if(accessLevel == READ) {
      // every project manager may see the time reports of her project
      var loginSign = authorizedUser.getEffectiveLoginSign();
      if(timereport.getSuborder().getCustomerorder().getResponsibleHbt().stream()
          .anyMatch(e -> loginSign.equals(e.getSalatUser().getLoginname()))) {
        return true;
      }
      if(authorizedUser.getEffectiveLoginSign().equals(timereport.getSuborder().getCustomerorder().getRespEmpHbtContract().getSalatUser().getLoginname())) {
        return true;
      }

      // backoffice authorizedUsers may see time reports that must be invoiced
      if(authorizedUser.isBackoffice() && timereport.getSuborder().getInvoice() == YESNO_YES) {
        return true;
      }
    }

    if(accessLevel == DELETE || accessLevel == AccessLevel.WRITE) {
      // write allowance depends on timereport status
      if(TIMEREPORT_STATUS_CLOSED.equals(timereport.getStatus())) {
        return authorizedUser.isManager() && !isOwner;
      }
      if(TIMEREPORT_STATUS_COMMITED.equals(timereport.getStatus())) {
        return !isOwner && (
          authorizedUser.isManager() || (authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract()))
        );
      }
      // TIMEREPORT_STATUS_OPEN timereports may be written by manager and owners
      if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) && (authorizedUser.isManager() || isOwner)) {
        return true;
      }
    }

    // check rules as fallback
    var employeeSign = timereport.getEmployeecontract().getEmployee().getSign();
    var date = timereport.getReferenceday().getRefdate();
    var customerOrderSign = timereport.getSuborder().getCustomerorder().getSign();
    var suborderSign = timereport.getSuborder().getCompleteOrderSign();
    return authService.isAuthorized(AUTH_CATEGORY_TIMEREPORT, date, accessLevel,
        objectsOf(employeeSign, customerOrderSign, suborderSign));
  }

  /**
   * A rule of this category may name two things at once — whose bookings, and on which order. Both fit into the one
   * object of a rule because the caller spells out every form it accepts, so the rule engine keeps comparing whole
   * values and never parses one:
   * <ul>
   *   <li>{@code 1453}, {@code 1453/01} — bookings of anybody on that order</li>
   *   <li>{@code xx:1453}, {@code xx:1453/01} — only the bookings of xx there</li>
   *   <li>{@code xx:*} — the bookings of xx, on every order</li>
   * </ul>
   * The separator is read at its first occurrence: an order sign may contain a colon, a sign may not. The format
   * belongs to this category alone — written into a rule of another one, it matches nothing.
   */
  private String[] objectsOf(String employeeSign, String customerOrderSign, String suborderSign) {
    return new String[] {
        customerOrderSign,
        suborderSign,
        employeeSign + SIGN_SEPARATOR + customerOrderSign,
        employeeSign + SIGN_SEPARATOR + suborderSign,
        employeeSign + SIGN_SEPARATOR + ANY_MATCH
    };
  }

  public void checkAuthorized(List<Timereport> timereports, AccessLevel accessLevel) throws AuthorizationException {
    // authorization is based on the status
    timereports.forEach(timereport -> {

      // pre qualify write access rules
      if(accessLevel == DELETE || accessLevel == AccessLevel.WRITE) {
        writeDenial(timereport.getEmployeecontract(), timereport.getStatus()).ifPresent(denial -> {
          throw new AuthorizationException(denial);
        });
      }

      // ensure isAuthorized checks are made
      if(!isAuthorized(timereport, accessLevel)) {
        throw new AuthorizationException(AA_NOT_ATHORIZED);
      }
    });
  }

  /**
   * Ob der angemeldete Benutzer Buchungen dieses Vertrags mit diesem Status schreiben oder löschen
   * darf. Die Frage braucht keine Buchung: so zeigt eine Seite den Link zum Bearbeiten oder Anlegen
   * nur dort, wo er nicht in einen Berechtigungsfehler führt (#760).
   *
   * <p>Es ist die Vorprüfung nach dem Status, die {@link #checkAuthorized} selbst aufruft, keine
   * Kopie davon: offene Buchungen schreiben die Person selbst und die Geschäftsführung, freigegebene
   * die Geschäftsführung und die zuständige People Lead, aber nie die Person selbst, abgenommene nur
   * die Geschäftsführung, und auch sie nicht die eigenen. Besteht eine Buchung mit einem dieser drei
   * Status die Vorprüfung, lässt {@link #isAuthorized} sie über seine ausdrücklichen Zweige ebenfalls
   * zu, und keine Regel ändert daran noch etwas — die Antwort ist dieselbe wie die von
   * {@code checkAuthorized}.
   */
  public boolean isWriteAllowed(Employeecontract contract, String status) {
    return writeDenial(contract, status).isEmpty();
  }

  private Optional<ErrorCode> writeDenial(Employeecontract contract, String status) {
    var isOwner = Objects.equals(authorizedUser.getEffectiveLoginSign(), contract.getEmployee().getSalatUser().getLoginname());
    if(TIMEREPORT_STATUS_CLOSED.equals(status) &&
       (!authorizedUser.isManager() || isOwner)) {
      return Optional.of(TR_CLOSED_TIME_REPORT_REQ_MANAGER);
    }
    if(TIMEREPORT_STATUS_COMMITED.equals(status) &&
       !authorizedUser.isManager() &&
       !(authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(contract))) {
      return Optional.of(TR_COMMITTED_TIME_REPORT_REQ_MANAGER);
    }
    if(TIMEREPORT_STATUS_COMMITED.equals(status) && isOwner) {
      return Optional.of(TR_COMMITTED_TIME_REPORT_NOT_SELF);
    }
    if(TIMEREPORT_STATUS_OPEN.equals(status) &&
       !authorizedUser.isManager() &&
       !isOwner) {
      return Optional.of(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE);
    }
    return Optional.empty();
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
