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

}
