package org.tb.employee.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.event.AuthorizedUserChangedEvent;
import org.tb.common.GlobalConstants;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.event.EmployeecontractChangedEvent;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthorizedUserChangedListenerTest {

  @InjectMocks
  private AuthorizedUserChangedListener classUnderTest;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private AuthorizedUser authorizedUser;
  @Mock
  private EmployeeService employeeService;
  @Mock
  private EmployeecontractService employeecontractService;

  @Test
  void publishes_employeecontract_changed_event_when_valid_contract_present() {
    Employee loginEmployee = EmployeeTestUtils.createEmployee("testy");
    setField(loginEmployee, "id", 42L);
    Employeecontract employeecontract = EmployeecontractTestUtils.createEmployeecontract(loginEmployee, null);
    setField(employeecontract, "id", 123L);

    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(42L)).thenReturn(Optional.of(employeecontract));

    classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this));

    ArgumentCaptor<EmployeecontractChangedEvent> captor = ArgumentCaptor.forClass(EmployeecontractChangedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    org.assertj.core.api.Assertions.assertThat(captor.getValue().getEmployeecontractId()).isEqualTo(123L);
  }

  @Test
  void does_not_publish_employeecontract_changed_event_when_no_contract_present() {
    Employee loginEmployee = EmployeeTestUtils.createEmployee("adm");
    setField(loginEmployee, "id", 1L);
    loginEmployee.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_ADM);

    when(employeeService.getLoginEmployee()).thenReturn(loginEmployee);
    when(employeecontractService.getCurrentContract(1L)).thenReturn(Optional.empty());

    classUnderTest.onAuthorizedUserChanged(new AuthorizedUserChangedEvent(this));

    verify(eventPublisher, never()).publishEvent(any());
  }

}
