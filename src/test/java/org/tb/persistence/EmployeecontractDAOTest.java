package org.tb.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tb.testutils.EmployeeTestUtils.TESTY_SIGN;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;
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
