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
import org.tb.auth.AuthService;
import org.tb.auth.AuthorizedUser;
import org.tb.common.AuthorizedUserAuditorAware;
import org.tb.common.SalatProperties;
import org.tb.dailyreport.persistence.TimereportDAO;
import org.tb.dailyreport.persistence.VacationDAO;
import org.tb.dailyreport.persistence.WorkingdayDAO;
import org.tb.employee.domain.Employee;
import org.tb.employee.persistence.EmployeeDAO;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.persistence.OvertimeDAO;
import org.tb.testutils.EmployeeTestUtils;

@DataJpaTest
@Import({
		AuthorizedUserAuditorAware.class,
		AuthorizedUser.class,
		EmployeeDAO.class,
		EmployeecontractDAO.class,
		VacationDAO.class,
		OvertimeDAO.class,
		TimereportDAO.class,
		WorkingdayDAO.class,
		AuthService.class,
		SalatProperties.class
})
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeDAOTest {

	@Autowired
	private EmployeeDAO employeeDAO;

	@MockBean
	private AuthorizedUser authorizedUser;

	@BeforeEach
	public void initAuthorizedUser() {
		when(authorizedUser.isAuthenticated()).thenReturn(true);
		when(authorizedUser.isManager()).thenReturn(true);
		when(authorizedUser.getSign()).thenReturn("test");
	}

	/**
	 * Prüft, dass die Datenbank noch keinen Mitarbeiter "testy" enthält
	 */
	@Test
	public void test_db_should_not_contain_employee_testy() {
		Employee employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		assertThat(employee).isNull();
	}

	/**
	 * Testet das Anlegen eines Mitarbeiters im SALAT 
	 */
	@Test
	public void new_employee_has_id_set() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee);
		assertThat(employee.getId()).isNotNull();
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen ID
	 */
	@Test
	public void employee_gets_loaded_by_id() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee);
		Long employeeId = employee.getId();

		employee = employeeDAO.getEmployeeById(employeeId);
		assertThat(employee).isNotNull();
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen Kürzel
	 */
	@Test
	public void employee_gets_loaded_by_sign() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee);

		employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		assertThat(employee).isNotNull();
	}

	/**
	 * Testet das Löschen eines Mitarbeiters im SALAT
	 */
	@Test
	public void employee_is_deleted_in_test_db() {
		Employee employee = EmployeeTestUtils.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee);
		Long employeeId = employee.getId();
		
		assertThat(employeeDAO.deleteEmployeeById(employeeId)).isTrue();
		assertThat(employeeDAO.getEmployeeById(employeeId)).isNull();
	}
	
}
