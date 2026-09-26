package org.tb.dailyreport.service;

import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.WORKED;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
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
import org.tb.common.exception.ErrorCode;
import org.tb.common.exception.ErrorCodeException;
import org.tb.common.exception.ServiceFeedbackMessage;
import org.tb.common.test.FixedClock;
import org.tb.dailyreport.auth.ReleaseAuthorization;
import org.tb.dailyreport.domain.Publicholiday;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.TimereportRepository;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
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

            // when releasing the whole month
            classUnderTest.releaseTimereports(EMPLOYEE_CONTRACT_ID, END_OF_MONTH);

            // then the contract end is what gets released and stored
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
}
