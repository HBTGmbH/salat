package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;

/**
 * {@link EmployeecontractService#hasReleasedSuccessor} closes an ended contract for its person once a later contract
 * has been released (#1215). Day view and matrix ask it per booking and per day, so a running contract never reaches
 * the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReleasedSuccessorTest {

  private static final long EMPLOYEE_ID = 7L;
  private static final LocalDate ENDED = LocalDate.of(2014, 12, 31);

  @Mock
  private EmployeecontractRepository employeecontractRepository;

  @InjectMocks
  private EmployeecontractService service;

  @Test
  void an_ended_contract_with_a_released_later_contract_has_a_released_successor() {
    when(employeecontractRepository.existsReleasedContractAfter(EMPLOYEE_ID, ENDED)).thenReturn(true);

    assertThat(service.hasReleasedSuccessor(contractUntil(ENDED))).isTrue();
  }

  @Test
  void an_ended_contract_without_a_released_later_contract_has_none() {
    when(employeecontractRepository.existsReleasedContractAfter(EMPLOYEE_ID, ENDED)).thenReturn(false);

    assertThat(service.hasReleasedSuccessor(contractUntil(ENDED))).isFalse();
  }

  @Test
  void a_contract_without_an_end_has_none_and_is_not_looked_up() {
    assertThat(service.hasReleasedSuccessor(contractUntil(null))).isFalse();
    verifyNoInteractions(employeecontractRepository);
  }

  @Test
  void a_running_fixed_term_contract_has_none_and_is_not_looked_up() {
    assertThat(service.hasReleasedSuccessor(contractUntil(LocalDate.of(2999, 12, 31)))).isFalse();
    verifyNoInteractions(employeecontractRepository);
  }

  private static Employeecontract contractUntil(LocalDate validUntil) {
    var employee = new Employee();
    ReflectionTestUtils.setField(employee, "id", EMPLOYEE_ID);
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(LocalDate.of(2014, 1, 1));
    contract.setValidUntil(validUntil);
    return contract;
  }
}
