package org.tb.persistence;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({ EmployeeDAOTest.class, EmployeecontractDAOTest.class })
public class PersistenceTestSuiteDeployment {

}
