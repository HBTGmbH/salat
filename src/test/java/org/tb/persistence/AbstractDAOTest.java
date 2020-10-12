package org.tb.persistence;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.tb.bdom.Employee;
import org.tb.bdom.Employeecontract;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations="classpath:spring-beans-cfg-devl.xml")
@TransactionConfiguration(transactionManager="txManager", defaultRollback=false)
@Transactional
public class AbstractDAOTest {
	private final static Logger LOG = LoggerFactory.getLogger(AbstractDAOTest.class);
	
	protected static final String TESTY_SIGN = "testy";

	@Autowired
	protected EmployeeDAO employeeDAO;
	
	@Autowired 
	protected EmployeecontractDAO employeecontractDAO;
	
	@Before
	public void before() {
		LOG.info("running before(): cleaning up test data {}", EmployeeDAOTest.TESTY_SIGN);
		cleanupTestyEmployee();
	}
	
	@After
	public void after() {
		LOG.info("running after(): cleaning up test data {}", EmployeeDAOTest.TESTY_SIGN);
		cleanupTestyEmployee();
	}
	
	private void cleanupTestyEmployee() {
		Employee employee = employeeDAO.getEmployeeBySign(TESTY_SIGN);
		if(employee != null) {
			cleanupTestyEmployeecontracts(employee.getId());
			
			employeeDAO.deleteEmployeeById(employee.getId());
		}
	}
	
	private void cleanupTestyEmployeecontracts(long employeeId) {
		List<Employeecontract> employeecontracts = employeecontractDAO.getEmployeeContractsByFilters(true, null, employeeId);
		
		for(Employeecontract ec : employeecontracts) {
			employeecontractDAO.deleteEmployeeContractById(ec.getId());
		}
	}

}
