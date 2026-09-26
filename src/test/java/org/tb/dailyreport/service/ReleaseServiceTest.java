package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.auth.domain.AccessLevel;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.domain.SalatUser;
import org.tb.common.GlobalConstants;
import org.tb.common.exception.AuthorizationException;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.InvalidDataException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.service.MailService;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.auth.ReleaseAuthorization;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.domain.OvertimeBalance;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.ReviewPeriod;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.TimereportReview;
import org.tb.dailyreport.domain.TimereportReview.DayEntry;
import org.tb.dailyreport.domain.TimereportReview.DayFinding;
import org.tb.dailyreport.domain.TimereportReview.MonthGroup;
import org.tb.dailyreport.domain.TimereportReview.OrderGroup;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.preferences.EmployeePreferenceService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.OrderType;

@ExtendWith(MockitoExtension.class)
class ReleaseServiceTest {

    @InjectMocks
    private ReleaseService classUnderTest;
    @Mock
    private TimereportDAO timereportDAO;
    @Mock
    private TimereportRepository timereportRepository;
    @Mock
    private WorkingdayDAO workingdayDAO;
    @Mock
    private EmployeecontractDAO employeecontractDAO;
    @Mock
    private PublicholidayDAO publicholidayDAO;
    @Mock
    private TimereportService timereportService;
    @Mock
    private ReleaseAuthorization releaseAuthorization;
    @Mock
    private EmployeecontractService employeecontractService;
    @Mock
    private OvertimeService overtimeService;
    @Mock
    private AuthorizedUser authorizedUser;
    @Mock
    private TimereportAuthorization timereportAuthorization;
    @Mock
    private MailService mailService;
    @Mock
    private EmployeePreferenceService employeePreferenceService;

    /**
     * Ein Vertrag endet mitten im Monat, das Formular kennt aber nur Monate: der Monatsletzte
     * liegt dann hinter dem Vertragsende (#324).
     */
    @Nested
    class BeyondTheContractEnd {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        // ein Wochenende als Vertragslaufzeit: so bleibt kein Arbeitstag ohne Buchung zurück
        private static final LocalDate CONTRACT_START = LocalDate.of(2024, 1, 6);
        private static final LocalDate CONTRACT_END = LocalDate.of(2024, 1, 7);
        private static final LocalDate END_OF_MONTH = LocalDate.of(2024, 1, 31);

        @Test
        void releaseStopsAtTheContractEndInsteadOfBeingRefused() {
            // given a contract that ended in the middle of the chosen month
            final var contract = endedContract();
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);

            // when reviewing the whole month and releasing what the review shows
            final var period = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, END_OF_MONTH).period();
            classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, period.begin(), period.end());

            // then the contract end is what gets shown, released and stored
            assertThat(period).isEqualTo(new ReviewPeriod(CONTRACT_START, CONTRACT_END));
            verify(timereportDAO, atLeastOnce()).getOpenTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, CONTRACT_END);
            verify(employeecontractService).updateReportReleaseData(EMPLOYEE_CONTRACT_ID, CONTRACT_END, null);
        }

        @Test
        void acceptanceStopsAtTheContractEndInsteadOfBeingRefused() {
            // given a contract that ended in the middle of the chosen month, released until its end
            final var contract = endedContract();
            contract.setReportReleaseDate(CONTRACT_END);
            when(releaseAuthorization.isAcceptAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);

            // when accepting the whole month
            classUnderTest.acceptTimereports(EMPLOYEE_CONTRACT_ID, END_OF_MONTH);

            // then the contract end is what gets accepted and stored
            verify(timereportDAO).getCommitedTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, CONTRACT_END);
            verify(employeecontractService).updateReportReleaseData(EMPLOYEE_CONTRACT_ID, CONTRACT_END, CONTRACT_END);
            verify(overtimeService).updateOvertimeStatic(EMPLOYEE_CONTRACT_ID);
        }

        private Employeecontract endedContract() {
            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            employee.setSign("xx");
            final var contract = new Employeecontract();
            setField(contract, "id", EMPLOYEE_CONTRACT_ID);
            contract.setEmployee(employee);
            contract.setValidFrom(CONTRACT_START);
            contract.setValidUntil(CONTRACT_END);
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            return contract;
        }
    }

    @Nested
    class ValidateForRelease {
        @Test
        void whenNoWorkingDayWasSaved_shouldReturnError() {
            // given the Workingday object does not exist
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).minusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of());
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);


            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should return error about missing begin of working time
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_BEGIN_TIME_MISSING);
        }

        @Test
        void whenNoTimeReports_shouldNotReturnError() {
            // given no timeReports in the specified timeframe
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(List.of());
            Workingday workingday = new Workingday();
            workingday.setRefday(date);
            workingday.setType(NOT_WORKED);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(
                workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setValidFrom(date);
            contract.setEmployee(employee);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenLessThan6HoursBooked_shouldNotReturnError() {
            // given the timeReport for the day has less than 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).minusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenExactly6HoursBooked_shouldNotReturnError() {
            // given the timeReport for the day has exactly 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenMoreThan6HoursBookedAndNoBreak_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_BREAK_TOO_SHORT_6);
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsShorterThan30Minutes_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(25);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_BREAK_TOO_SHORT_6);
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsAtLeast30Minutes_shouldNotReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should not return any error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenExactly9HoursBookedAnd30MinutesBreak_shouldNotReturnError() {
            // given the timeReports for the day have exactly 9 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(3))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should not return any error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenMoreThan9HoursBookedAndNoBreak_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_BREAK_TOO_SHORT_9);
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsShorterThan45Minutes_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(40);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);
            //when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setValidFrom(LocalDate.of(2023, 10, 1));
            contract.setReportReleaseDate(LocalDate.of(2023, 12, 31));
            contract.setEmployee(employee);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_BREAK_TOO_SHORT_9);
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsAtLeast45Minutes_shouldNotReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(45);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of(workingday));

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setValidFrom(date);
            contract.setEmployee(employee);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then expect no error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenLessThan11HoursBetweenWorkingDays_shouldReturnError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO releaseTimeReport = TimereportDTO.builder()
                    .orderType(OrderType.STANDARD)
                    .referenceday(releaseDate)
                    .duration(Duration.ofHours(6))
                    .build();
            final List<TimereportDTO> releaseDateResult = List.of(releaseTimeReport);
            final TimereportDTO yesterdayTimeReport = TimereportDTO.builder()
                .orderType(OrderType.STANDARD)
                .referenceday(yesterdayDate)
                .duration(Duration.ofHours(1))
                .build();
            final List<TimereportDTO> yesterdayDateResult = List.of(yesterdayTimeReport);
            final Workingday releaseDay = new Workingday();
            releaseDay.setStarttimehour(6);
            releaseDay.setRefday(releaseDate);
            final Workingday yesterday = new Workingday();
            yesterday.setStarttimehour(18);
            yesterday.setStarttimeminute(1);
            yesterday.setRefday(yesterdayDate);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate)).thenReturn(releaseDateResult);
            when(timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, yesterdayDate)).thenReturn(yesterdayDateResult);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, yesterdayDate, releaseDate)).thenReturn(List.of(yesterday, releaseDay));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(LocalDate.of(2023, 10, 1));
            contract.setReportReleaseDate(LocalDate.of(2024, 1, 30));
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, releaseDate);

            // then expect an error regarding resttime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(releaseDay.getRefday());
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_REST_TIME_TOO_SHORT);
        }

        @Test
        void when11HoursBetweenWorkingDays_shouldNotReturnError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO timeReport = TimereportDTO.builder()
                .orderType(OrderType.STANDARD)
                .referenceday(releaseDate)
                .duration(Duration.ofHours(6).minusMinutes(1))
                .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday releaseDay = new Workingday();
            releaseDay.setStarttimehour(6);
            releaseDay.setRefday(releaseDate);
            final Workingday yesterday = new Workingday();
            yesterday.setStarttimehour(19);
            yesterday.setRefday(yesterdayDate);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate)).thenReturn(result);
            when(timereportDAO.getTimereportsByDateAndEmployeeContractId(employeeContractId, yesterdayDate)).thenReturn(List.of());
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, yesterdayDate, releaseDate)).thenReturn(List.of(yesterday, releaseDay));
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(LocalDate.of(2023, 10, 1));
            contract.setReportReleaseDate(LocalDate.of(2024, 1, 30));
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, releaseDate);

            // then expect no error to be returned
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenTimeReportIsForSuborderThatIsIrrelevantForWorkingDayContingent_shouldNotReturnError() {
            // given someone has booked 8 hours of vacation
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KRANK_URLAUB_ABWESEND)
                    .referenceday(date)
                    .duration(Duration.ofHours(8))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setValidFrom(date);
            contract.setEmployee(employee);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then expect no error to be returned
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenOnlyStandbyWasBookedWithoutWorkingDay_shouldNotReturnError() {
            // given a day on which nothing but standby was booked - no start of work, no break
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO standby = TimereportDTO.builder()
                    .orderType(OrderType.BEREITSCHAFT)
                    .referenceday(date)
                    .duration(Duration.ofHours(12))
                    .build();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(List.of(standby));
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(employeeContractId, date.minusDays(1), date)).thenReturn(List.of());
            when(timereportService.needsWorkingHoursLawValidation(employeeContractId)).thenReturn(true);

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then neither the missing begin nor the missing break is complained about
            assertThat(errors).isEmpty();
        }

        @Test
        void whenStandbyAndWorkTogetherAreExactly24Hours_shouldNotReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date))
                .thenReturn(dayOf(date, Duration.ofHours(8), Duration.ofHours(16)));
            whenContractStartsAt(employeeContractId, date);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then the day is still accepted
            assertThat(errors).isEmpty();
        }

        @Test
        void whenStandbyAndWorkTogetherExceed24Hours_shouldReturnError() {
            // given one minute more than the day has
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date))
                .thenReturn(dayOf(date, Duration.ofHours(8), Duration.ofHours(16).plusMinutes(1)));
            whenContractStartsAt(employeeContractId, date);

            // when validating
            final List<ServiceFeedbackMessage> errors = runValidateForRelease(employeeContractId, date);

            // then the release is refused, naming the day
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_DAY_LENGTH_TOO_LONG);
            assertThat(errors.getFirst().getArguments().getFirst()).isEqualTo(date);
        }

        private List<TimereportDTO> dayOf(LocalDate date, Duration worked, Duration standby) {
            return List.of(
                TimereportDTO.builder().orderType(OrderType.STANDARD).referenceday(date).duration(worked).build(),
                TimereportDTO.builder().orderType(OrderType.BEREITSCHAFT).referenceday(date).duration(standby).build());
        }

        private void whenContractStartsAt(long employeeContractId, LocalDate date) {
            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeecontractById(employeeContractId)).thenReturn(contract);
        }

        private List<ServiceFeedbackMessage> runValidateForRelease(long employeeContractId, LocalDate date) {
            try {
                classUnderTest.validateForRelease(employeeContractId, date);
                return List.of();
            } catch(ErrorCodeException e) {
                return e.getMessages();
            }
        }
    }

    /**
     * Jeder Arbeitstag des Freigabezeitraums braucht eine offene Buchung, sonst meldet die Freigabe
     * {@code WD_NO_TIMEREPORT} mit dem Datum. Die Tests halten das Verhalten fest, bevor die Regel
     * nach {@code UnbookedWorkingDays} umzieht (#1124), und stubben die drei Ladevorgänge mit genau
     * den Argumenten, die {@code validateForRelease} heute verwendet — die strikten Stubs melden
     * jede Abweichung.
     *
     * <p>Die Woche vom 04.03.2024 (Montag) bis 10.03.2024 hat keinen Feiertag; der 01.01.2024 der
     * übrigen Tests ist einer, nur eben nicht im Mock.
     */
    @Nested
    class WorkingDaysWithoutBooking {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        private static final LocalDate MONDAY = LocalDate.of(2024, 3, 4);
        private static final LocalDate TUESDAY = MONDAY.plusDays(1);
        private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
        private static final LocalDate THURSDAY = MONDAY.plusDays(3);
        private static final LocalDate FRIDAY = MONDAY.plusDays(4);
        private static final LocalDate SUNDAY = MONDAY.plusDays(6);

        @Test
        void everyWeekdayWithoutBookingIsReportedInDateOrder() {
            // given a contract starting on Monday and nothing booked
            contractFrom(MONDAY);
            givenLoads(MONDAY, FRIDAY, List.of(), List.of(), List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then each weekday is reported once, in date order
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().getFirst())
                .containsExactly(
                    tuple(ErrorCode.WD_NO_TIMEREPORT, MONDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, TUESDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, WEDNESDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, THURSDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, FRIDAY));
        }

        @Test
        void theWeekendIsNeverReported() {
            // given nothing booked
            contractFrom(MONDAY);
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(), List.of());

            // when releasing until Sunday
            final var errors = runValidateForRelease(SUNDAY);

            // then Saturday and Sunday are not among the reported days
            assertThat(reportedDays(errors)).containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void aPublicHolidayIsNotReported() {
            // given a public holiday on Wednesday, loaded for exactly the period
            contractFrom(MONDAY);
            givenLoads(MONDAY, FRIDAY, List.of(), List.of(), List.of(new Publicholiday(WEDNESDAY, "Feiertag")));

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then Wednesday is skipped
            assertThat(reportedDays(errors)).containsExactly(MONDAY, TUESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void aDayMarkedNotWorkedIsNotReportedButAWorkedDayWithoutBookingIs() {
            // given Thursday has a working day of type WORKED and Friday one of type NOT_WORKED
            contractFrom(MONDAY);
            givenLoads(MONDAY, FRIDAY, List.of(), List.of(workingday(THURSDAY, WORKED), workingday(FRIDAY, NOT_WORKED)), List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then only Friday is skipped: a working day without a booking is still a gap
            assertThat(reportedDays(errors)).containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY);
        }

        @Test
        void anOpenAbsenceCountsAsBooked() {
            // given an open absence booked on Tuesday
            contractFrom(MONDAY);
            final var absence = TimereportDTO.builder()
                .orderType(OrderType.KRANK_URLAUB_ABWESEND)
                .referenceday(TUESDAY)
                .duration(Duration.ofHours(8))
                .build();
            givenLoads(MONDAY, FRIDAY, List.of(absence), List.of(), List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then Tuesday is booked
            assertThat(reportedDays(errors)).containsExactly(MONDAY, WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void onlyTheDaysAfterTheLastReleaseAreChecked() {
            // given a contract released until Tuesday
            final var contract = contractFrom(MONDAY.minusDays(3));
            contract.setReportReleaseDate(TUESDAY);
            givenLoads(WEDNESDAY, FRIDAY, List.of(), List.of(), List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then the check starts on Wednesday
            assertThat(reportedDays(errors)).containsExactly(WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void theCheckStartsAtTheBeginOfTheContract() {
            // given a contract beginning on Wednesday
            contractFrom(WEDNESDAY);
            givenLoads(WEDNESDAY, FRIDAY, List.of(), List.of(), List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then Monday and Tuesday lie before the contract
            assertThat(reportedDays(errors)).containsExactly(WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void theCheckEndsAtTheEndOfTheContract() {
            // given a contract ending on Wednesday
            final var contract = contractFrom(MONDAY);
            contract.setValidUntil(WEDNESDAY);
            givenLoads(MONDAY, WEDNESDAY, List.of(), List.of(), List.of());

            // when releasing until the contract end
            final var errors = runValidateForRelease(WEDNESDAY);

            // then Thursday and Friday lie after the contract
            assertThat(reportedDays(errors)).containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        }

        @Test
        void findingsOfAllChecksAreSortedByDate() {
            // given the working hours law applies and Tuesday has work booked without a start of work
            contractFrom(MONDAY);
            final var work = TimereportDTO.builder()
                .orderType(OrderType.STANDARD)
                .referenceday(TUESDAY)
                .duration(Duration.ofHours(4))
                .build();
            givenLoads(MONDAY, FRIDAY, List.of(work), List.of(), List.of());
            when(timereportService.needsWorkingHoursLawValidation(EMPLOYEE_CONTRACT_ID)).thenReturn(true);
            when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, MONDAY)).thenReturn(List.of());

            // when releasing until Friday
            final var errors = runValidateForRelease(FRIDAY);

            // then the missing start of Tuesday stands between the days without booking
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().getFirst())
                .containsExactly(
                    tuple(ErrorCode.WD_NO_TIMEREPORT, MONDAY),
                    tuple(ErrorCode.WD_BEGIN_TIME_MISSING, TUESDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, WEDNESDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, THURSDAY),
                    tuple(ErrorCode.WD_NO_TIMEREPORT, FRIDAY));
        }

        private Employeecontract contractFrom(LocalDate validFrom) {
            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(validFrom);
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            return contract;
        }

        /** The three loads, each with exactly the arguments {@code validateForRelease} passes today. */
        private void givenLoads(LocalDate begin, LocalDate end, List<TimereportDTO> openTimereports,
                                List<Workingday> workingdays, List<Publicholiday> publicHolidays) {
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(EMPLOYEE_CONTRACT_ID, begin.minusDays(1), end)).thenReturn(workingdays);
            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, end)).thenReturn(openTimereports);
            when(publicholidayDAO.getPublicHolidaysBetween(begin, end)).thenReturn(publicHolidays);
        }

        private Workingday workingday(LocalDate date, Workingday.WorkingDayType type) {
            final var workingday = new Workingday();
            workingday.setRefday(date);
            workingday.setType(type);
            return workingday;
        }

        private List<LocalDate> reportedDays(List<ServiceFeedbackMessage> errors) {
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsOnly(ErrorCode.WD_NO_TIMEREPORT);
            return errors.stream().map(message -> (LocalDate) message.getArguments().getFirst()).toList();
        }

        private List<ServiceFeedbackMessage> runValidateForRelease(LocalDate releaseDate) {
            try {
                classUnderTest.validateForRelease(EMPLOYEE_CONTRACT_ID, releaseDate);
                return List.of();
            } catch(ErrorCodeException e) {
                return e.getMessages();
            }
        }
    }

    /**
     * Der Hinweis im Dashboard auf Arbeitstage der Vorwoche ohne Buchung (#1124). Die Uhr steht auf
     * Montag, den 28.09.2026: die Vorwoche ist der 21. bis 27.09., eine Woche ohne Feiertag.
     *
     * <p>Welche Buchungen zählen, prüft {@code TimereportRepositoryBookedDaysTest} an der echten
     * Abfrage; hier liefert sie nur die Tage.
     */
    @Nested
    @FixedClock("2026-09-28T08:00:00")
    class UnbookedWorkingDaysOfPreviousWeek {

        private static final long EMPLOYEE_CONTRACT_ID = 7L;
        private static final LocalDate MONDAY = LocalDate.of(2026, 9, 21);
        private static final LocalDate TUESDAY = MONDAY.plusDays(1);
        private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
        private static final LocalDate THURSDAY = MONDAY.plusDays(3);
        private static final LocalDate FRIDAY = MONDAY.plusDays(4);
        private static final LocalDate SUNDAY = MONDAY.plusDays(6);

        @Test
        void namesTheWeekdaysWithoutAnyBooking() {
            // given bookings on Monday and Wednesday
            authorized(contractFrom(MONDAY.minusYears(1)));
            givenLoads(MONDAY, SUNDAY, List.of(MONDAY, WEDNESDAY), List.of(), List.of());

            // when asking for the hint
            final var days = classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID);

            // then the other weekdays are named, and each load covered the week once
            assertThat(days).containsExactly(TUESDAY, THURSDAY, FRIDAY);
            verify(timereportRepository).findBookedDaysBetween(EMPLOYEE_CONTRACT_ID, MONDAY, SUNDAY);
            verify(workingdayDAO).getWorkingdaysByEmployeeContractId(EMPLOYEE_CONTRACT_ID, MONDAY, SUNDAY);
            verify(publicholidayDAO).getPublicHolidaysBetween(MONDAY, SUNDAY);
        }

        @Test
        @FixedClock("2026-09-27T20:00:00")
        void onSundayThePreviousWeekIsStillTheWeekBefore() {
            // given the clock stands on Sunday evening, the last day of the current week
            authorized(contractFrom(MONDAY.minusYears(1)));
            givenLoads(MONDAY.minusWeeks(1), SUNDAY.minusWeeks(1), List.of(), List.of(), List.of());

            // when asking for the hint
            final var days = classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID);

            // then it is about the 14th to the 20th
            assertThat(days).containsExactly(MONDAY.minusWeeks(1), TUESDAY.minusWeeks(1),
                WEDNESDAY.minusWeeks(1), THURSDAY.minusWeeks(1), FRIDAY.minusWeeks(1));
        }

        @Test
        @FixedClock("2026-09-28T00:05:00")
        void fromMondayOnTheWindowMovesOn() {
            // given the clock stands five minutes into Monday
            authorized(contractFrom(MONDAY.minusYears(1)));
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(), List.of());

            // when asking for the hint
            final var days = classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID);

            // then the week just ended is the previous week
            assertThat(days).containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void aPublicHolidayIsNotNamed() {
            authorized(contractFrom(MONDAY.minusYears(1)));
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(), List.of(new Publicholiday(THURSDAY, "Feiertag")));

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID))
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY, FRIDAY);
        }

        @Test
        void aDayMarkedNotWorkedIsNotNamed() {
            authorized(contractFrom(MONDAY.minusYears(1)));
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(workingday(FRIDAY, NOT_WORKED)), List.of());

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID))
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY, THURSDAY);
        }

        @Test
        void aContractBeginningMidweekIsCheckedFromItsBeginning() {
            authorized(contractFrom(WEDNESDAY));
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(), List.of());

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID))
                .containsExactly(WEDNESDAY, THURSDAY, FRIDAY);
        }

        @Test
        void aContractEndingMidweekIsCheckedUntilItsEnd() {
            final var contract = contractFrom(MONDAY.minusYears(1));
            contract.setValidUntil(WEDNESDAY);
            authorized(contract);
            givenLoads(MONDAY, SUNDAY, List.of(), List.of(), List.of());

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID))
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        }

        @Test
        void aWeekReleasedUpToMidweekCountsItsReleasedBookingsAsBooked() {
            // given the month ended on Thursday and was released, and every weekday is booked
            final var contract = contractFrom(MONDAY.minusYears(1));
            contract.setReportReleaseDate(THURSDAY);
            authorized(contract);
            givenLoads(MONDAY, SUNDAY, List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), List.of(), List.of());

            // when asking for the hint
            final var days = classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID);

            // then there is no gap, and the open bookings the release looks at were never asked for
            assertThat(days).isEmpty();
            verifyNoInteractions(timereportDAO);
        }

        @Test
        void aFreelancerGetsNoHint() {
            final var contract = contractFrom(MONDAY.minusYears(1));
            contract.setFreelancer(true);
            authorized(contract);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNothingLoaded();
        }

        @Test
        void aRestrictedPersonGetsNoHint() {
            final var contract = contractFrom(MONDAY.minusYears(1));
            contract.getEmployee().getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_RESTRICTED);
            authorized(contract);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNothingLoaded();
        }

        @Test
        void aContractWithoutDailyWorkingTimeGetsNoHint() {
            final var contract = contractFrom(MONDAY.minusYears(1));
            contract.setDailyWorkingTime(Duration.ZERO);
            authorized(contract);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNothingLoaded();
        }

        @Test
        void whoMayNotReleaseTheContractGetsNoHint() {
            final var contract = contractFrom(MONDAY.minusYears(1));
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(false);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNothingLoaded();
        }

        @Test
        void aRightToReadTheReleaseIsNotEnough() {
            // given a release rule that grants reading only
            final var contract = contractFrom(MONDAY.minusYears(1));
            lenient().when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.READ)).thenReturn(true);
            lenient().when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(false);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNothingLoaded();
        }

        @Test
        void anUnknownContractGetsNoHint() {
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(null);

            assertThat(classUnderTest.getUnbookedWorkingDaysOfPreviousWeek(EMPLOYEE_CONTRACT_ID)).isEmpty();
            verifyNoInteractions(releaseAuthorization);
            verifyNothingLoaded();
        }

        private Employeecontract contractFrom(LocalDate validFrom) {
            final var salatUser = new SalatUser();
            salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var employee = new Employee();
            employee.setSalatUser(salatUser);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(validFrom);
            contract.setDailyWorkingTime(Duration.ofHours(8));
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            return contract;
        }

        private void authorized(Employeecontract contract) {
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);
        }

        private void givenLoads(LocalDate monday, LocalDate sunday, List<LocalDate> bookedDays,
                                List<Workingday> workingdays, List<Publicholiday> publicHolidays) {
            when(timereportRepository.findBookedDaysBetween(EMPLOYEE_CONTRACT_ID, monday, sunday)).thenReturn(bookedDays);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(EMPLOYEE_CONTRACT_ID, monday, sunday)).thenReturn(workingdays);
            when(publicholidayDAO.getPublicHolidaysBetween(monday, sunday)).thenReturn(publicHolidays);
        }

        private Workingday workingday(LocalDate date, Workingday.WorkingDayType type) {
            final var workingday = new Workingday();
            workingday.setRefday(date);
            workingday.setType(type);
            return workingday;
        }

        private void verifyNothingLoaded() {
            verifyNoInteractions(timereportRepository, timereportDAO, workingdayDAO, publicholidayDAO);
        }
    }

    /**
     * Ein Befund über den ganzen Zeitraum ersetzt die Prüfung der Tage (#760). Einer davon ist der
     * leere Zeitraum: der gewählte Monat ist schon freigegeben ({@code RL-0009}). Bis #760 scheiterte
     * die Prüfung an einem Monat vor der letzten Freigabe mit einer {@link IllegalArgumentException}
     * aus der Regel „Arbeitstag ohne Buchung", und die Freigabe endete auf der Fehlerseite.
     */
    @Nested
    class PeriodFindings {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        private static final LocalDate CONTRACT_START = LocalDate.of(2023, 10, 1);
        private static final LocalDate RELEASED_UNTIL = LocalDate.of(2024, 2, 29);

        @Test
        void aMonthBeforeTheLastReleaseIsNothingToRelease() {
            // given a contract released until the end of February
            contractReleasedUntil(RELEASED_UNTIL);

            // when releasing until the end of January
            final var errors = runValidateForRelease(LocalDate.of(2024, 1, 31));

            // then there is nothing to release, and no day is looked at
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_NOTHING_TO_RELEASE);
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, timereportService);
        }

        @Test
        void theDayOfTheLastReleaseAgainIsNothingToRelease() {
            // given a contract released until the end of February
            contractReleasedUntil(RELEASED_UNTIL);

            // when releasing until the end of February once more
            final var errors = runValidateForRelease(RELEASED_UNTIL);

            // then there is nothing to release
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_NOTHING_TO_RELEASE);
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, timereportService);
        }

        @Test
        void theDayAfterTheLastReleaseIsChecked() {
            // given a contract released until Thursday, the end of February, and nothing booked since
            contractReleasedUntil(RELEASED_UNTIL);
            final var friday = RELEASED_UNTIL.plusDays(1);

            // when releasing until Friday
            final var errors = runValidateForRelease(friday);

            // then the period consists of Friday, and Friday lacks a booking
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().getFirst())
                .containsExactly(tuple(ErrorCode.WD_NO_TIMEREPORT, friday));
        }

        @Test
        void aDateBeforeTheAcceptanceIsReportedInsteadOfNothingToRelease() {
            // given a contract released until February and accepted until January
            final var contract = contractReleasedUntil(RELEASED_UNTIL);
            contract.setReportAcceptanceDate(LocalDate.of(2024, 1, 31));

            // when releasing until the end of December
            final var errors = runValidateForRelease(LocalDate.of(2023, 12, 31));

            // then the acceptance is what stands in the way
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_BEFORE_ACCEPTANCE);
        }

        @Test
        void aDateBeforeTheContractIsReportedInsteadOfNothingToRelease() {
            // given a contract released until February
            contractReleasedUntil(RELEASED_UNTIL);

            // when releasing until a day before the contract began
            final var errors = runValidateForRelease(CONTRACT_START.minusDays(1));

            // then the date itself is invalid
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_INVALID);
        }

        /**
         * Ein Vertrag ohne Ende setzt der Freigabe keine Grenze, und der Monat kommt aus der Anfrage.
         * Weiter als bis zum Ende des Monats in einem Jahr reicht sie deshalb nicht — dahinter ist das
         * Datum ungültig, und kein Tag wird angesehen.
         */
        @Test
        @FixedClock("2024-03-15T10:00:00")
        void aDateAfterTheEndOfTheMonthAYearAheadIsInvalid() {
            // given a contract without end, released until February
            contractReleasedUntil(RELEASED_UNTIL);

            // when releasing until the day after the end of March next year
            final var errors = runValidateForRelease(LocalDate.of(2025, 4, 1));

            // then the date itself is invalid, and no day is looked at
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_INVALID);
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, timereportService);
        }

        @Test
        @FixedClock("2024-03-15T10:00:00")
        void theEndOfTheMonthAYearAheadIsStillChecked() {
            // given a contract without end, released until February, and nothing booked since
            contractReleasedUntil(RELEASED_UNTIL);

            // when releasing until the end of March next year
            final var errors = runValidateForRelease(LocalDate.of(2025, 3, 31));

            // then the days are checked: the first working day of the period lacks a booking
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).doesNotContain(ErrorCode.RL_RELEASE_DATE_INVALID);
            assertThat(errors.getFirst().getErrorCode()).isEqualTo(ErrorCode.WD_NO_TIMEREPORT);
            assertThat(errors.getFirst().getArguments()).containsExactly(RELEASED_UNTIL.plusDays(1));
        }

        private Employeecontract contractReleasedUntil(LocalDate releasedUntil) {
            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(CONTRACT_START);
            contract.setReportReleaseDate(releasedUntil);
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            return contract;
        }

        private List<ServiceFeedbackMessage> runValidateForRelease(LocalDate releaseDate) {
            try {
                classUnderTest.validateForRelease(EMPLOYEE_CONTRACT_ID, releaseDate);
                return List.of();
            } catch(ErrorCodeException e) {
                return e.getMessages();
            }
        }
    }

    /**
     * Die Übersicht vor der Freigabe (#760): welchen Zeitraum sie zeigt, welcher Befund an welchem
     * Tag steht und wer darin bearbeiten und anlegen darf. Freigegeben ist bis Sonntag, 03.03.2024;
     * die Woche danach hat keinen Feiertag, und jeder ihrer Arbeitstage ist gebucht, wo ein Test
     * nichts anderes sagt.
     */
    @Nested
    class ReviewRelease {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        private static final String OWNER = "xx";
        private static final String PEOPLE_LEAD = "pl";
        private static final String MANAGER = "gf";
        private static final LocalDate CONTRACT_START = LocalDate.of(2024, 1, 1);
        private static final LocalDate RELEASED_UNTIL = LocalDate.of(2024, 3, 3);
        private static final LocalDate MONDAY = LocalDate.of(2024, 3, 4);
        private static final LocalDate TUESDAY = MONDAY.plusDays(1);
        private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
        private static final LocalDate THURSDAY = MONDAY.plusDays(3);
        private static final LocalDate FRIDAY = MONDAY.plusDays(4);

        @Test
        void thePeriodStartsTheDayAfterTheLastRelease() {
            releasableContract();
            givenBookings(MONDAY, FRIDAY, week());

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.period()).isEqualTo(new ReviewPeriod(MONDAY, FRIDAY));
            assertThat(review.releasedUntil()).isEqualTo(RELEASED_UNTIL);
            assertThat(review.actionAllowed()).isTrue();
        }

        @Test
        void thePeriodStartsAtTheContractBeginWithoutRelease() {
            final var contract = releasableContract();
            contract.setValidFrom(WEDNESDAY);
            contract.setReportReleaseDate(null);
            givenBookings(WEDNESDAY, FRIDAY, week().subList(2, 5));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.period()).isEqualTo(new ReviewPeriod(WEDNESDAY, FRIDAY));
            assertThat(review.releasedUntil()).isNull();
        }

        /** Der Monat reicht über das Vertragsende hinaus — gezeigt wird bis zum Vertragsende, ohne Befund (#324). */
        @Test
        void thePeriodIsLimitedToTheContractEnd() {
            final var contract = releasableContract();
            contract.setValidUntil(WEDNESDAY);
            givenBookings(MONDAY, WEDNESDAY, week().subList(0, 3));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 3, 31));

            assertThat(review.period()).isEqualTo(new ReviewPeriod(MONDAY, WEDNESDAY));
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isTrue();
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days).extracting(DayEntry::date)
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        }

        /**
         * Die Ruhezeit am ersten Tag des Zeitraums rechnet mit dem Vortag, den die Prüfung dafür
         * nachlädt. Der Befund steht am ersten Tag, der Vortag selbst gehört nicht zur Übersicht.
         */
        @Test
        void theDayBeforeIsUsedForTheRestTimeButNotShown() {
            final var contract = releasableContract();
            contract.setReportReleaseDate(MONDAY);
            final var tuesday = booking(2, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(6));
            final var monday = booking(1, MONDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(1));
            givenBookings(TUESDAY, TUESDAY, List.of(tuesday));
            when(timereportService.needsWorkingHoursLawValidation(EMPLOYEE_CONTRACT_ID)).thenReturn(true);
            when(timereportDAO.getTimereportsByDateAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, MONDAY)).thenReturn(List.of(monday));
            givenWorkingdays(TUESDAY, TUESDAY, List.of(workingday(MONDAY, 18, 1), workingday(TUESDAY, 6, 0)));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, TUESDAY);

            assertThat(review.dayFindings()).extracting(DayFinding::date, finding -> finding.message().getErrorCode())
                .containsExactly(tuple(TUESDAY, ErrorCode.WD_REST_TIME_TOO_SHORT));
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days).extracting(DayEntry::date).containsExactly(TUESDAY);
            assertThat(review.byOrder()).flatExtracting(OrderGroup::timereports).containsExactly(tuesday);
            assertThat(review.beforePeriod()).isEmpty();
            assertThat(review.actionAllowed()).isFalse();
        }

        @Test
        void findingsAreAttachedToTheirDay() {
            releasableContract();
            final var standby = booking(6, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.BEREITSCHAFT, Duration.ofHours(16).plusMinutes(1));
            final var bookings = new ArrayList<>(week());
            bookings.add(standby);
            givenBookings(MONDAY, FRIDAY, bookings);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.dayFindings()).extracting(DayFinding::date, finding -> finding.message().getErrorCode())
                .containsExactly(tuple(WEDNESDAY, ErrorCode.WD_DAY_LENGTH_TOO_LONG));
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days)
                .extracting(DayEntry::date, day -> day.findings().size())
                .containsExactly(tuple(MONDAY, 0), tuple(TUESDAY, 0), tuple(WEDNESDAY, 1), tuple(THURSDAY, 0), tuple(FRIDAY, 0));
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isFalse();
        }

        /**
         * Eine offene Buchung vor dem Zeitraum gibt die Freigabe mit frei. Ihr Befund steht bei denen
         * über den Zeitraum, denn ihr Tag ist nicht Teil der Übersicht — er sperrt trotzdem.
         */
        @Test
        void aFindingOutsideThePeriodIsReportedPeriodWide() {
            releasableContract();
            final var stray = booking(9, RELEASED_UNTIL.minusDays(1), GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(25));
            final var open = new ArrayList<>(week());
            open.add(stray);
            givenBookings(MONDAY, FRIDAY, open, week());

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().getFirst())
                .containsExactly(tuple(ErrorCode.WD_DAY_LENGTH_TOO_LONG, stray.getReferenceday()));
            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.beforePeriod()).containsExactly(stray);
            assertThat(review.byOrder()).isNotEmpty();
            assertThat(review.actionAllowed()).isFalse();
        }

        @Test
        void openBookingsBeforeThePeriodAreListedApart() {
            releasableContract();
            final var stray = booking(9, RELEASED_UNTIL.minusDays(1), GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(2));
            final var open = new ArrayList<>(week());
            open.add(stray);
            givenBookings(MONDAY, FRIDAY, open, week());

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.beforePeriod()).containsExactly(stray);
            assertThat(review.byOrder()).flatExtracting(OrderGroup::timereports).doesNotContain(stray);
            assertThat(review.timereportCount()).isEqualTo(5);
            assertThat(review.actionAllowed()).isTrue();
        }

        @Test
        void aWorkingDayWithoutBookingIsAFinding() {
            releasableContract();
            final var withoutWednesday = week().stream().filter(booking -> !booking.getReferenceday().equals(WEDNESDAY)).toList();
            givenBookings(MONDAY, FRIDAY, withoutWednesday);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.dayFindings()).extracting(DayFinding::date, finding -> finding.message().getErrorCode())
                .containsExactly(tuple(WEDNESDAY, ErrorCode.WD_NO_TIMEREPORT));
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days)
                .extracting(DayEntry::date, DayEntry::withoutBooking)
                .containsExactly(tuple(MONDAY, false), tuple(TUESDAY, false), tuple(WEDNESDAY, true), tuple(THURSDAY, false), tuple(FRIDAY, false));
            assertThat(review.actionAllowed()).isFalse();
        }

        /** Nur eine offene Buchung macht einen Tag zu einem gebuchten, denn nur sie gibt die Freigabe frei. */
        @Test
        void aDayWithOnlyAReleasedBookingIsWithoutBooking() {
            releasableContract();
            final var committed = booking(3, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(8));
            final var open = week().stream().filter(booking -> !booking.getReferenceday().equals(WEDNESDAY)).toList();
            final var listed = new ArrayList<>(open);
            listed.add(committed);
            givenBookings(MONDAY, FRIDAY, open, listed);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            final var wednesday = review.byMonth().getFirst().days().get(2);
            assertThat(wednesday.timereports()).containsExactly(committed);
            assertThat(wednesday.withoutBooking()).isTrue();
        }

        @Test
        void aShortDayIsNotAFinding() {
            releasableContract();
            final var bookings = week().stream()
                .map(booking -> booking.getReferenceday().equals(THURSDAY)
                    ? booking(booking.getId(), THURSDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofMinutes(30))
                    : booking)
                .toList();
            givenBookings(MONDAY, FRIDAY, bookings);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isTrue();
        }

        @Test
        void theListsShowEveryBookingOfThePeriodAndTheStandbyApart() {
            releasableContract();
            final var standby = booking(6, FRIDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.BEREITSCHAFT, Duration.ofHours(3));
            final var bookings = new ArrayList<>(week());
            bookings.add(standby);
            givenBookings(MONDAY, FRIDAY, bookings);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.timereportCount()).isEqualTo(6);
            assertThat(review.standby()).isEqualTo(Duration.ofHours(3));
            assertThat(review.byOrder()).extracting(OrderGroup::suborderId, OrderGroup::standby, OrderGroup::duration)
                .containsExactly(tuple(10L, false, Duration.ofHours(40)), tuple(30L, true, Duration.ofHours(3)));
            assertThat(review.byMonth()).extracting(MonthGroup::workingTime, MonthGroup::standby)
                .containsExactly(tuple(Duration.ofHours(40), Duration.ofHours(3)));
        }

        /** Ein Tag, der als nicht gearbeitet markiert ist, ist kein Tag ohne Buchung, und die Übersicht sagt, warum. */
        @Test
        void aDayMarkedNotWorkedIsShownAsSuch() {
            releasableContract();
            final var withoutWednesday = week().stream().filter(booking -> !booking.getReferenceday().equals(WEDNESDAY)).toList();
            givenBookings(MONDAY, FRIDAY, withoutWednesday);
            final var notWorked = workingday(WEDNESDAY, 0, 0);
            notWorked.setType(NOT_WORKED);
            givenWorkingdays(MONDAY, FRIDAY, List.of(notWorked));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            final var wednesday = review.byMonth().getFirst().days().get(2);
            assertThat(wednesday.notWorked()).isTrue();
            assertThat(wednesday.withoutBooking()).isFalse();
            assertThat(review.actionAllowed()).isTrue();
        }

        @Test
        void aMonthBeforeTheLastReleaseIsNothingToRelease() {
            final var contract = releasableContract();
            contract.setReportReleaseDate(LocalDate.of(2024, 3, 31));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 2, 29));

            assertThat(review.period().isEmpty()).isTrue();
            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_NOTHING_TO_RELEASE);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        @Test
        void aReleaseDateBeforeTheAcceptanceIsPeriodWide() {
            final var contract = releasableContract();
            contract.setReportReleaseDate(LocalDate.of(2024, 3, 31));
            contract.setReportAcceptanceDate(LocalDate.of(2024, 3, 15));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 2, 29));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_BEFORE_ACCEPTANCE);
            assertThat(review.acceptedUntil()).isEqualTo(LocalDate.of(2024, 3, 15));
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        @Test
        void aMonthBeforeTheContractIsPeriodWide() {
            releasableContract();

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, CONTRACT_START.minusDays(1));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_INVALID);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        /**
         * Der Monat steht in der Adresse der Übersicht. Ohne Grenze stellte ein einziger Aufruf für
         * einen Vertrag ohne Ende Befunde und Tage über Jahrzehnte zusammen — die Übersicht bleibt bei
         * dem einen Befund über den Zeitraum.
         */
        @Test
        @FixedClock("2024-03-15T10:00:00")
        void aMonthFarAheadIsPeriodWide() {
            releasableContract();

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, LocalDate.of(2099, 12, 31));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_INVALID);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        @Test
        void theReviewRequiresTheReleaseAuthorization() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(false);

            final var denial = catchThrowableOfType(AuthorizationException.class,
                () -> classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY));

            assertThat(denial.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_NOT_ALLOWED);
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, overtimeService);
        }

        @Test
        void anUnknownContractIsInvalidData() {
            final var thrown = catchThrowableOfType(InvalidDataException.class,
                () -> classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY));

            assertThat(thrown.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND);
            verifyNoInteractions(releaseAuthorization);
        }

        /** Offene Buchungen einer anderen Person bearbeitet nur die Geschäftsführung (#760, Fallstrick). */
        @Test
        void aPeopleLeadGetsNoEditOrCreateLinksForOpenBookings() {
            releasableContract();
            givenBookings(MONDAY, FRIDAY, week());
            loggedInAs(PEOPLE_LEAD, false, true);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).isEmpty();
            assertThat(review.canCreate()).isFalse();
            assertThat(review.ownContract()).isFalse();
        }

        @Test
        void theManagerGetsEditAndCreateLinks() {
            releasableContract();
            final var committed = booking(6, MONDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(1));
            final var listed = new ArrayList<>(week());
            listed.add(committed);
            givenBookings(MONDAY, FRIDAY, week(), listed);
            loggedInAs(MANAGER, true, true);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
            assertThat(review.canCreate()).isTrue();
        }

        @Test
        void theOwnerGetsEditAndCreateLinksForOpenBookings() {
            releasableContract();
            final var committed = booking(6, MONDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(1));
            final var listed = new ArrayList<>(week());
            listed.add(committed);
            givenBookings(MONDAY, FRIDAY, week(), listed);
            loggedInAs(OWNER, false, false);

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
            assertThat(review.canCreate()).isTrue();
            assertThat(review.ownContract()).isTrue();
        }

        @Test
        void theBalanceComesFromTheOvertimeCalculation() {
            final var contract = releasableContract();
            contract.setDailyWorkingTime(Duration.ofHours(8));
            givenBookings(MONDAY, FRIDAY, week());
            final var balance = new OvertimeBalance(Duration.ofHours(40), Duration.ofHours(40), Duration.ZERO, Duration.ZERO);
            when(overtimeService.calculateOvertimeBalance(EMPLOYEE_CONTRACT_ID, MONDAY, FRIDAY)).thenReturn(Optional.of(balance));

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.balance()).isEqualTo(balance);
            assertThat(review.overtimeAccount()).isTrue();
        }

        @Test
        void aContractWithoutDailyWorkingTimeHasNoBalance() {
            releasableContract();
            givenBookings(MONDAY, FRIDAY, week());

            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.balance()).isNull();
            assertThat(review.overtimeAccount()).isFalse();
            assertThat(review.byOrder()).isNotEmpty();
            verifyNoInteractions(overtimeService);
        }

        private void assertThatTheReviewShowsNothingButItsFindings(TimereportReview review) {
            assertThat(review.balance()).isNull();
            assertThat(review.byOrder()).isEmpty();
            assertThat(review.byMonth()).isEmpty();
            assertThat(review.beforePeriod()).isEmpty();
            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.timereportCount()).isZero();
            assertThat(review.actionAllowed()).isFalse();
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, overtimeService);
        }

        /** The loads of the check and of the overview; the check reads the open bookings, the overview all of the period. */
        private void givenBookings(LocalDate begin, LocalDate end, List<TimereportDTO> open) {
            givenBookings(begin, end, open, open);
        }

        private void givenBookings(LocalDate begin, LocalDate end, List<TimereportDTO> open, List<TimereportDTO> listed) {
            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, end)).thenReturn(open);
            when(timereportDAO.getTimereportsByDatesAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, begin, end)).thenReturn(listed);
        }

        /** The check loads the working days from the day before, the overview those of the period. */
        private void givenWorkingdays(LocalDate begin, LocalDate end, List<Workingday> fromTheDayBefore) {
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(EMPLOYEE_CONTRACT_ID, begin.minusDays(1), end)).thenReturn(fromTheDayBefore);
            when(workingdayDAO.getWorkingdaysByEmployeeContractId(EMPLOYEE_CONTRACT_ID, begin, end))
                .thenReturn(fromTheDayBefore.stream().filter(workingday -> !workingday.getRefday().isBefore(begin)).toList());
        }

        /** Asks the real rule of {@link TimereportAuthorization} as the given person; it needs no rule of the rule engine. */
        private void loggedInAs(String sign, boolean manager, boolean peopleLead) {
            lenient().when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
            lenient().when(authorizedUser.isManager()).thenReturn(manager);
            lenient().when(authorizedUser.isPeopleLead()).thenReturn(peopleLead);
            final var rule = new TimereportAuthorization(authorizedUser, null);
            when(timereportAuthorization.isWriteAllowed(any(), any()))
                .thenAnswer(invocation -> rule.isWriteAllowed(invocation.getArgument(0), invocation.getArgument(1)));
        }

        private Employeecontract releasableContract() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);
            return contract;
        }

        private Employeecontract contract() {
            final var contract = new Employeecontract();
            setField(contract, "id", EMPLOYEE_CONTRACT_ID);
            contract.setEmployee(employee(OWNER));
            contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
            contract.setValidFrom(CONTRACT_START);
            contract.setReportReleaseDate(RELEASED_UNTIL);
            return contract;
        }

        private Employee employee(String sign) {
            final var salatUser = new SalatUser();
            salatUser.setLoginname(sign);
            salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var employee = new Employee();
            employee.setSalatUser(salatUser);
            employee.setSign(sign);
            employee.setFirstname("Vorname");
            employee.setLastname(sign);
            return employee;
        }

        /** Eight open hours on each weekday, ids 1 to 5. */
        private List<TimereportDTO> week() {
            return List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).stream()
                .map(day -> booking(day.getDayOfWeek().getValue(), day, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(8)))
                .toList();
        }

        private TimereportDTO booking(long id, LocalDate date, String status, OrderType orderType, Duration duration) {
            final var standby = orderType == OrderType.BEREITSCHAFT;
            return TimereportDTO.builder()
                .id(id)
                .referenceday(date)
                .status(status)
                .orderType(orderType)
                .suborderId(standby ? 30L : 10L)
                .completeOrderSign(standby ? "ALPHA/09" : "ALPHA/01")
                .customerorderSign("ALPHA")
                .duration(duration)
                .build();
        }

        private Workingday workingday(LocalDate date, int startHour, int startMinute) {
            final var workingday = new Workingday();
            workingday.setRefday(date);
            workingday.setStarttimehour(startHour);
            workingday.setStarttimeminute(startMinute);
            return workingday;
        }
    }

    /**
     * Die Freigabe aus der Übersicht (#760) gibt genau den gezeigten Zeitraum frei. Freigegeben ist
     * bis Donnerstag, 28.03.2024; die Übersicht über den März zeigt also Freitag bis Sonntag, und
     * nur der Freitag ist ein Arbeitstag.
     */
    @Nested
    class ReleaseReviewedPeriod {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        private static final long TIMEREPORT_ID = 7L;
        private static final String OWNER = "xx";
        private static final String PEOPLE_LEAD = "pl";
        private static final LocalDate CONTRACT_START = LocalDate.of(2024, 1, 1);
        private static final LocalDate RELEASED_UNTIL = LocalDate.of(2024, 3, 28);
        private static final LocalDate FRIDAY = LocalDate.of(2024, 3, 29);
        private static final LocalDate END_OF_MONTH = LocalDate.of(2024, 3, 31);

        @Test
        void releasesWhatTheReviewShowedAndStoresItsEnd() {
            releasableContract();
            givenAnOpenBookingOnFriday();
            when(authorizedUser.getLoginSign()).thenReturn(PEOPLE_LEAD);

            final var period = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, END_OF_MONTH).period();
            classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, period.begin(), period.end());

            assertThat(period).isEqualTo(new ReviewPeriod(FRIDAY, END_OF_MONTH));
            verify(timereportService).updateReleaseData(eq(TIMEREPORT_ID), eq(GlobalConstants.TIMEREPORT_STATUS_COMMITED),
                eq(PEOPLE_LEAD), any(LocalDateTime.class), isNull(), isNull());
            verify(employeecontractService).updateReportReleaseData(EMPLOYEE_CONTRACT_ID, END_OF_MONTH, null);
            verify(mailService).sendEmail(anyString(), anyString(), any(), any());
        }

        /** Etwa in einem zweiten Fenster: die Übersicht zeigte ab Freitag, freigegeben ist inzwischen bis Monatsende. */
        @Test
        void rejectsWhenTheReleaseDateMovedSinceTheReview() {
            final var contract = releasableContract();
            contract.setReportReleaseDate(END_OF_MONTH);

            final var errors = runRelease(FRIDAY, END_OF_MONTH);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            assertThatNothingWasReleased();
        }

        /** Zweimal hintereinander abgeschickt: die zweite Freigabe findet den Zeitraum schon freigegeben. */
        @Test
        void rejectsTheSecondOfTwoSubmits() {
            final var contract = releasableContract();
            givenAnOpenBookingOnFriday();
            when(authorizedUser.getLoginSign()).thenReturn(OWNER);
            classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, FRIDAY, END_OF_MONTH);
            contract.setReportReleaseDate(END_OF_MONTH);

            final var errors = runRelease(FRIDAY, END_OF_MONTH);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            verify(employeecontractService).updateReportReleaseData(EMPLOYEE_CONTRACT_ID, END_OF_MONTH, null);
            verify(mailService).sendEmail(anyString(), anyString(), any(), any());
        }

        @Test
        void rejectsWhenTheContractEndMovedBeforeTheReviewedEnd() {
            final var contract = releasableContract();
            contract.setValidUntil(FRIDAY);

            final var errors = runRelease(FRIDAY, END_OF_MONTH);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            assertThatNothingWasReleased();
        }

        /** Die Übersicht endete am Vertragsende mitten im Monat; verlängert endet der Monat jetzt später. */
        @Test
        void rejectsWhenAContractEndingInTheMonthWasExtended() {
            final var contract = releasableContract();
            contract.setValidUntil(null);

            final var errors = runRelease(FRIDAY, FRIDAY);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            assertThatNothingWasReleased();
        }

        /** Endete die Übersicht am Monatsende, ändert eine Verlängerung des Vertrags an ihr nichts. */
        @Test
        void keepsTheReviewedEndWhenTheContractWasExtendedBeyondIt() {
            final var contract = releasableContract();
            contract.setValidUntil(LocalDate.of(2024, 6, 30));
            givenAnOpenBookingOnFriday();

            classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, FRIDAY, END_OF_MONTH);

            verify(employeecontractService).updateReportReleaseData(EMPLOYEE_CONTRACT_ID, END_OF_MONTH, null);
        }

        /** Die Übersicht endet an einem Monatsletzten oder am Vertragsende — ein anderes Ende hat sie nie gezeigt. */
        @Test
        void rejectsAnEndInTheMiddleOfTheMonth() {
            releasableContract();

            final var errors = runRelease(FRIDAY, FRIDAY);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            assertThatNothingWasReleased();
        }

        @Test
        void rejectsABeginTheReviewCannotHaveShown() {
            releasableContract();

            final var errors = runRelease(CONTRACT_START, END_OF_MONTH);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_REVIEWED_PERIOD_CHANGED);
            assertThatNothingWasReleased();
        }

        @Test
        void stillThrowsTheFindings() {
            releasableContract();

            final var errors = runRelease(FRIDAY, END_OF_MONTH);

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode, message -> message.getArguments().getFirst())
                .containsExactly(tuple(ErrorCode.WD_NO_TIMEREPORT, FRIDAY));
            verify(employeecontractService, never()).updateReportReleaseData(any(), any(), any());
            verifyNoInteractions(timereportRepository, mailService);
        }

        /**
         * Die Übersicht über einen Monat vor der letzten Freigabe zeigt einen leeren Zeitraum. Ihn
         * freizugeben scheiterte bis #760 mit einer {@link IllegalArgumentException} und endete auf
         * der Fehlerseite; jetzt ist es der Befund {@code RL-0009}, und das Freigabedatum bleibt
         * stehen.
         */
        @Test
        void aMonthBeforeTheLastReleaseIsNothingToRelease() {
            final var contract = releasableContract();
            final var endOfFebruary = LocalDate.of(2024, 2, 29);
            final var review = classUnderTest.reviewRelease(EMPLOYEE_CONTRACT_ID, endOfFebruary);

            final var errors = runRelease(review.period().begin(), review.period().end());

            assertThat(review.period()).isEqualTo(new ReviewPeriod(FRIDAY, endOfFebruary));
            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_NOTHING_TO_RELEASE);
            assertThat(contract.getReportReleaseDate()).isEqualTo(RELEASED_UNTIL);
            assertThatNothingWasReleased();
        }

        /** Das Freigeben selbst hält dieselbe Grenze wie die Übersicht, auch mit einem Zeitraum von Hand. */
        @Test
        @FixedClock("2024-03-15T10:00:00")
        void aPeriodFarAheadIsNotReleased() {
            releasableContract();

            final var errors = runRelease(FRIDAY, LocalDate.of(2099, 12, 31));

            assertThat(errors).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_DATE_INVALID);
            assertThatNothingWasReleased();
        }

        @Test
        void rejectsWithoutReleaseAuthorization() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(false);

            final var denial = catchThrowableOfType(AuthorizationException.class,
                () -> classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, FRIDAY, END_OF_MONTH));

            assertThat(denial.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_RELEASE_NOT_ALLOWED);
            assertThatNothingWasReleased();
        }

        @Test
        void anUnknownContractIsInvalidData() {
            final var thrown = catchThrowableOfType(InvalidDataException.class,
                () -> classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, FRIDAY, END_OF_MONTH));

            assertThat(thrown.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND);
            verifyNoInteractions(releaseAuthorization, employeecontractService);
        }

        private List<ServiceFeedbackMessage> runRelease(LocalDate reviewedBegin, LocalDate reviewedEnd) {
            final var thrown = catchThrowableOfType(BusinessRuleException.class,
                () -> classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, reviewedBegin, reviewedEnd));
            assertThat(thrown).as("the release is refused").isNotNull();
            return thrown.getMessages();
        }

        private void assertThatNothingWasReleased() {
            verifyNoInteractions(timereportDAO, timereportRepository, employeecontractService, mailService);
        }

        private void givenAnOpenBookingOnFriday() {
            final var booking = TimereportDTO.builder()
                .id(TIMEREPORT_ID)
                .referenceday(FRIDAY)
                .status(GlobalConstants.TIMEREPORT_STATUS_OPEN)
                .orderType(OrderType.STANDARD)
                .duration(Duration.ofHours(8))
                .build();
            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, END_OF_MONTH)).thenReturn(List.of(booking));
            when(timereportRepository.findById(TIMEREPORT_ID)).thenReturn(Optional.of(new Timereport()));
        }

        private Employeecontract releasableContract() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isReleaseAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);
            return contract;
        }

        private Employeecontract contract() {
            final var contract = new Employeecontract();
            setField(contract, "id", EMPLOYEE_CONTRACT_ID);
            contract.setEmployee(employee(OWNER));
            contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
            contract.setValidFrom(CONTRACT_START);
            contract.setReportReleaseDate(RELEASED_UNTIL);
            return contract;
        }

        private Employee employee(String sign) {
            final var salatUser = new SalatUser();
            salatUser.setLoginname(sign);
            salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var employee = new Employee();
            employee.setSalatUser(salatUser);
            employee.setSign(sign);
            employee.setFirstname("Vorname");
            employee.setLastname(sign);
            return employee;
        }
    }

    /**
     * Die Übersicht vor der Abnahme (#1122). Abgenommen ist bis Sonntag, 03.03.2024, freigegeben bis
     * Freitag, 08.03.2024: die Übersicht bis zur Freigabe zeigt also die Woche vom 04.03.2024.
     */
    @Nested
    class AcceptanceReview {

        private static final long EMPLOYEE_CONTRACT_ID = 1L;
        private static final String OWNER = "xx";
        private static final String PEOPLE_LEAD = "pl";
        private static final String OTHER_PEOPLE_LEAD = "ol";
        private static final String MANAGER = "gf";
        private static final LocalDate CONTRACT_START = LocalDate.of(2024, 1, 1);
        private static final LocalDate ACCEPTED_UNTIL = LocalDate.of(2024, 3, 3);
        private static final LocalDate MONDAY = LocalDate.of(2024, 3, 4);
        private static final LocalDate TUESDAY = MONDAY.plusDays(1);
        private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
        private static final LocalDate THURSDAY = MONDAY.plusDays(3);
        private static final LocalDate FRIDAY = MONDAY.plusDays(4);
        private static final LocalDate RELEASED_UNTIL = FRIDAY;

        @Test
        void thePeriodBeginsTheDayAfterTheLastAcceptance() {
            acceptableContract();
            givenBookings(MONDAY, FRIDAY, week());

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.period()).isEqualTo(new ReviewPeriod(MONDAY, FRIDAY));
            assertThat(review.acceptedUntil()).isEqualTo(ACCEPTED_UNTIL);
            assertThat(review.releasedUntil()).isEqualTo(RELEASED_UNTIL);
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isTrue();
        }

        @Test
        void thePeriodBeginsAtTheContractStartWithoutAcceptance() {
            final var contract = acceptableContract();
            contract.setValidFrom(WEDNESDAY);
            contract.setReportAcceptanceDate(null);
            givenBookings(WEDNESDAY, FRIDAY, week().subList(2, 5));

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.period()).isEqualTo(new ReviewPeriod(WEDNESDAY, FRIDAY));
            assertThat(review.acceptedUntil()).isNull();
        }

        /** Der Monat reicht über das Vertragsende hinaus — gezeigt wird bis zum Vertragsende, ohne Befund (#324). */
        @Test
        void thePeriodEndsAtTheContractEndWithinTheMonth() {
            final var contract = acceptableContract();
            contract.setValidUntil(WEDNESDAY);
            contract.setReportReleaseDate(WEDNESDAY);
            givenBookings(MONDAY, WEDNESDAY, week().subList(0, 3));

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 3, 31));

            assertThat(review.period()).isEqualTo(new ReviewPeriod(MONDAY, WEDNESDAY));
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isTrue();
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days).extracting(DayEntry::date)
                .containsExactly(MONDAY, TUESDAY, WEDNESDAY);
        }

        /**
         * Gelistet ist, was die Bilanz summiert: jede Buchung des Zeitraums, gleich welchen Status —
         * auch eine, die schon abgenommen ist, und eine, die noch offen ist.
         */
        @Test
        void theListsShowEveryBookingOfThePeriodWhateverItsStatus() {
            acceptableContract();
            final var closed = booking(6, MONDAY, GlobalConstants.TIMEREPORT_STATUS_CLOSED, OrderType.STANDARD, Duration.ofHours(1));
            final var open = booking(7, TUESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(1));
            final var listed = new ArrayList<>(week());
            listed.add(closed);
            listed.add(open);
            givenBookings(MONDAY, FRIDAY, listed);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.byOrder()).flatExtracting(OrderGroup::timereports).containsExactlyInAnyOrderElementsOf(listed);
            assertThat(review.timereportCount()).isEqualTo(7);
            verify(timereportDAO, atLeastOnce()).getTimereportsByDatesAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, MONDAY, FRIDAY);
        }

        /** Die Abnahme schließt alle freigegebenen Buchungen bis zu ihrem Ende ab, auch eine vor dem Zeitraum. */
        @Test
        void releasedBookingsBeforeThePeriodAreListedApart() {
            acceptableContract();
            final var stray = booking(9, ACCEPTED_UNTIL.minusDays(1), GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(2));
            final var committed = new ArrayList<>(week());
            committed.add(stray);
            givenBookings(MONDAY, FRIDAY, week(), committed);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.beforePeriod()).containsExactly(stray);
            assertThat(review.byOrder()).flatExtracting(OrderGroup::timereports).doesNotContain(stray);
            assertThat(review.timereportCount()).isEqualTo(5);
            assertThat(review.actionAllowed()).isTrue();
        }

        /** Ein Tag ohne Buchung steht als solcher da, ist aber kein Befund und bietet kein Anlegen an. */
        @Test
        void aWorkingDayWithoutAnyBookingIsShownButIsNoFinding() {
            acceptableContract();
            final var withoutWednesday = week().stream().filter(booking -> !booking.getReferenceday().equals(WEDNESDAY)).toList();
            givenBookings(MONDAY, FRIDAY, withoutWednesday);
            loggedInAs(MANAGER, true, true);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.byMonth()).flatExtracting(MonthGroup::days)
                .extracting(DayEntry::date, DayEntry::withoutBooking)
                .containsExactly(tuple(MONDAY, false), tuple(TUESDAY, false), tuple(WEDNESDAY, true), tuple(THURSDAY, false), tuple(FRIDAY, false));
            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.byMonth()).flatExtracting(MonthGroup::days).flatExtracting(DayEntry::findings).isEmpty();
            assertThat(review.canCreate()).isFalse();
            assertThat(review.actionAllowed()).isTrue();
        }

        /** Anders als bei der Freigabe zählt jede Buchung: an einem Tag mit einer Buchung fehlt keine. */
        @Test
        void aBookingOfAnyStatusMakesADayBooked() {
            acceptableContract();
            final var open = booking(3, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(8));
            final var closed = booking(4, THURSDAY, GlobalConstants.TIMEREPORT_STATUS_CLOSED, OrderType.STANDARD, Duration.ofHours(8));
            final var listed = week().stream()
                .map(booking -> booking.getReferenceday().equals(WEDNESDAY) ? open
                    : booking.getReferenceday().equals(THURSDAY) ? closed : booking)
                .toList();
            givenBookings(MONDAY, FRIDAY, listed);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.byMonth()).flatExtracting(MonthGroup::days).extracting(DayEntry::withoutBooking).containsOnly(false);
        }

        /** Arbeitszeit, Pausen und Ruhezeit hat die Freigabe geprüft; die Abnahme prüft keine Tage. */
        @Test
        void noDayIsCheckedForWorkingTime() {
            acceptableContract();
            final var longDay = booking(3, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(11));
            final var standby = booking(6, WEDNESDAY, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.BEREITSCHAFT, Duration.ofHours(14));
            final var listed = new ArrayList<>(week().stream().filter(booking -> !booking.getReferenceday().equals(WEDNESDAY)).toList());
            listed.add(longDay);
            listed.add(standby);
            givenBookings(MONDAY, FRIDAY, listed);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.periodFindings()).isEmpty();
            assertThat(review.actionAllowed()).isTrue();
            verifyNoInteractions(timereportService);
            verify(timereportDAO, never()).getOpenTimereportsByEmployeeContractIdBeforeDate(anyLong(), any());
        }

        @Test
        void aMonthAfterTheReleaseIsAPeriodFindingWithoutBookings() {
            acceptableContract();

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 3, 31));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode)
                .containsExactly(ErrorCode.RL_ACCEPTANCE_DATE_AFTER_RELEASE);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        /** Ohne Freigabe ist nichts abzunehmen — bis #1122 schrieb die Abnahme dann das Überstundenkonto über offene Buchungen fest. */
        @Test
        void aContractNeverReleasedCannotBeAccepted() {
            final var contract = acceptableContract();
            contract.setReportReleaseDate(null);
            contract.setReportAcceptanceDate(null);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.period()).isEqualTo(new ReviewPeriod(CONTRACT_START, FRIDAY));
            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode)
                .containsExactly(ErrorCode.RL_ACCEPTANCE_WITHOUT_RELEASE);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        @Test
        void theMonthOfTheLastAcceptanceIsNothingToAccept() {
            acceptableContract();

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, ACCEPTED_UNTIL);

            assertThat(review.period().isEmpty()).isTrue();
            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode)
                .containsExactly(ErrorCode.RL_NOTHING_TO_ACCEPT);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        /**
         * Die Abnahme geht für niemanden zurück, auch nicht für die Administration: bis #1122 durfte
         * sie das (#652) und ließ die Buchungen dazwischen abgenommen stehen. Zurück geht es mit
         * „Öffnen".
         */
        @Test
        void anAdminCannotMoveTheAcceptanceBackwards() {
            acceptableContract();
            lenient().when(authorizedUser.isAdmin()).thenReturn(true);
            lenient().when(authorizedUser.isManager()).thenReturn(true);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, LocalDate.of(2024, 2, 29));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode)
                .containsExactly(ErrorCode.RL_ACCEPTANCE_DATE_MOVED_BACKWARDS);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        @Test
        void aMonthBeforeTheContractIsInvalid() {
            acceptableContract();

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, CONTRACT_START.minusDays(1));

            assertThat(review.periodFindings()).extracting(ServiceFeedbackMessage::getErrorCode)
                .containsExactly(ErrorCode.RL_ACCEPTANCE_DATE_INVALID);
            assertThatTheReviewShowsNothingButItsFindings(review);
        }

        /** Seine eigenen Buchungen nimmt niemand ab, auch die Geschäftsführung nicht — die Übersicht sieht sie deshalb nicht. */
        @Test
        void theOwnContractIsRefused() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            lenient().when(authorizedUser.getEffectiveLoginSign()).thenReturn(OWNER);
            lenient().when(authorizedUser.isManager()).thenReturn(true);
            final var rule = new ReleaseAuthorization(authorizedUser, null);
            when(releaseAuthorization.isAcceptAuthorized(any(), any()))
                .thenAnswer(invocation -> rule.isAcceptAuthorized(invocation.getArgument(0), invocation.getArgument(1)));

            final var denial = catchThrowableOfType(AuthorizationException.class,
                () -> classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY));

            assertThat(denial.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_ACCEPT_NOT_ALLOWED);
            assertThat(classUnderTest.isAcceptAllowed(EMPLOYEE_CONTRACT_ID)).isFalse();
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, overtimeService);
        }

        @Test
        void theReviewRequiresTheAcceptAuthorization() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isAcceptAuthorized(contract, AccessLevel.WRITE)).thenReturn(false);

            final var denial = catchThrowableOfType(AuthorizationException.class,
                () -> classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY));

            assertThat(denial.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.RL_ACCEPT_NOT_ALLOWED);
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, overtimeService);
        }

        @Test
        void anUnknownContractIsInvalidData() {
            final var thrown = catchThrowableOfType(InvalidDataException.class,
                () -> classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY));

            assertThat(thrown.getMessages()).extracting(ServiceFeedbackMessage::getErrorCode).containsExactly(ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND);
            assertThat(classUnderTest.isAcceptAllowed(EMPLOYEE_CONTRACT_ID)).isFalse();
            verifyNoInteractions(releaseAuthorization);
        }

        @Test
        void acceptingIsAllowedWhereTheAcceptAuthorizationSaysSo() {
            acceptableContract();

            assertThat(classUnderTest.isAcceptAllowed(EMPLOYEE_CONTRACT_ID)).isTrue();
        }

        /** Freigegebene Buchungen bearbeitet die zuständige People Lead; eine noch offene nicht. */
        @Test
        void theSupervisingPeopleLeadMayEditTheReleasedBookings() {
            acceptableContract();
            final var open = booking(6, MONDAY, GlobalConstants.TIMEREPORT_STATUS_OPEN, OrderType.STANDARD, Duration.ofHours(1));
            final var listed = new ArrayList<>(week());
            listed.add(open);
            givenBookings(MONDAY, FRIDAY, listed);
            loggedInAs(PEOPLE_LEAD, false, true);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
            assertThat(review.canCreate()).isFalse();
            assertThat(review.ownContract()).isFalse();
        }

        /** Wer nur über eine Regel abnimmt, ohne zuständig zu sein, bearbeitet nichts. */
        @Test
        void aPeopleLeadWhoIsNotTheSupervisorMayEditNothing() {
            acceptableContract();
            givenBookings(MONDAY, FRIDAY, week());
            loggedInAs(OTHER_PEOPLE_LEAD, false, true);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).isEmpty();
            assertThat(review.canCreate()).isFalse();
        }

        @Test
        void theManagerMayEditTheReleasedAndTheAcceptedBookingsButCreatesNone() {
            acceptableContract();
            final var closed = booking(6, MONDAY, GlobalConstants.TIMEREPORT_STATUS_CLOSED, OrderType.STANDARD, Duration.ofHours(1));
            final var listed = new ArrayList<>(week());
            listed.add(closed);
            givenBookings(MONDAY, FRIDAY, listed);
            loggedInAs(MANAGER, true, true);

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.editableTimereportIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
            assertThat(review.canCreate()).isFalse();
        }

        @Test
        void theBalanceComesFromTheOvertimeCalculation() {
            final var contract = acceptableContract();
            contract.setDailyWorkingTime(Duration.ofHours(8));
            givenBookings(MONDAY, FRIDAY, week());
            final var balance = new OvertimeBalance(Duration.ofHours(40), Duration.ofHours(40), Duration.ZERO, Duration.ZERO);
            when(overtimeService.calculateOvertimeBalance(EMPLOYEE_CONTRACT_ID, MONDAY, FRIDAY)).thenReturn(Optional.of(balance));

            final var review = classUnderTest.reviewAcceptance(EMPLOYEE_CONTRACT_ID, FRIDAY);

            assertThat(review.balance()).isEqualTo(balance);
            assertThat(review.overtimeAccount()).isTrue();
        }

        private void assertThatTheReviewShowsNothingButItsFindings(TimereportReview review) {
            assertThat(review.balance()).isNull();
            assertThat(review.byOrder()).isEmpty();
            assertThat(review.byMonth()).isEmpty();
            assertThat(review.beforePeriod()).isEmpty();
            assertThat(review.dayFindings()).isEmpty();
            assertThat(review.timereportCount()).isZero();
            assertThat(review.canCreate()).isFalse();
            assertThat(review.actionAllowed()).isFalse();
            verifyNoInteractions(timereportDAO, workingdayDAO, publicholidayDAO, overtimeService);
        }

        /** The loads of the overview: all bookings of the period, and the released ones up to its end that the acceptance closes. */
        private void givenBookings(LocalDate begin, LocalDate end, List<TimereportDTO> listed) {
            givenBookings(begin, end, listed, listed);
        }

        private void givenBookings(LocalDate begin, LocalDate end, List<TimereportDTO> listed, List<TimereportDTO> committed) {
            when(timereportDAO.getTimereportsByDatesAndEmployeeContractId(EMPLOYEE_CONTRACT_ID, begin, end)).thenReturn(listed);
            when(timereportDAO.getCommitedTimereportsByEmployeeContractIdBeforeDate(EMPLOYEE_CONTRACT_ID, end)).thenReturn(committed);
        }

        /** Asks the real rule of {@link TimereportAuthorization} as the given person; it needs no rule of the rule engine. */
        private void loggedInAs(String sign, boolean manager, boolean peopleLead) {
            lenient().when(authorizedUser.getEffectiveLoginSign()).thenReturn(sign);
            lenient().when(authorizedUser.isManager()).thenReturn(manager);
            lenient().when(authorizedUser.isPeopleLead()).thenReturn(peopleLead);
            final var rule = new TimereportAuthorization(authorizedUser, null);
            when(timereportAuthorization.isWriteAllowed(any(), any()))
                .thenAnswer(invocation -> rule.isWriteAllowed(invocation.getArgument(0), invocation.getArgument(1)));
        }

        private Employeecontract acceptableContract() {
            final var contract = contract();
            when(employeecontractDAO.getEmployeecontractById(EMPLOYEE_CONTRACT_ID)).thenReturn(contract);
            when(releaseAuthorization.isAcceptAuthorized(contract, AccessLevel.WRITE)).thenReturn(true);
            return contract;
        }

        private Employeecontract contract() {
            final var contract = new Employeecontract();
            setField(contract, "id", EMPLOYEE_CONTRACT_ID);
            contract.setEmployee(employee(OWNER));
            contract.setSupervisors(new ArrayList<>(List.of(employee(PEOPLE_LEAD))));
            contract.setValidFrom(CONTRACT_START);
            contract.setReportReleaseDate(RELEASED_UNTIL);
            contract.setReportAcceptanceDate(ACCEPTED_UNTIL);
            return contract;
        }

        private Employee employee(String sign) {
            final var salatUser = new SalatUser();
            salatUser.setLoginname(sign);
            salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var employee = new Employee();
            employee.setSalatUser(salatUser);
            employee.setSign(sign);
            employee.setFirstname("Vorname");
            employee.setLastname(sign);
            return employee;
        }

        /** Eight released hours on each weekday, ids 1 to 5. */
        private List<TimereportDTO> week() {
            return List.of(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY).stream()
                .map(day -> booking(day.getDayOfWeek().getValue(), day, GlobalConstants.TIMEREPORT_STATUS_COMMITED, OrderType.STANDARD, Duration.ofHours(8)))
                .toList();
        }

        private TimereportDTO booking(long id, LocalDate date, String status, OrderType orderType, Duration duration) {
            final var standby = orderType == OrderType.BEREITSCHAFT;
            return TimereportDTO.builder()
                .id(id)
                .referenceday(date)
                .status(status)
                .orderType(orderType)
                .suborderId(standby ? 30L : 10L)
                .completeOrderSign(standby ? "ALPHA/09" : "ALPHA/01")
                .customerorderSign("ALPHA")
                .duration(duration)
                .build();
        }
    }
}
