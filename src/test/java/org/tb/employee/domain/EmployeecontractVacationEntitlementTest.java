package org.tb.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * Der anteilige Urlaubsanspruch eines Jahres. Die Fälle stammen aus dem früheren
 * {@code VacationTest} und halten fest, dass die Rechnung beim Auflösen der Entity
 * {@code Vacation} dieselbe geblieben ist (#1077).
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeecontractVacationEntitlementTest {

  @Test
  public void should_deal_with_employees_starting_mid_year() {
    var contract = contract(LocalDate.of(2021, 7, 1), null);

    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2021)))
        .isEqualTo(Duration.ofHours(8 * 15));
  }

  @Test
  public void should_deal_with_employees_starting_mid_month() {
    var contract = contract(LocalDate.of(2021, 6, 16), null);

    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2021)))
        .isEqualTo(Duration.ofHours(8 * 15).plusHours(10));
  }

  @Test
  public void should_deal_with_employee_contract_prolong() {
    var contract = contract(LocalDate.of(2021, 6, 16), LocalDate.of(2026, 3, 31));

    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2025)))
        .isEqualTo(Duration.ofHours(8 * 30));
    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2026)))
        .isEqualTo(Duration.ofHours(8 * 30 / 4));
  }

  /**
   * Ein angefangener letzter Monat zählt zu dem Anteil, den er an seinen Tagen hat: Januar bis
   * März voll (3/12 von 30 Tagen = 60 Stunden), dazu der halbe April (1/12 × 1/2 = 10 Stunden).
   */
  @Test
  public void should_deal_with_a_contract_ending_mid_month() {
    var contract = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 15));

    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2026)))
        .isEqualTo(Duration.ofHours(70));
  }

  @Test
  public void a_year_outside_the_contract_yields_nothing() {
    var contract = contract(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2025))).isEqualTo(Duration.ZERO);
    assertThat(contract.getEffectiveVacationEntitlement(Year.of(2027))).isEqualTo(Duration.ZERO);
  }

  private Employeecontract contract(LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setVacationEntitlement(30);
    return contract;
  }

}
