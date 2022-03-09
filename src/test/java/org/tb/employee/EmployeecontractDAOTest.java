package org.tb.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.tb.testutils.EmployeeTestUtils.TESTY_SIGN;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.tb.auth.AuthorizedUser;
import org.tb.dailyreport.VacationDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ EmployeeDAO.class, EmployeecontractDAO.class, VacationDAO.class, OvertimeDAO.class })
public class EmployeecontractDAOTest {

	@Autowired
	private EmployeeDAO employeeDAO;

	@Autowired
	private EmployeecontractDAO employeecontractDAO;

	@MockBean
	private AuthorizedUser authorizedUser;

	@BeforeEach
	public void initAuthorizedUser() {
		when(authorizedUser.isAuthenticated()).thenReturn(false);
	}

	@Test
	public void employee_contract_can_be_saved_and_gets_id() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee, employee);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);

		assertThat(ec.getId()).isNotNull();
	}

	@Test
	public void employee_contract_can_be_deleted() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee, employee);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);
	
		assertThat(employeecontractDAO.deleteEmployeeContractById(ec.getId())).isTrue();
		assertThat(employeecontractDAO.getEmployeeContractById(ec.getId())).isNull();
	}

}
