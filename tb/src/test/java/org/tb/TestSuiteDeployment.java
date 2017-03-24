package org.tb;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.tb.persistence.PersistenceTestSuiteDeployment;

@RunWith(Suite.class)
@Suite.SuiteClasses({
	PersistenceTestSuiteDeployment.class
})
public class TestSuiteDeployment {

}
