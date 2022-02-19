package org.tb.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.persistence.utils.TestUtilsEmployee.TESTY_SIGN;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
import org.tb.persistence.utils.TestUtilsEmployee;
import org.tb.persistence.utils.TestUtilsEmployeecontract;

@DataJpaTest
@DisplayNameGeneration(ReplaceUnderscores.class)
public class EmployeecontractDAOTest {

	@Autowired
	private EmployeeDAO employeeDAO;

	@Autowired
	private EmployeecontractDAO employeecontractDAO;

	@Test
	public void employee_contract_can_be_saved_and_gets_id() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee, employee);

		Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);

		assertThat(ec.getId()).isNotNull();
	}

	@Test
	public void employee_contract_can_be_deleted() {
		Employee employee = TestUtilsEmployee.createEmployee(TESTY_SIGN);
		employeeDAO.save(employee, employee);

		Employeecontract ec = TestUtilsEmployeecontract.createEmployeecontract(employee);
		employeecontractDAO.save(ec, employee);
	
		assertThat(employeecontractDAO.deleteEmployeeContractById(ec.getId())).isTrue();
		assertThat(employeecontractDAO.getEmployeeContractById(ec.getId())).isNull();
	}

}
