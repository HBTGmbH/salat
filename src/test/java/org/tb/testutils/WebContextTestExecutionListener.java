package org.tb.testutils;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

public class WebContextTestExecutionListener extends AbstractTestExecutionListener {

  @Override
  public void prepareTestInstance(TestContext testContext) {
    if (testContext.getApplicationContext() instanceof GenericApplicationContext) {
      GenericApplicationContext context = (GenericApplicationContext) testContext.getApplicationContext();
      ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
      Scope requestScope = new SimpleThreadScope();
      beanFactory.registerScope("request", requestScope);
      Scope sessionScope = new SimpleThreadScope();
      beanFactory.registerScope("session", sessionScope);
      Scope threadScope = new SimpleThreadScope();
      beanFactory.registerScope("thread", threadScope);
    }
  }

}