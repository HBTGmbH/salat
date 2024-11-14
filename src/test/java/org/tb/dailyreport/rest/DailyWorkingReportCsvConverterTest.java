package org.tb.dailyreport.rest;

import net.bytebuddy.asm.Advice;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tb.dailyreport.domain.Workingday;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DailyWorkingReportCsvConverterTest {

    @InjectMocks
    DailyWorkingReportCsvConverter dailyWorkingReportCsvConverter;

    private static Stream<Arguments> missingParametersForQueries() {
        return Stream.of(
                Arguments.of(
                        "",
                        List.of()
                ),
                Arguments.of(
                        """
                                date,type,startTime,breakTime,employeeorderId,orderSign,orderLabel,suborderSign,suborderLabel,workingTime,comment
                                
                                2024-11-04,WORKED,09:00,00:30,183209,111,Rumsitzen,111.01,Stuhlpolsterung,00:30,Team-Mittag
                                2024-11-04,,,,183209,111,Rumsitzen,111.01,Stuhlpolsterung,07:30,Daily
                                """,
                        List.of(
                                DailyWorkingReportData.builder()
                                        .type(Workingday.WorkingDayType.WORKED)
                                        .date(LocalDate.of(2024,11,4))
                                        .startTime(LocalTime.of(9,0))
                                        .breakDuration(LocalTime.of(0,30))
                                        .dailyReports(List.of(
                                                DailyReportData.builder()
                                                        .employeeorderId(183209)
                                                        .orderSign("111")
                                                        .orderLabel("Rumsitzen")
                                                        .suborderSign("111.01")
                                                        .suborderLabel("Stuhlpolsterung")
                                                        .hours(0)
                                                        .minutes(30)
                                                        .comment("Team-Mittag")
                                                        .build(),
                                                DailyReportData.builder()
                                                        .employeeorderId(183209)
                                                        .orderSign("111")
                                                        .orderLabel("Rumsitzen")
                                                        .suborderSign("111.01")
                                                        .suborderLabel("Stuhlpolsterung")
                                                        .hours(7)
                                                        .minutes(30)
                                                        .comment("Daily")
                                                        .build()
                                        ))
                                        .build()
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("missingParametersForQueries")
    void shouldReadFromCsv(String csv, List<DailyWorkingReportData> expected) throws IOException {
        // given
        // when
        var result = dailyWorkingReportCsvConverter.read(IOUtils.toInputStream(csv, StandardCharsets.UTF_8));

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    }

}