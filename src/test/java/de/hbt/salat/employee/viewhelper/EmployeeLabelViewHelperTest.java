package de.hbt.salat.employee.viewhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;

/**
 * Wie eine Person und ein Vertrag in jeder Auswahl heißen (#1266): {@code Vorname Nachname | Kürzel},
 * beim Vertrag der Zeitraum als zweite Zeile.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeeLabelViewHelperTest {

  private final EmployeeLabelViewHelper viewHelper = new EmployeeLabelViewHelper();

  @Test
  void should_name_the_person_first_then_the_sign() {
    assertThat(viewHelper.label(employee("Erika", "Muster", "em"))).isEqualTo("Erika Muster | em");
  }

  @Test
  void should_name_a_record_with_name_and_sign_the_same_way() {
    assertThat(viewHelper.label("Erika Muster", "em")).isEqualTo("Erika Muster | em");
  }

  @Test
  void should_fall_back_to_whichever_part_is_there() {
    assertThat(EmployeeLabelViewHelper.of("Erika Muster", null)).isEqualTo("Erika Muster");
    assertThat(EmployeeLabelViewHelper.of("  ", "em")).isEqualTo("em");
    assertThat(EmployeeLabelViewHelper.of(null, null)).isEmpty();
  }

  @Test
  void should_name_a_contract_like_its_person() {
    assertThat(viewHelper.contractLabel(contract(LocalDate.of(2024, 1, 1), null))).isEqualTo("Erika Muster | em");
  }

  @Test
  void should_give_the_period_of_a_contract() {
    assertThat(viewHelper.contractPeriod(contract(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 6, 30))))
        .isEqualTo("01.01.2024 – 30.06.2025");
  }

  /** Ein offenes Ende ist überall {@code ∞}, nicht {@code …} und nicht leer. */
  @Test
  void should_mark_an_open_end_with_infinity() {
    assertThat(viewHelper.contractPeriod(contract(LocalDate.of(2024, 1, 1), null))).isEqualTo("01.01.2024 – ∞");
  }

  /** Die Überschrift einer Seite liest sich wie die Auswahl, aus der der Vertrag stammt. */
  @Test
  void should_title_a_contract_with_label_and_period() {
    assertThat(EmployeeLabelViewHelper.title(contract(LocalDate.of(2024, 1, 1), null)))
        .isEqualTo("Erika Muster | em (01.01.2024 – ∞)");
  }

  private static Employee employee(String firstname, String lastname, String sign) {
    var employee = new Employee();
    employee.setFirstname(firstname);
    employee.setLastname(lastname);
    employee.setSign(sign);
    return employee;
  }

  private static Employeecontract contract(LocalDate validFrom, LocalDate validUntil) {
    var contract = new Employeecontract();
    contract.setEmployee(employee("Erika", "Muster", "em"));
    contract.setValidFrom(validFrom);
    contract.setValidUntil(validUntil);
    return contract;
  }

}
