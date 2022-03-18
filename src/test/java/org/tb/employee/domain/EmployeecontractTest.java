package org.tb.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeecontractTest {

  @Test
  public void overlap_open_untils_should_be_true() {
    Employeecontract a = new Employeecontract();
    Employeecontract b = new Employeecontract();
    a.setValidFrom(LocalDate.of(2022, 1, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    assertThat(a.overlaps(b)).isTrue();
  }

  @Test
  public void should_overlap_time_dependent_contracts() {
    Employeecontract a = new Employeecontract();
    Employeecontract b = new Employeecontract();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2023, 5, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    assertThat(a.overlaps(b)).isTrue();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2023, 5, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    b.setValidUntil(LocalDate.of(2024, 2, 1));
    assertThat(a.overlaps(b)).isTrue();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2022, 2, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    b.setValidUntil(LocalDate.of(2024, 2, 1));
    assertThat(a.overlaps(b)).isTrue();
  }

  @Test
  public void should_not_overlap_time_independent_contracts() {
    Employeecontract a = new Employeecontract();
    Employeecontract b = new Employeecontract();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2021, 5, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    assertThat(a.overlaps(b)).isFalse();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2021, 5, 1));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    b.setValidUntil(LocalDate.of(2024, 2, 1));
    assertThat(a.overlaps(b)).isFalse();
    a.setValidFrom(LocalDate.of(2021, 1, 1));
    a.setValidUntil(LocalDate.of(2022, 1, 31));
    b.setValidFrom(LocalDate.of(2022, 2, 1));
    b.setValidUntil(LocalDate.of(2024, 2, 1));
    assertThat(a.overlaps(b)).isFalse();
  }

}
