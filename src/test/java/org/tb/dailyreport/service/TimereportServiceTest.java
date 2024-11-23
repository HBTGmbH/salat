package org.tb.dailyreport.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.common.ServiceFeedbackMessage;
import org.tb.common.exception.ErrorCode;
import org.tb.common.GlobalConstants;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.PublicholidayDAO;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.order.domain.OrderType;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;
import static org.tb.dailyreport.domain.Workingday.WorkingDayType.NOT_WORKED;

@ExtendWith(MockitoExtension.class)
class TimereportServiceTest {

    @InjectMocks
    private TimereportService classUnderTest;
    @Mock
    private TimereportDAO timereportDAO;
    @Mock
    private WorkingdayDAO workingdayDAO;
    @Mock
    private EmployeecontractDAO employeecontractDAO;
    @Mock
    private PublicholidayDAO publicholidayDAO;

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setValidFrom(date);
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            //when(publicholidayDAO.getPublicHolidaysBetween(any(), any())).thenReturn(List.of());

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setReportReleaseDate(LocalDate.of(2023, 12, 31));
            contract.setEmployee(employee);
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setReportReleaseDate(LocalDate.of(2024, 1, 30));
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, releaseDate);

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

            final var employee = new Employee();
            employee.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
            final var contract = new Employeecontract();
            contract.setEmployee(employee);
            contract.setReportReleaseDate(LocalDate.of(2024, 1, 30));
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, releaseDate);

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
            when(employeecontractDAO.getEmployeeContractById(employeeContractId)).thenReturn(contract);

            // when validating
            final List<ServiceFeedbackMessage> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then expect no error to be returned
            assertThat(errors).hasSize(0);
        }
    }
}