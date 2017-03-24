package org.tb.persistence;

import org.junit.Assert;
import org.junit.Test;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.utils.TestUtilsEmployee;
import org.tb.persistence.utils.TestUtilsEmployeecontract;

public class EmployeecontractDAOTest extends AbstractDAOTest {

	@Test
	public void testSaveAndDelete() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");
		try {
			employeeDAO.save(employee, employee);
			Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());

			Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
			employeecontractDAO.save(ec, employee);
			Assert.assertNotNull("after persisting the new Employeecontract-object-id should not be null!", ec.getId());
		
			Assert.assertTrue("the test employeecontract could not be deleted from the database!", employeecontractDAO.deleteEmployeeContractById(ec.getId()));
		} finally {
			Assert.assertTrue("the test employee could not be deleted from the database!", employeeDAO.deleteEmployeeById(employee.getId()));
		}
	}

	@Test
	public void testSaveAndNotDelete_failure() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");
		try {
			employeeDAO.save(employee, employee);
			Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());

			Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
			employeecontractDAO.save(ec, employee);
			Assert.assertNotNull("after persisting the new Employeecontract-object-id should not be null!", ec.getId());
		} finally {
			Assert.assertFalse("the test employee should not be deleteable from the database!", employeeDAO.deleteEmployeeById(employee.getId()));
		}
	}
}
