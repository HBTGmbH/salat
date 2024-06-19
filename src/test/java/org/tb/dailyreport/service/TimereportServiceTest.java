package org.tb.dailyreport.service;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.WorkingDayValidationError;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.order.domain.OrderType;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimereportServiceTest {

    @InjectMocks
    private TimereportService classUnderTest;
    @Mock
    private TimereportDAO timereportDAO;
    @Mock
    private WorkingdayDAO workingdayDAO;


    @Nested
    class ValidateForRelease {
        @Test
        void whenNoWorkingDayWasSaved_shouldReturnError() {
            // given the Workingday object does not exist
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).minusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(null);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should return error about missing begin of working time
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getDate()).isEqualTo(date);
            assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.beginofworkingday.required");
        }

        @Test
        void whenNoTimeReports_shouldNotReturnError() {
            // given no timeReports in the specified timeframe
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final List<TimereportDTO> result = new ArrayList<>();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenLessThan6HoursBooked_shouldNotReturnError() {
            // given the timeReport for the day has less than 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).minusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenExactly6HoursBooked_shouldNotReturnError() {
            // given the timeReport for the day has exactly 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should not return any errors
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenMoreThan6HoursBookedAndNoBreak_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            assertThat(errors.getFirst().getDate()).isEqualTo(date);
            assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.breaktime.six.length");
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsShorterThan30Minutes_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(25);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            AssertionsForClassTypes.assertThat(errors.getFirst().getDate()).isEqualTo(date);
            AssertionsForClassTypes.assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.breaktime.six.length");
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsAtLeast30Minutes_shouldNotReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should not return any error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenExactly9HoursBookedAnd30MinutesBreak_shouldNotReturnError() {
            // given the timeReports for the day have exactly 9 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(3))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should not return any error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenMoreThan9HoursBookedAndNoBreak_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            AssertionsForClassTypes.assertThat(errors.getFirst().getDate()).isEqualTo(date);
            AssertionsForClassTypes.assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.breaktime.nine.length");
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsShorterThan45Minutes_shouldReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(40);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then should return an error regarding breaktime
            assertThat(errors).hasSize(1);
            AssertionsForClassTypes.assertThat(errors.getFirst().getDate()).isEqualTo(date);
            AssertionsForClassTypes.assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.breaktime.nine.length");
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsAtLeast45Minutes_shouldNotReturnError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(45);
            workingday.setStarttimehour(8);
            workingday.setRefday(date);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then expect no error
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenLessThan11HoursBetweenWorkingDays_shouldReturnError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
                    .referenceday(releaseDate)
                    .duration(Duration.ofHours(6))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday releaseDay = new Workingday();
            releaseDay.setStarttimehour(6);
            releaseDay.setRefday(releaseDate);
            final Workingday yesterday = new Workingday();
            yesterday.setStarttimehour(19);
            yesterday.setStarttimeminute(1);
            yesterday.setRefday(yesterdayDate);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(releaseDate, employeeContractId)).thenReturn(releaseDay);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(yesterdayDate, employeeContractId)).thenReturn(yesterday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, releaseDate);

            // then expect an error regarding resttime
            assertThat(errors).hasSize(1);
            AssertionsForClassTypes.assertThat(errors.getFirst().getDate()).isEqualTo(releaseDate);
            AssertionsForClassTypes.assertThat(errors.getFirst().getMessage()).isEqualTo("form.release.error.resttime.length");
        }

        @Test
        void when11HoursBetweenWorkingDays_shouldNotReturnError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.KUNDE)
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
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(releaseDate, employeeContractId)).thenReturn(releaseDay);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(yesterdayDate, employeeContractId)).thenReturn(yesterday);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, releaseDate);

            // then expect no error to be returned
            assertThat(errors).hasSize(0);
        }

        @Test
        void whenTimeReportIsForSuborderThatIsIrrelevantForWorkingDayContingent_shouldNotReturnError() {
            // given someone has booked 8 hours of vacation
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .orderType(OrderType.URLAUB)
                    .referenceday(date)
                    .duration(Duration.ofHours(8))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);

            // when validating
            final List<WorkingDayValidationError> errors = classUnderTest.validateForRelease(employeeContractId, date);

            // then expect no error to be returned
            assertThat(errors).hasSize(0);
        }
    }
}