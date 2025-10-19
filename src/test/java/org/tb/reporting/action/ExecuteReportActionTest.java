package org.tb.reporting.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.reporting.action.ExecuteReportAction.getParametersFromRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.tb.reporting.domain.ReportParameter;

class ExecuteReportActionTest {

    private static Stream<Arguments> requestParametersForQueries() {
        return Stream.of(
                Arguments.of(
                        null,
                        Map.of(),
                        List.of()
                ),
                Arguments.of(
                        "no sql",
                        Map.of(),
                        List.of()
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :this",
                        Map.of(
                                "this", "value"
                        ),
                        List.of(
                                ReportParameter.builder().name("this").value("value").type("string").build()
                        )
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :this",
                        Map.of(
                                "this", "value",
                                "wrong", "wrong"
                        ),
                        List.of(
                                ReportParameter.builder().name("this").value("value").type("string").build()
                        )
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :this",
                        Map.of(
                                "this", "number,value"
                        ),
                        List.of(
                                ReportParameter.builder().name("this").value("value").type("number").build()
                        )
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :that",
                        Map.of(
                                "this", "value",
                                "that", "date,value"
                        ),
                        List.of(
                                ReportParameter.builder().name("this").value("value").type("string").build(),
                                ReportParameter.builder().name("that").value("value").type("date").build()
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("requestParametersForQueries")
    public void shouldReturnQueryParametersFromRequest(String sql,
                                                       Map<String, String> parameters,
                                                       List<ReportParameter> expected) {
        // given
        var request = new MockHttpServletRequest();
        request.setParameters(parameters);

        // when
        var result = getParametersFromRequest(request, sql);

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static Stream<Arguments> missingParametersForQueries() {
        return Stream.of(
                Arguments.of(
                        null,
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "no sql",
                        List.of(),
                        List.of()
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :this",
                        List.of(),
                        List.of(
                                "this"
                        )
                ),
                Arguments.of(
                        "SELECT * FROM somewhere WHERE something = :this AND others = :that",
                        List.of(
                                ReportParameter.builder().name("this").value("value").type("string").build()
                        ),
                        List.of(
                                "that"
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource("missingParametersForQueries")
    public void shouldDetermineMissingQueryParameters(String sql, List<ReportParameter> parameters, List<String> expected) {
        // given
        // when
        var result = ExecuteReportAction.getMissingParameters(parameters, sql);

        // then
        assertThat(result).containsExactlyInAnyOrderElementsOf(expected);
    }
}