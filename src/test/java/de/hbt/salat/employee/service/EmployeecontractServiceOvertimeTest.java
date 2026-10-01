package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Overtime;
import de.hbt.salat.employee.persistence.OvertimeRepository;

/**
 * Eine Überstundenkorrektur legt nur das Management an (#1256). Bis dahin schützte allein der
 * Controller sie; der Service selbst prüfte nichts, und jeder weitere Aufrufer hätte Überstundenstände
 * ohne Berechtigung ändern können.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractServiceOvertimeTest {

  @Mock
  private OvertimeRepository overtimeRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private AuthorizedUser authorizedUser;
  @InjectMocks
  private EmployeecontractService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void without_the_management_role_nothing_is_stored() {
    when(authorizedUser.isManager()).thenReturn(false);

    assertThatThrownBy(() -> service.create(overtime()))
        .isInstanceOf(AuthorizationException.class)
        .hasMessageContaining(ErrorCode.AA_NEEDS_MANAGER.getCode());
    verify(overtimeRepository, never()).save(any());
  }

  @Test
  void the_management_stores_it_and_gets_its_id() {
    when(authorizedUser.isManager()).thenReturn(true);
    var overtime = overtime();
    when(overtimeRepository.save(overtime)).thenAnswer(invocation -> {
      ReflectionTestUtils.setField(overtime, "id", 42L);
      return overtime;
    });

    assertThat(service.create(overtime)).isEqualTo(42L);
    verify(overtimeRepository).save(overtime);
  }

  private static Overtime overtime() {
    var employee = new Employee();
    employee.setSign("em");
    var contract = new Employeecontract();
    contract.setEmployee(employee);
    var overtime = new Overtime();
    overtime.setEmployeecontract(contract);
    overtime.setTime(Duration.ofHours(2));
    overtime.setComment("Korrektur");
    return overtime;
  }
}
