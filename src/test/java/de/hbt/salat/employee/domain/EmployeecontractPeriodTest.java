package de.hbt.salat.employee.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.common.LocalDateRange;

@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractPeriodTest {

  private static final LocalDate FROM = LocalDate.of(2022, 1, 1);
  private static final LocalDate UNTIL = LocalDate.of(2022, 6, 30);

  @Test
  void a_contract_inside_the_period_keeps_its_own_validity() {
    var period = EmployeecontractPeriod.of(contract(LocalDate.of(2022, 2, 1), LocalDate.of(2022, 3, 31)), FROM, UNTIL);

    assertThat(period).isEqualTo(new EmployeecontractPeriod(1L, LocalDate.of(2022, 2, 1), LocalDate.of(2022, 3, 31)));
  }

  @Test
  void a_contract_beyond_both_ends_is_cut_to_the_period() {
    var period = EmployeecontractPeriod.of(contract(LocalDate.of(2019, 4, 1), LocalDate.of(2024, 3, 31)), FROM, UNTIL);

    assertThat(period).isEqualTo(new EmployeecontractPeriod(1L, FROM, UNTIL));
  }

  @Test
  void an_open_end_is_cut_to_the_end_of_the_period() {
    var period = EmployeecontractPeriod.of(contract(LocalDate.of(2022, 4, 1), null), FROM, UNTIL);

    assertThat(period).isEqualTo(new EmployeecontractPeriod(1L, LocalDate.of(2022, 4, 1), UNTIL));
  }

  @Test
  void an_end_on_the_sentinel_is_cut_to_the_end_of_the_period() {
    var period = EmployeecontractPeriod.of(contract(LocalDate.of(2022, 4, 1), LocalDateRange.FINIT_UNTIL_BOUNDARY), FROM, UNTIL);

    assertThat(period).isEqualTo(new EmployeecontractPeriod(1L, LocalDate.of(2022, 4, 1), UNTIL));
  }

  private Employeecontract contract(LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    ReflectionTestUtils.setField(contract, "id", 1L);
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    return contract;
  }
}
