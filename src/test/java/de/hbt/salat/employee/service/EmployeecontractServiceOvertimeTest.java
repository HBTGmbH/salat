package de.hbt.salat.employee.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthorizationAspect;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Overtime;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.employee.persistence.OvertimeRepository;

/**
 * Eine Überstundenkorrektur legt nur das Management an (#1256). Bis dahin schützte allein der
 * Controller sie; der Service selbst prüfte nichts.
 *
 * <p>Seitdem verlangt die Klasse das Management, und was jede angemeldete Person aufrufen darf, sagt
 * das an der Methode — die lesenden Methoden und die Freigabedaten, die eine Person beim Freigeben
 * ihrer eigenen Buchungen schreibt. Geprüft wird deshalb durch den echten {@link AuthorizationAspect}
 * hindurch, wie es der Spring-Proxy tut.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmployeecontractServiceOvertimeTest {

  @Mock
  private OvertimeRepository overtimeRepository;
  @Mock
  private EmployeecontractDAO employeecontractDAO;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private AuthorizedUser authorizedUser;
  @InjectMocks
  private EmployeecontractService target;

  private EmployeecontractService service;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    var proxyFactory = new AspectJProxyFactory(target);
    proxyFactory.setProxyTargetClass(true);
    proxyFactory.addAspect(new AuthorizationAspect(authorizedUser));
    service = proxyFactory.getProxy();
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

  /** Lesen und die eigene Freigabe brauchen nur die Anmeldung, nicht das Management. */
  @Test
  void reading_and_the_release_data_stay_open_to_every_login() {
    when(authorizedUser.isManager()).thenReturn(false);
    var contract = overtime().getEmployeecontract();
    when(employeecontractDAO.getEmployeecontractById(7L)).thenReturn(contract);

    assertThat(service.getEmployeecontractById(7L)).isSameAs(contract);
    service.updateReportReleaseData(7L, LocalDate.of(2026, 5, 31), null);
    assertThat(contract.getReportReleaseDate()).isEqualTo(LocalDate.of(2026, 5, 31));
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
