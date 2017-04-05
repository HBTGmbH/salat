package org.tb.persistence;

import org.junit.Assert;
import org.junit.Test;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.utils.TestUtilsEmployee;
import org.tb.persistence.utils.TestUtilsEmployeecontract;

public class EmployeecontractDAOTest extends AbstractDAOTest {

	@Test
	public void testSave() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");

		employeeDAO.save(employee, employee);
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());

		Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);
		Assert.assertNotNull("after persisting the new Employeecontract-object-id should not be null!", ec.getId());
	}

	@Test
	public void testDelete() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");

		employeeDAO.save(employee, employee);
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());

		Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);
		Assert.assertNotNull("after persisting the new Employeecontract-object-id should not be null!", ec.getId());
	
		Assert.assertTrue("the test employeecontract could not be deleted from the database!", employeecontractDAO.deleteEmployeeContractById(ec.getId()));
	}

	@Test
	public void testSaveAndNotDelete_failure() {
		Employee employee = TestUtilsEmployee.createEmployee("testy");

		employeeDAO.save(employee, employee);
		Assert.assertNotNull("after persisting the new Employee-object-id should not be null!", employee.getId());

		Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);
		Assert.assertNotNull("after persisting the new Employeecontract-object-id should not be null!", ec.getId());
	}
}
