package org.tb.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.persistence.utils.TestUtilsEmployee.TESTY_SIGN;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.tb.bdom.Employee;
import org.tb.persistence.utils.TestUtilsEmployee;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeeDAOTest {

	@Autowired
	private EmployeeDAO employeeDAO;

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
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee, employee);
		assertThat(employee.getId()).isNotNull();
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen ID
	 */
	@Test
	public void employee_gets_loaded_by_id() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Long employeeId = employee.getId();

		employee = employeeDAO.getEmployeeById(employeeId);
		assertThat(employee).isNotNull();
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen Kürzel
	 */
	@Test
	public void employee_gets_loaded_by_sign() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		employee.getId();

		employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		assertThat(employee).isNotNull();
	}

	/**
	 * Testet das Löschen eines Mitarbeiters im SALAT
	 */
	@Test
	public void employee_is_deleted_in_test_db() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Long employeeId = employee.getId();
		
		assertThat(employeeDAO.deleteEmployeeById(employeeId)).isTrue();
		assertThat(employeeDAO.getEmployeeById(employeeId)).isNull();
	}
	
}
