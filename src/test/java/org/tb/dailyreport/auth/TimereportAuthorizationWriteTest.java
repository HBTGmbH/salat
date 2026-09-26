package org.tb.dailyreport.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.lenient;
import static org.mockito.quality.Strictness.LENIENT;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.exception.ErrorCode.TR_CLOSED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_NOT_SELF;
import static org.tb.common.exception.ErrorCode.TR_COMMITTED_TIME_REPORT_REQ_MANAGER;
import static org.tb.common.exception.ErrorCode.TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.auth.service.AuthService;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.ErrorCode;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;

/**
 * Wer eine Buchung schreiben darf, hängt an ihrem Status und daran, wer fragt: die Person selbst,
 * die Geschäftsführung, die zuständige People Lead. Die Tabelle hält fest, was
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

    private TimereportAuthorization timereportAuthorization;
    private Employeecontract contract;

    @BeforeEach
    void setUp() {
        timereportAuthorization = new TimereportAuthorization(authorizedUser, authService);
        contract = new Employeecontract();
        contract.setEmployee(employee(OWNER));
        contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
    }

    /**
     * status, owner, manager, supervising people lead → the code of the denial, {@code null} when
     * writing is allowed.
     */
    static Stream<Arguments> writeAccess() {
        var cases = new ArrayList<Arguments>();
        for (var status : List.of(TIMEREPORT_STATUS_OPEN, TIMEREPORT_STATUS_COMMITED, TIMEREPORT_STATUS_CLOSED)) {
            for (var owner : List.of(true, false)) {
                for (var manager : List.of(true, false)) {
                    for (var supervisingPeopleLead : List.of(true, false)) {
                        cases.add(Arguments.of(status, owner, manager, supervisingPeopleLead,
                            expectedDenial(status, owner, manager, supervisingPeopleLead)));
                    }
                }
            }
        }
        return cases.stream();
    }

    private static ErrorCode expectedDenial(String status, boolean owner, boolean manager, boolean supervisingPeopleLead) {
        return switch (status) {
            case TIMEREPORT_STATUS_OPEN -> owner || manager ? null : TR_OPEN_TIME_REPORT_REQ_EMPLOYEE;
            case TIMEREPORT_STATUS_COMMITED -> !manager && !supervisingPeopleLead ? TR_COMMITTED_TIME_REPORT_REQ_MANAGER
                : owner ? TR_COMMITTED_TIME_REPORT_NOT_SELF
                : null;
            case TIMEREPORT_STATUS_CLOSED -> manager && !owner ? null : TR_CLOSED_TIME_REPORT_REQ_MANAGER;
            default -> throw new IllegalArgumentException(status);
        };
    }

    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3} -> {4}")
    @MethodSource("writeAccess")
    void checkAuthorizedDecidesByStatusAndWhoAsks(String status, boolean owner, boolean manager,
                                                  boolean supervisingPeopleLead, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead);

        var denial = catchThrowableOfType(AuthorizationException.class,
            () -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.WRITE));

        if (expectedDenial == null) {
            assertThat(denial).isNull();
        } else {
            assertThat(denial).isNotNull();
            assertThat(denial.getMessages()).extracting(message -> message.getErrorCode()).containsExactly(expectedDenial);
        }
    }

    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3} -> {4}")
    @MethodSource("writeAccess")
    void deletingIsDecidedTheSameWay(String status, boolean owner, boolean manager,
                                     boolean supervisingPeopleLead, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead);

        var check = assertThatCode(() -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.DELETE));

        if (expectedDenial == null) {
            check.doesNotThrowAnyException();
        } else {
            check.isInstanceOf(AuthorizationException.class);
        }
    }

    /** Die Frage ohne Buchung beantwortet {@code isWriteAllowed} genau so, wie {@code checkAuthorized} sie mit Buchung beantwortet (#760). */
    @ParameterizedTest(name = "{0}, owner {1}, manager {2}, supervising people lead {3} -> {4}")
    @MethodSource("writeAccess")
    void isWriteAllowedAgreesWithCheckAuthorized(String status, boolean owner, boolean manager,
                                                 boolean supervisingPeopleLead, ErrorCode expectedDenial) {
        loggedInAs(owner, manager, supervisingPeopleLead);

        var checkPasses = catchThrowableOfType(AuthorizationException.class,
            () -> timereportAuthorization.checkAuthorized(List.of(timereport(status)), AccessLevel.WRITE)) == null;

        assertThat(timereportAuthorization.isWriteAllowed(contract, status))
            .isEqualTo(checkPasses)
            .isEqualTo(expectedDenial == null);
    }

    private void loggedInAs(boolean owner, boolean manager, boolean supervisingPeopleLead) {
        var sign = owner ? OWNER : supervisingPeopleLead ? PEOPLE_LEAD : SOMEBODY_ELSE;
        if (owner && supervisingPeopleLead) {
            // one person as owner and supervisor of the same contract
            contract.getSupervisors().add(employee(OWNER));
        }
        lenient().when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
        lenient().when(authorizedUser.getLoginSign()).thenReturn(sign);
        lenient().when(authorizedUser.isManager()).thenReturn(manager);
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
