package de.hbt.salat.dailyreport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_CLOSED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static de.hbt.salat.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * In welchem Zeitraum ein Tag liegt, und wie weit Abnahme und Freigabe in einen Monat reichen (#1164). Die Grenzen
 * sind einschließlich.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReportPeriodTest {

  private static final YearMonth MAY = YearMonth.of(2026, 5);

  @Test
  void a_day_up_to_the_acceptance_date_is_accepted_up_to_the_release_date_released_after_it_open() {
    var contract = contract(MAY.atDay(8), MAY.atDay(15));

    assertThat(ReportPeriod.statusOn(contract, MAY.atDay(8))).isEqualTo(TIMEREPORT_STATUS_CLOSED);
    assertThat(ReportPeriod.statusOn(contract, MAY.atDay(9))).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(ReportPeriod.statusOn(contract, MAY.atDay(15))).isEqualTo(TIMEREPORT_STATUS_COMMITED);
    assertThat(ReportPeriod.statusOn(contract, MAY.atDay(16))).isEqualTo(TIMEREPORT_STATUS_OPEN);
  }

  @Test
  void a_contract_never_released_is_open_on_every_day() {
    assertThat(ReportPeriod.statusOn(contract(null, null), MAY.atDay(1))).isEqualTo(TIMEREPORT_STATUS_OPEN);
  }

  @Test
  void a_month_accepted_in_part_and_released_in_part_names_both_last_days() {
    var month = ReportPeriod.Month.of(contract(MAY.atDay(8), MAY.atDay(15)), MAY);

    assertThat(month.acceptedUntil()).isEqualTo(MAY.atDay(8));
    assertThat(month.releasedUntil()).isEqualTo(MAY.atDay(15));
    assertThat(month.acceptedWholeMonth()).isFalse();
    assertThat(month.releasedWholeMonth()).isFalse();
  }

  @Test
  void a_release_beyond_the_month_covers_the_whole_month() {
    var month = ReportPeriod.Month.of(contract(null, LocalDate.of(2026, 7, 31)), MAY);

    assertThat(month.acceptedUntil()).isNull();
    assertThat(month.releasedUntil()).isEqualTo(MAY.atEndOfMonth());
    assertThat(month.releasedWholeMonth()).isTrue();
  }

  /** Was abgenommen ist, ist auch freigegeben; die Überschrift sagt dann nur „abgenommen". */
  @Test
  void a_month_accepted_as_far_as_it_is_released_shows_only_the_acceptance() {
    var month = ReportPeriod.Month.of(contract(LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 30)), MAY);

    assertThat(month.acceptedWholeMonth()).isTrue();
    assertThat(month.releasedUntil()).isNull();
  }

  @Test
  void a_release_ending_before_the_month_does_not_reach_it() {
    var month = ReportPeriod.Month.of(contract(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), MAY);

    assertThat(month.acceptedUntil()).isNull();
    assertThat(month.releasedUntil()).isNull();
  }

  private static Employeecontract contract(LocalDate acceptedUntil, LocalDate releasedUntil) {
    var contract = new Employeecontract();
    contract.setReportAcceptanceDate(acceptedUntil);
    contract.setReportReleaseDate(releasedUntil);
    return contract;
  }
}
