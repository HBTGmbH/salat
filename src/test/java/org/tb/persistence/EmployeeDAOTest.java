package org.tb.persistence;

import org.junit.Assert;
import org.junit.Test;
import org.tb.bdom.Employee;
import org.tb.persistence.utils.TestUtilsEmployee;

public class EmployeeDAOTest extends AbstractDAOTest {

	/**
	 * Prüft, dass die Datenbank noch keinen Mitarbeiter "testy" enthält 
	 */
	@Test
	public void testDB_should_not_contain_employee_testy() {
		Employee employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		Assert.assertNull("There should not be an Employee with sign '" + TESTY_SIGN +"' in the database!", employee);
	}

	/**
	 * Testet das Anlegen eines Mitarbeiters im SALAT 
	 */
	@Test
	public void testSave() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen ID 
	 */
	@Test
	public void testGetEmployeeById() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Long employeeId = employee.getId();
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employeeId);

		employee = employeeDAO.getEmployeeById(employeeId);
		Assert.assertNotNull("Could not find recently created Employee-object in the database", employee);
	}

	/**
	 * Testet das laden eines Mitarbeiters im SALAT über dessen Kürzel 
	 */
	@Test
	public void testGetEmployeeBySign() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Long employeeId = employee.getId();
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employeeId);

		employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		Assert.assertNotNull("Could not find recently created Employee-object in the database", employee);
	}

	/**
	 * Testet das Löschen eines Mitarbeiters im SALAT 
	 */
	@Test
	public void testDelete() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		
		employeeDAO.save(employee, employee);
		Long employeeId = employee.getId();
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employeeId);
		
		Assert.assertTrue("the test employee could not be deleted from the database!", employeeDAO.deleteEmployeeById(employeeId));
		
		employee = employeeDAO.getEmployeeById(employeeId);
		Assert.assertNull("Recently deleted Employee-object is still in the database!", employee);
	}
	
}
