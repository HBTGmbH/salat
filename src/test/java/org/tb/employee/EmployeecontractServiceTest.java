package org.tb.employee;

import static java.lang.Boolean.TRUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.tb.testutils.EmployeeTestUtils.BOSS_SIGN;
import static org.tb.testutils.EmployeeTestUtils.TESTY_SIGN;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.common.SalatProperties;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.EmployeeService;
import org.tb.employee.service.EmployeecontractService;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ EmployeeService.class, EmployeeDAO.class, EmployeecontractDAO.class, EmployeecontractService.class,
		SalatProperties.class, EmployeeAuthorization.class, AuthService.class})
public class EmployeecontractServiceTest {

	@Autowired
	private EmployeeService employeeService;

	@Autowired
	private EmployeecontractService employeecontractService;

	@MockBean
	private AuthorizedUser authorizedUser;

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
		this.employeeService.createOrUpdate(supervisor);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee, supervisor);
		long id = employeecontractService.createEmployeecontract(
				ec.getEmployee().getId(),
				ec.getValidFrom(),
				ec.getValidUntil(),
				ec.getSupervisor().getId(),
				ec.getTaskDescription(),
				ec.getFreelancer(),
				TRUE == ec.getHide(),
				ec.getDailyWorkingTime(),
				ec.getVacationEntitlement(),
				Duration.ZERO,
				false
		);

		assertThat(id).isGreaterThan(0);
		var employeecontract = employeecontractService.getEmployeecontractById(id);
		assertThat(employeecontract).isNotNull();
		assertThat(employeecontract.getId()).isEqualTo(id);
		assertThat(employeecontract.getVacations()).hasSize(1);
		assertThat(employeecontract.getVacations().getFirst().getEntitlement()).isEqualTo(ec.getVacationEntitlement());
	}

}
