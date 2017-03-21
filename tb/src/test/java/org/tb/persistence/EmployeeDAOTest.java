package org.tb.persistence;

import org.junit.Assert;
import org.junit.Test;
import org.tb.bdom.Employee;
import org.tb.persistence.utils.TestUtilsEmployee;

public class EmployeeDAOTest extends AbstractDAOTest {

	@Test
	public void testSaveAndDelete() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");
		
		employeeDAO.save(employee, employee);
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());
		
		Assert.assertTrue("the test employee could not be deleted from the database!", employeeDAO.deleteEmployeeById(employee.getId()));
	}
	
}
