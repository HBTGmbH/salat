package org.tb.employee;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.tb.testutils.EmployeeTestUtils.BOSS_SIGN;
import static org.tb.testutils.EmployeeTestUtils.TESTY_SIGN;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.GlobalConstants;
import org.tb.common.SalatProperties;
import org.tb.common.exception.BusinessRuleException;
import org.tb.common.web.UiState;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.auth.EmployeecontractAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.notification.service.NotificationService;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ EmployeeService.class, EmployeeDAO.class, EmployeecontractDAO.class, EmployeecontractService.class,
		SalatProperties.class, EmployeeAuthorization.class, EmployeecontractAuthorization.class, AuthService.class})
public class EmployeecontractServiceTest {

	@Autowired
	private EmployeeService employeeService;

	@Autowired
	private EmployeecontractService employeecontractService;

	@MockitoBean
	private AuthorizedUser authorizedUser;

	@MockitoBean
	private UiState uiState;

	@MockitoBean
	private NotificationService notificationService;

	@BeforeEach
	public void initAuthorizedUser() {
		when(authorizedUser.isAuthenticated()).thenReturn(true);
		when(authorizedUser.isManager()).thenReturn(true);
	}

	@Test
	public void employee_contract_can_be_saved_and_creates_vacation() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		this.employeeService.createOrUpdate(employee);
		Employee supervisor = EmployeeTestUtils.createEmployee(BOSS_SIGN);
		supervisor.setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
		this.employeeService.createOrUpdate(supervisor);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee, supervisor);
		var info = employeecontractService.createEmployeecontract(
				ec.getEmployee().getId(),
				ec.getValidFrom(),
				ec.getValidUntil(),
				ec.getSupervisors().stream().map(org.tb.employee.domain.Employee::getId).toList(),
				ec.getTaskDescription(),
				ec.getFreelancer(),
				TRUE == ec.getHide(),
				ec.getDailyWorkingTime(),
				ec.getVacationEntitlement(),
				Duration.ZERO,
				false
		);

		assertThat(info.getId()).isGreaterThan(0);
		var employeecontract = employeecontractService.getEmployeecontractById(info.getId());
		assertThat(employeecontract).isNotNull();
		assertThat(employeecontract.getId()).isEqualTo(info.getId());
		assertThat(employeecontract.getVacations()).hasSize(1);
		assertThat(employeecontract.getVacations().getFirst().getEntitlement()).isEqualTo(ec.getVacationEntitlement());
	}

	@Test
	public void employee_contract_can_have_two_supervisors() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		this.employeeService.createOrUpdate(employee);

		Employee supervisor1 = EmployeeTestUtils.createEmployee(BOSS_SIGN);
		supervisor1.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
		this.employeeService.createOrUpdate(supervisor1);

		Employee supervisor2 = EmployeeTestUtils.createEmployee("lead");
		supervisor2.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
		this.employeeService.createOrUpdate(supervisor2);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee, supervisor1);
		var info = employeecontractService.createEmployeecontract(
				ec.getEmployee().getId(),
				ec.getValidFrom(),
				ec.getValidUntil(),
				List.of(supervisor1.getId(), supervisor2.getId()),
				ec.getTaskDescription(),
				ec.getFreelancer(),
				TRUE == ec.getHide(),
				ec.getDailyWorkingTime(),
				ec.getVacationEntitlement(),
				Duration.ZERO,
				false
		);

		var saved = employeecontractService.getEmployeecontractById(info.getId());
		assertThat(saved.getSupervisors()).hasSize(2);
		assertThat(saved.getSupervisors()).extracting(Employee::getSign)
				.containsExactlyInAnyOrder(BOSS_SIGN, "lead");
	}

	@Test
	public void employee_contract_requires_at_least_one_supervisor() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		this.employeeService.createOrUpdate(employee);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee, null);

		assertThatThrownBy(() -> employeecontractService.createEmployeecontract(
				ec.getEmployee().getId(),
				ec.getValidFrom(),
				ec.getValidUntil(),
				List.of(),
				ec.getTaskDescription(),
				ec.getFreelancer(),
				FALSE == ec.getHide(),
				ec.getDailyWorkingTime(),
				ec.getVacationEntitlement(),
				Duration.ZERO,
				false
		)).isInstanceOf(BusinessRuleException.class);
	}

	@Test
	public void employee_contract_rejects_self_as_supervisor() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		employee.getSalatUser().setStatus(GlobalConstants.EMPLOYEE_STATUS_PV);
		this.employeeService.createOrUpdate(employee);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee, employee);

		assertThatThrownBy(() -> employeecontractService.createEmployeecontract(
				ec.getEmployee().getId(),
				ec.getValidFrom(),
				ec.getValidUntil(),
				List.of(employee.getId()),
				ec.getTaskDescription(),
				ec.getFreelancer(),
				FALSE == ec.getHide(),
				ec.getDailyWorkingTime(),
				ec.getVacationEntitlement(),
				Duration.ZERO,
				false
		)).isInstanceOf(BusinessRuleException.class);
	}

}
