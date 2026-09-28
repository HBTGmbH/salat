package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static de.hbt.salat.auth.domain.AccessLevel.READ;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import de.hbt.salat.employee.auth.EmployeecontractAuthorization;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;

/**
 * {@link EmployeecontractService#getReadableEmployeecontract(long)} answers "not readable" with an
 * empty result instead of an exception (#1157): a caught exception out of the service would still
 * roll back the caller's transaction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
class ReadableEmployeecontractTest {

  private static final long ID = 42L;

  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private EmployeecontractAuthorization employeecontractAuthorization;

  @InjectMocks
  private EmployeecontractService service;

  @Test
  void gives_a_contract_the_user_may_read() {
    var contract = new Employeecontract();
    when(employeecontractDAO.getEmployeecontractById(ID)).thenReturn(contract);
    when(employeecontractAuthorization.isAuthorized(contract, READ)).thenReturn(true);

    assertThat(service.getReadableEmployeecontract(ID)).containsSame(contract);
  }

  @Test
  void gives_nothing_for_a_contract_the_user_may_not_read() {
    var contract = new Employeecontract();
    when(employeecontractDAO.getEmployeecontractById(ID)).thenReturn(contract);
    when(employeecontractAuthorization.isAuthorized(contract, READ)).thenReturn(false);

    assertThat(service.getReadableEmployeecontract(ID)).isEmpty();
  }

  @Test
  void gives_nothing_for_a_contract_that_does_not_exist() {
    when(employeecontractDAO.getEmployeecontractById(ID)).thenReturn(null);

    assertThat(service.getReadableEmployeecontract(ID)).isEmpty();
    verifyNoInteractions(employeecontractAuthorization);
  }
}
