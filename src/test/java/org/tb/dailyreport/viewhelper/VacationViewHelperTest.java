package org.tb.dailyreport.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.tb.dailyreport.domain.VacationInfo;
import org.tb.employee.domain.Employeecontract;

/** Die Urlaubszeile des Dashboards und der Kontenuebersicht (#1175). */
class VacationViewHelperTest {

  private static final long HOUR = 60;

  /* Tage sind die Einheit, in der Urlaub gedacht wird; die Stunden stehen als Detail dahinter. */
  @Test
  void names_the_days_first_and_the_hours_behind_them() {
    var helper = helper(8, 30 * 8, 12 * 8, 0);

    assertThat(helper.getUsedVacationString()).isEqualTo("12,00 Tage (96:00)");
    assertThat(helper.getBudgetVacationString()).isEqualTo("30,00 Tage (240:00)");
  }

  @Test
  void without_daily_working_time_shows_only_the_hours() {
    var helper = helper(0, 240, 96, 0);

    assertThat(helper.getUsedVacationString()).isEqualTo("96:00");
  }

  /* Geplanter Urlaub gehoert zum Verbrauch und steht als eigener Teil des Balkens daneben. */
  @Test
  void splits_the_bar_into_taken_and_planned() {
    var helper = helper(8, 20 * 8, 10 * 8, 5 * 8);

    assertThat(helper.getUsedPercent()).isEqualTo(50);
    assertThat(helper.getTakenPercent()).isEqualTo(25);
    assertThat(helper.getPlannedPercent()).isEqualTo(25);
    assertThat(helper.hasPlannedVacation()).isTrue();
    assertThat(helper.getPlannedVacationString()).isEqualTo("5,00 Tage (40:00)");
  }

  /* Ueber dem Budget reicht der Balken bis zum Ende, nicht darueber hinaus; der geplante Teil ist
     das, was vom Budget nach dem genommenen noch bleibt. */
  @Test
  void keeps_the_bar_within_the_budget() {
    var helper = helper(8, 10 * 8, 12 * 8, 4 * 8);

    assertThat(helper.getTakenPercent()).isEqualTo(80);
    assertThat(helper.getPlannedPercent()).isEqualTo(20);
    assertThat(helper.getColorClass()).isEqualTo("danger");
  }

  @Test
  void without_planned_vacation_the_whole_bar_is_taken() {
    var helper = helper(8, 20 * 8, 10 * 8, 0);

    assertThat(helper.getTakenPercent()).isEqualTo(50);
    assertThat(helper.getPlannedPercent()).isZero();
    assertThat(helper.hasPlannedVacation()).isFalse();
    assertThat(helper.getColorClass()).isEqualTo("secondary");
  }

  @Test
  void searches_an_open_ended_vacation_two_years_ahead() {
    var open = VacationViewHelper.from(contract(8), info(80, 0, 0, null));
    var closed = VacationViewHelper.from(contract(8), info(80, 0, 0, LocalDate.parse("2027-03-31")));

    assertThat(open.getBookingsUntil()).isAfter(LocalDate.now().plusYears(1));
    assertThat(closed.getBookingsUntil()).isEqualTo(LocalDate.parse("2027-03-31"));
  }

  private static VacationViewHelper helper(long dailyHours, long budgetHours, long usedHours, long plannedHours) {
    return VacationViewHelper.from(contract(dailyHours), info(budgetHours, usedHours, plannedHours, null));
  }

  private static VacationInfo info(long budgetHours, long usedHours, long plannedHours, LocalDate validUntil) {
    return new VacationInfo("2026", Duration.ofHours(budgetHours), usedHours * HOUR, plannedHours * HOUR, 7L,
        LocalDate.parse("2026-01-01"), validUntil);
  }

  private static Employeecontract contract(long dailyHours) {
    var contract = new Employeecontract();
    contract.setDailyWorkingTime(Duration.ofHours(dailyHours));
    return contract;
  }
}
