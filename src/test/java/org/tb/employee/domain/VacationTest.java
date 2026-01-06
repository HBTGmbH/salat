package org.tb.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class VacationTest {

  @Test
  public void should_deal_with_employees_starting_mid_year() {
    Employeecontract contract = new Employeecontract();
    contract.setValidFrom(LocalDate.of(2021, 7, 1));
    contract.setDailyWorkingTime(Duration.ofHours(8));

    Vacation vacation = new Vacation();
    vacation.setEmployeecontract(contract);
    vacation.setYear(2021);
    vacation.setUsed(0);
    vacation.setEntitlement(30);

    assertThat(vacation.getEffectiveEntitlement()).isEqualTo(Duration.ofHours(8 * 15));
  }

  @Test
  public void should_deal_with_employees_starting_mid_month() {
    Employeecontract contract = new Employeecontract();
    contract.setValidFrom(LocalDate.of(2021, 6, 16));
    contract.setDailyWorkingTime(Duration.ofHours(8));

    Vacation vacation = new Vacation();
    vacation.setEmployeecontract(contract);
    vacation.setYear(2021);
    vacation.setUsed(0);
    vacation.setEntitlement(30);

    assertThat(vacation.getEffectiveEntitlement()).isEqualTo(Duration.ofHours(8 * 15).plusHours(10));
  }

  @Test
  public void should_deal_with_employee_contract_prolong() {
    Employeecontract contract = new Employeecontract();
    contract.setValidFrom(LocalDate.of(2021, 6, 16));
    contract.setValidUntil(LocalDate.of(2026, 3, 31));
    contract.setDailyWorkingTime(Duration.ofHours(8));

    Vacation vacation1 = new Vacation();
    vacation1.setEmployeecontract(contract);
    vacation1.setYear(2025);
    vacation1.setUsed(0);
    vacation1.setEntitlement(30);
    Vacation vacation2 = new Vacation();
    vacation2.setEmployeecontract(contract);
    vacation2.setYear(2026);
    vacation2.setUsed(0);
    vacation2.setEntitlement(30);

    assertThat(vacation1.getEffectiveEntitlement()).isEqualTo(Duration.ofHours(8 * 30));
    assertThat(vacation2.getEffectiveEntitlement()).isEqualTo(Duration.ofHours(8 * 30 / 4));
  }

}
