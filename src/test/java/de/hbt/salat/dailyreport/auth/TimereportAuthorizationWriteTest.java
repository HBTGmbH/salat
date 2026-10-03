package de.hbt.salat.dailyreport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.quality.Strictness.LENIENT;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITTED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static de.hbt.salat.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_ADMIN;
import static de.hbt.salat.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static de.hbt.salat.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static de.hbt.salat.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
import static de.hbt.salat.common.exception.ErrorCode.TR_SUCCEEDED_CONTRACT_NOT_SELF;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import de.hbt.salat.auth.domain.AccessLevel;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.service.EmployeecontractService;

/**
 * Wer eine Buchung schreiben darf, hängt an ihrem Status und daran, wer fragt: die Person selbst,
 * die Geschäftsführung, die zuständige People Lead, ein Admin. Eine abgenommene Buchung schreibt nur
 * noch ein Admin; alle anderen öffnen den Zeitraum erst wieder (#1164). Die Tabelle hält fest, was
 * {@link TimereportAuthorization#checkAuthorized} dazu entscheidet — mit dem Fehlercode, den eine
 * Ablehnung trägt. Keine Regel der Kategorie TIMEREPORT greift hier; sie könnte an diesen Fällen
 * auch nichts ändern, denn die Prüfung des Status kommt vor ihr.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = LENIENT)
class TimereportAuthorizationWriteTest {

    private static final String OWNER = "xx";
    private static final String PEOPLE_LEAD = "pl";
    private static final String SOMEBODY_ELSE = "yy";

    @Mock
    private AuthorizedUser authorizedUser;
    @Mock
    private AuthService authService;
    @Mock
    private EmployeecontractService employeecontractService;

    private TimereportAuthorization timereportAuthorization;
    private Employeecontract contract;

    @BeforeEach
    void setUp() {
        timereportAuthorization = new TimereportAuthorization(authorizedUser, authService, employeecontractService);
        contract = new Employeecontract();
        contract.setEmployee(employee(OWNER));
        contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
    }

    /**
     * status, owner, manager, supervising people lead, admin → the code of the denial, {@code null}
     * when writing is allowed. An admin holds the manager role as well.
     */
    static Stream<Arguments> writeAccess() {
        var cases = new ArrayList<Arguments>();
        for (var status : List.of(TIMEREPORT_STATUS_OPEN, TIMEREPORT_STATUS_COMMITTED, TIMEREPORT_STATUS_CLOSED)) {
            for (var owner : List.of(true, false)) {
                for (var manager : List.of(true, false)) {
                    for (var supervisingPeopleLead : List.of(true, false)) {
                        for (var admin : manager ? List.of(true, false) : List.of(false)) {
                            cases.add(Arguments.of(status, owner, manager, supervisingPeopleLead, admin,
                                expectedDenial(status, owner, manager, supervisingPeopleLead, admin)));
                        }
                    }
                }
            }
        }
        return cases.stream();
    }

    private static ErrorCode expectedDenial(String status, boolean owner, boolean manager, boolean supervisingPeopleLead,
                                            boolean admin) {
        return switch (status) {
            case TIMEREPORT_STATUS_OPEN -> owner || manager ? null : TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
            case TIMEREPORT_STATUS_COMMITTED -> !manager && !supervisingPeopleLead ? TR_COMMITTED_TIME_REPORT_REQ_MANAGER
                : owner ? TR_COMMITTED_TIME_REPORT_NOT_SELF
                : null;
            case TIMEREPORT_STATUS_CLOSED -> admin && !owner ? null : TR_CLOSED_TIME_REPORT_REQ_ADMIN;
            default -> throw new IllegalArgumentException(status);
        };
    }

    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3}, admin {4} -> {5}")
    @MethodSource("writeAccess")
    void checkAuthorizedDecidesByStatusAndWhoAsks(String status, boolean owner, boolean manager,
                                                  boolean supervisingPeopleLead, boolean admin, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead, admin);

        var denial = catchThrowableOfType(AuthorizationException.class,
            () -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.WRITE));

        if (expectedDenial == null) {
            assertThat(denial).isNull();
        } else {
            assertThat(denial).isNotNull();
            assertThat(denial.getMessages()).extracting(message -> message.getErrorCode()).containsExactly(expectedDenial);
        }
    }

    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3}, admin {4} -> {5}")
    @MethodSource("writeAccess")
    void deletingIsDecidedTheSameWay(String status, boolean owner, boolean manager,
                                     boolean supervisingPeopleLead, boolean admin, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead, admin);

        var check = assertThatCode(() -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.DELETE));

        if (expectedDenial == null) {
            check.doesNotThrowAnyException();
        } else {
            check.isInstanceOf(AuthorizationException.class);
        }
    }

    /** Die Frage ohne Buchung beantwortet {@code isWriteAllowed} genau so, wie {@code checkAuthorized} sie mit Buchung beantwortet (#760). */
    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3}, admin {4} -> {5}")
    @MethodSource("writeAccess")
    void isWriteAllowedAgreesWithCheckAuthorized(String status, boolean owner, boolean manager,
                                                 boolean supervisingPeopleLead, boolean admin, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead, admin);

        var checkPasses = catchThrowableOfType(AuthorizationException.class,
            () -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.WRITE)) == null;

        assertThat(timereportAuthorization.isWriteAllowed(contract, status))
            .isEqualTo(checkPasses)
            .isEqualTo(expectedDenial == null);
    }

    /**
     * Ein beendeter Vertrag, auf den ein schon freigegebener Folgevertrag folgt, ist für die Person selbst abgeschlossen
     * (#1215) — auch wenn sein letzter Monat nie freigegeben wurde und seine Buchungen dort noch offen sind.
     */
    @Test
    void ownerMayNotWriteOpenReportsOfContractWithReleasedSuccessor() {
        endedWithReleasedSuccessor();
        loggedInAs(true, false, false, false);

        var denial = catchThrowableOfType(AuthorizationException.class,
            () -> timereportAuthorization.checkAuthorized(List.of(timereport(TIMEREPORT_STATUS_OPEN)), AccessLevel.WRITE));

        assertThat(denial).isNotNull();
        assertThat(denial.getMessages()).extracting(message -> message.getErrorCode())
            .containsExactly(TR_SUCCEEDED_CONTRACT_NOT_SELF);
        assertThat(timereportAuthorization.isWriteAllowed(contract, TIMEREPORT_STATUS_OPEN)).isFalse();
        assertThat(timereportAuthorization.writeDenialOn(contract, contract.getValidUntil()))
            .contains(TR_SUCCEEDED_CONTRACT_NOT_SELF);
    }

    @Test
    void ownerMayNotDeleteOpenReportsOfContractWithReleasedSuccessor() {
        endedWithReleasedSuccessor();
        loggedInAs(true, false, false, false);

        assertThatCode(() -> timereportAuthorization.checkAuthorized(List.of(timereport(TIMEREPORT_STATUS_OPEN)), AccessLevel.DELETE))
            .isInstanceOf(AuthorizationException.class);
    }

    @Test
    void managerMayStillWriteOpenReportsOfContractWithReleasedSuccessor() {
        endedWithReleasedSuccessor();
        loggedInAs(false, true, false, false);

        assertThatCode(() -> timereportAuthorization.checkAuthorized(List.of(timereport(TIMEREPORT_STATUS_OPEN)), AccessLevel.WRITE))
            .doesNotThrowAnyException();
        assertThat(timereportAuthorization.isWriteAllowed(contract, TIMEREPORT_STATUS_OPEN)).isTrue();
    }

    /** Bis zur ersten Freigabe auf dem Folgevertrag kann die Person den letzten Monat noch nachbuchen. */
    @Test
    void ownerMayWriteOpenReportsOfEndedContractWhileSuccessorIsNotReleased() {
        contract.setValidUntil(LocalDate.of(2025, 12, 31));
        lenient().when(employeecontractService.hasReleasedSuccessor(contract)).thenReturn(false);
        loggedInAs(true, false, false, false);

        assertThatCode(() -> timereportAuthorization.checkAuthorized(List.of(timereport(TIMEREPORT_STATUS_OPEN)), AccessLevel.WRITE))
            .doesNotThrowAnyException();
        assertThat(timereportAuthorization.isWriteAllowed(contract, TIMEREPORT_STATUS_OPEN)).isTrue();
    }

    private void endedWithReleasedSuccessor() {
        contract.setValidFrom(LocalDate.of(2014, 1, 1));
        contract.setValidUntil(LocalDate.of(2014, 12, 31));
        contract.setReportReleaseDate(LocalDate.of(2014, 11, 30));
        contract.setReportAcceptanceDate(LocalDate.of(2014, 7, 31));
        lenient().when(employeecontractService.hasReleasedSuccessor(contract)).thenReturn(true);
    }

    private void loggedInAs(boolean owner, boolean manager, boolean supervisingPeopleLead, boolean admin) {
        var sign = owner ? OWNER : supervisingPeopleLead ? PEOPLE_LEAD : SOMEBODY_ELSE;
        if (owner && supervisingPeopleLead) {
            // one person as owner and supervisor of the same contract
            contract.getSupervisors().add(employee(OWNER));
        }
        lenient().when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
        lenient().when(authorizedUser.getLoginSign()).thenReturn(sign);
        lenient().when(authorizedUser.isManager()).thenReturn(manager);
        lenient().when(authorizedUser.isAdmin()).thenReturn(admin);
        lenient().when(authorizedUser.isPeopleLead()).thenReturn(manager || supervisingPeopleLead);
    }

    private Timereport timereport(String status) {
        var timereport = new Timereport();
        timereport.setEmployeecontract(contract);
        timereport.setStatus(status);
        return timereport;
    }

    private static Employee employee(String sign) {
        var salatUser = new SalatUser();
        salatUser.setLoginname(sign);
        var employee = new Employee();
        employee.setSalatUser(salatUser);
        employee.setSign(sign);
        return employee;
    }
}
