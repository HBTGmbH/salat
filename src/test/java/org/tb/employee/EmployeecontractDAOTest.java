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
import org.tb.auth.service.AuthService;
import org.tb.common.SalatProperties;
import org.tb.dailyreport.auth.TimereportAuthorization;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.auth.EmployeeAuthorization;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.employee.persistence.VacationDAO;
import org.tb.testutils.EmployeeTestUtils;
import org.tb.testutils.EmployeecontractTestUtils;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
@Import({ EmployeeDAO.class, EmployeecontractDAO.class, VacationDAO.class, OvertimeDAO.class, TimereportDAO.class,
		WorkingdayDAO.class, AuthService.class, SalatProperties.class, EmployeeAuthorization.class, TimereportAuthorization.class,
		AuthService.class})
public class EmployeecontractDAOTest {

	@Autowired
	private EmployeeDAO employeeDAO;

	@Autowired
	private EmployeecontractDAO employeecontractDAO;

	@MockBean
	private AuthorizedUser authorizedUser;

	@BeforeEach
	public void initAuthorizedUser() {
		when(authorizedUser.isAuthenticated()).thenReturn(true);
		when(authorizedUser.isManager()).thenReturn(true);
	}

	@Test
	public void employee_contract_can_be_saved_and_gets_id() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee);

		Employeecontract ec = EmployeecontractTestUtils.createEmployeecontract(employee);
		employeecontractDAO.save(ec);

		assertThat(ec.getId()).isNotNull();
	}

}
