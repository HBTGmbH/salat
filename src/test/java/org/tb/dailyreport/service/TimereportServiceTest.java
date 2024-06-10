package org.tb.dailyreport.service;

import org.apache.struts.action.ActionMessage;
import org.apache.struts.action.ActionMessages;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Workingday;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;

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
        void whenNoTimeReports_shouldNotAddError() {
            // given no timeReports in the specified timeframe
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final List<TimereportDTO> result = new ArrayList<>();
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then should not add any errors regarding breaktime
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenLessThan6HoursBooked_shouldNotAddError() {
            // given the timeReport for the day has less than 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6).minusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then should not add any errors regarding breaktime
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenExactly6HoursBooked_shouldNotAddError() {
            // given the timeReport for the day has exactly 6 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then should not add any errors regarding breaktime
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenMoreThan6HoursBookedAndNoBreak_shouldAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            ActionMessage expected = new ActionMessage("form.release.error.breaktime.six.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsShorterThan30Minutes_shouldAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(25);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            ActionMessage expected = new ActionMessage("form.release.error.breaktime.six.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void whenMoreThan6HoursBookedAndBreakIsAtLeast30Minutes_shouldNotAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenExactly9HoursBookedAnd30MinutesBreak_shouldNotAddError() {
            // given the timeReports for the day have exactly 9 hours
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(3))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(30);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then should not add any errors regarding breaktime
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenMoreThan9HoursBookedAndNoBreak_shouldAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(0);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            ActionMessage expected = new ActionMessage("form.release.error.breaktime.nine.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsShorterThan45Minutes_shouldAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(40);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            ActionMessage expected = new ActionMessage("form.release.error.breaktime.nine.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void whenMoreThan9HoursBookedAndBreakIsAtLeast45Minutes_shouldNotAddError() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(6))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(3).plusMinutes(1))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            final Workingday workingday = new Workingday();
            workingday.setBreakminutes(45);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, employeeContractId)).thenReturn(workingday);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            assertThat(errors.size("validation")).isEqualTo(0);
        }

        @Test
        void whenLessThan11HoursBetweenWorkingDays_shouldAddError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO timeReport = TimereportDTO.builder()
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
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(releaseDate, employeeContractId)).thenReturn(releaseDay);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(yesterdayDate, employeeContractId)).thenReturn(yesterday);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, releaseDate, errors);

            // then should add errors regarding resttime
            ActionMessage expected = new ActionMessage("form.release.error.resttime.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void when11HoursBetweenWorkingDays_shouldNotAddError() {
            // given there are less than 11 hours between end of working time yesterday and the beginning of working time of the next day
            final long employeeContractId = 1L;
            final LocalDate releaseDate = LocalDate.of(2024, 1, 31);
            final LocalDate yesterdayDate = LocalDate.of(2024, 1, 30);
            final TimereportDTO timeReport = TimereportDTO.builder()
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
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, releaseDate)).thenReturn(result);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(releaseDate, employeeContractId)).thenReturn(releaseDay);
            when(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(yesterdayDate, employeeContractId)).thenReturn(yesterday);

            // when validating
            classUnderTest.validateForRelease(employeeContractId, releaseDate, errors);

            // then should add errors regarding resttime
            assertThat(errors.size("validation")).isEqualTo(0);
        }
/*
        @Test
        void template() {
            // given
            final long employeeContractId = 1L;
            final LocalDate date = LocalDate.of(2024, 1, 1);
            final TimereportDTO timeReport1 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofHours(7L))
                    .build();
            final TimereportDTO timeReport2 = TimereportDTO.builder()
                    .referenceday(date)
                    .duration(Duration.ofMinutes(5L))
                    .build();
            final List<TimereportDTO> result = List.of(timeReport1, timeReport2);
            ActionMessages errors = new ActionMessages();

            when(timereportDAO.getOpenTimereportsByEmployeeContractIdBeforeDate(employeeContractId, date)).thenReturn(result);

            // when
            classUnderTest.validateForRelease(employeeContractId, date, errors);

            // then
            ActionMessage expected = new ActionMessage("break", "form.release.error.breaktime.six.length");
            assertThat(errors.size("validation")).isEqualTo(1);
            assertThat(errors.get("validation").next()).isEqualTo(expected);
        }
        */

        private Duration moreThan(long hours) {
            return Duration.ofHours(hours).plusMinutes(1);
        }
    }
}