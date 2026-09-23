package org.tb.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.tb.common.test.FixedClock;

/**
 * Freigabe und Abnahme müssen bis zum Ende des Vormonats reichen — bei einem beendeten Vertrag
 * aber nur bis zu seinem letzten Tag (#324).
 */
@FixedClock("2026-06-25T10:15:30")
class EmployeecontractWarningTest {

    private static final LocalDate END_OF_PREVIOUS_MONTH = LocalDate.of(2026, 5, 31);

    @Test
    void whenTheEndedContractIsCompletelyProcessed_thenNoWarning() {
        // given a contract that ended in March and is released and accepted until that day
        var contractEnd = LocalDate.of(2026, 3, 31);
        var contract = contractEndingAt(contractEnd);
        contract.setReportReleaseDate(contractEnd);
        contract.setReportAcceptanceDate(contractEnd);

        // then nothing is overdue, although the contract end is months before the previous month
        assertThat(contract.getReleaseWarning()).isFalse();
        assertThat(contract.getAcceptanceWarning()).isFalse();
    }

    @Test
    void whenTheEndedContractStillHasOpenDays_thenWarning() {
        // given a contract that ended in March but is processed only until February
        var contract = contractEndingAt(LocalDate.of(2026, 3, 31));
        contract.setReportReleaseDate(LocalDate.of(2026, 2, 28));
        contract.setReportAcceptanceDate(LocalDate.of(2026, 2, 28));

        // then both are overdue
        assertThat(contract.getReleaseWarning()).isTrue();
        assertThat(contract.getAcceptanceWarning()).isTrue();
    }

    @Test
    void whenTheContractRunsOn_thenTheEndOfThePreviousMonthIsTheYardstick() {
        // given a running contract processed until the end of the previous month
        var contract = contractEndingAt(null);
        contract.setReportReleaseDate(END_OF_PREVIOUS_MONTH);
        contract.setReportAcceptanceDate(END_OF_PREVIOUS_MONTH);
        assertThat(contract.getReleaseWarning()).isFalse();
        assertThat(contract.getAcceptanceWarning()).isFalse();

        // and one day short of it
        contract.setReportReleaseDate(END_OF_PREVIOUS_MONTH.minusDays(1));
        contract.setReportAcceptanceDate(END_OF_PREVIOUS_MONTH.minusDays(1));
        assertThat(contract.getReleaseWarning()).isTrue();
        assertThat(contract.getAcceptanceWarning()).isTrue();
    }

    private Employeecontract contractEndingAt(LocalDate validUntil) {
        var contract = new Employeecontract();
        contract.setValidFrom(LocalDate.of(2024, 1, 1));
        contract.setValidUntil(validUntil);
        return contract;
    }
}
