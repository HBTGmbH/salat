package de.hbt.salat.dailyreport.auth;

import static de.hbt.salat.auth.domain.AccessLevel.DELETE;
import static de.hbt.salat.auth.domain.AccessLevel.READ;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITTED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.GlobalConstants.YESNO_YES;
import static de.hbt.salat.common.exception.ErrorCode.AA_NOT_ATHORIZED;
import static de.hbt.salat.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
import static de.hbt.salat.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_SUCCEEDED_CONTRACT_NOT_SELF;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.dailyreport.domain.ReportPeriod;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

@Component
@RequiredArgsConstructor
public class TimereportAuthorization {

  private static final String AUTH_CATEGORY_TIMEREPORT = "TIMEREPORT";

  private final AuthorizedUser authorizedUser;
  private final AuthService authService;
  private final EmployeecontractService employeecontractService;

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
        return authorizedUser.isAdmin() && !isOwner;
      }
      if(TIMEREPORT_STATUS_COMMITTED.equals(timereport.getStatus())) {
        return !isOwner && (
          authorizedUser.isManager() || (authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(timereport.getEmployeecontract()))
        );
      }
      // TIMEREPORT_STATUS_OPEN timereports may be written by manager and owners
      if(TIMEREPORT_STATUS_OPEN.equals(timereport.getStatus()) && (authorizedUser.isManager() || isOwner)) {
        return true;
      }
    }

    // check rules as fallback — person and order by id, in every form a rule may name them (#1089, #1204)
    var date = timereport.getReferenceday().getRefdate();
    return authService.isAuthorized(AUTH_CATEGORY_TIMEREPORT, date, accessLevel, TimereportRuleObject.formsOf(
        timereport.getEmployeecontract().getEmployee().getId(),
        timereport.getSuborder().getCustomerorder().getId(),
        timereport.getSuborder().getId()));
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
   * noch ein Admin (#1164) — alle anderen öffnen den Zeitraum erst wieder. Offene Buchungen eines beendeten Vertrags
   * schreibt die Person selbst nicht mehr, sobald sie auf einem Folgevertrag freigegeben hat (#1215). Besteht eine Buchung mit einem dieser drei
   * Status die Vorprüfung, lässt {@link #isAuthorized} sie über seine ausdrücklichen Zweige ebenfalls
   * zu, und keine Regel ändert daran noch etwas — die Antwort ist dieselbe wie die von
   * {@code checkAuthorized}.
   */
  public boolean isWriteAllowed(Employeecontract contract, String status) {
    return writeDenial(contract, status).isEmpty();
  }

  /**
   * Dieselbe Frage für einen Tag statt für einen Status: der Status ist der, den eine Buchung an
   * diesem Tag bekäme ({@link ReportPeriod}). So beantworten Tagesansicht und Liste je Tag, ob sie
   * Anlegen und den Arbeitstag anbieten, und der Arbeitstag folgt beim Speichern derselben Regel
   * wie die Buchungen seines Tages (#1164).
   */
  public boolean isWriteAllowedOn(Employeecontract contract, LocalDate day) {
    return writeDenialOn(contract, day).isEmpty();
  }

  public Optional<ErrorCode> writeDenialOn(Employeecontract contract, LocalDate day) {
    return writeDenial(contract, ReportPeriod.statusOn(contract, day));
  }

  private Optional<ErrorCode> writeDenial(Employeecontract contract, String status) {
    var isOwner = Objects.equals(authorizedUser.getEffectiveLoginSign(), contract.getEmployee().getSalatUser().getLoginname());
    if(TIMEREPORT_STATUS_CLOSED.equals(status) &&
       (!authorizedUser.isAdmin() || isOwner)) {
      return Optional.of(TR_CLOSED_TIME_REPORT_REQ_ADMIN);
    }
    if(TIMEREPORT_STATUS_COMMITTED.equals(status) &&
       !authorizedUser.isManager() &&
       !(authorizedUser.isPeopleLead() && isSupervisedByCurrentUser(contract))) {
      return Optional.of(TR_COMMITTED_TIME_REPORT_REQ_MANAGER);
    }
    if(TIMEREPORT_STATUS_COMMITTED.equals(status) && isOwner) {
      return Optional.of(TR_COMMITTED_TIME_REPORT_NOT_SELF);
    }
    if(TIMEREPORT_STATUS_OPEN.equals(status) &&
       !authorizedUser.isManager() &&
       !isOwner) {
      return Optional.of(TR_OPEN_TIME_REPORT_REQ_EMPLOYEE);
    }
    if(TIMEREPORT_STATUS_OPEN.equals(status) &&
       !authorizedUser.isManager() &&
       hasReleasedSuccessor(contract)) {
      return Optional.of(TR_SUCCEEDED_CONTRACT_NOT_SELF);
    }
    return Optional.empty();
  }

  /**
   * Ob die Person selbst den Vertrag hinter sich hat: er ist beendet, und auf einem Folgevertrag hat sie schon
   * freigegeben (#1215). Freigabe und Abnahme laufen je Vertrag, und die Seite der Freigabe schlägt den laufenden vor —
   * so blieb der letzte Monat eines alten Vertrags oft für immer offen und damit für die Person änderbar. Das Vertragsende
   * allein genügt nicht: bis zur ersten Freigabe auf dem Folgevertrag bucht die Person den letzten Monat des alten noch
   * nach und gibt ihn frei. Nur die Person selbst ist gemeint — die Geschäftsführung schreibt offene Buchungen weiter.
   * Die Frage geht an die Datenbank und steht deshalb hinter den Prüfungen, die ohne sie auskommen.
   */
  private boolean hasReleasedSuccessor(Employeecontract contract) {
    return employeecontractService.hasReleasedSuccessor(contract);
  }

  private boolean isSupervisedByCurrentUser(Employeecontract ec) {
    return ec.getSupervisors().stream()
        .anyMatch(s -> s.getSalatUser().getLoginname().equals(authorizedUser.getEffectiveLoginSign()));
  }

}
