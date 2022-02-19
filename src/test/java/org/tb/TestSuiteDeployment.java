package org.tb;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.tb.persistence.PersistenceTestSuiteDeployment;

@Suite
@SelectClasses(PersistenceTestSuiteDeployment.class)
public class TestSuiteDeployment {

}
