package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCodeException;

/**
 * Ein Konflikt der Versionsnummer wird zur {@link BusinessRuleException} {@code XX-0003} (#1237) — auch
 * dann, wenn er erst beim Commit entsteht. Das ist der Normalfall: eine geänderte Entität schreibt
 * Hibernate beim Flush, und der kommt meist erst, wenn die Methode schon zurückgekehrt ist. Der Aspekt
 * muss deshalb außen um die Transaktion liegen; das zeigt der Transaktionsmanager hier, dessen Commit
 * immer scheitert.
 */
@SpringJUnitConfig(ConcurrentModificationAspectTest.Config.class)
class ConcurrentModificationAspectTest {

  @Autowired
  private TestService service;
  @Autowired
  private TestComponent component;

  @Test
  void a_conflict_at_the_commit_becomes_a_business_rule_exception() {
    var thrown = catchThrowable(service::changeSomething);

    assertThat(thrown).isInstanceOf(BusinessRuleException.class)
        .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(((ErrorCodeException) thrown).getMessages())
        .singleElement()
        .satisfies(message -> assertThat(message.getErrorCode()).isEqualTo(XX_CONCURRENT_MODIFICATION));
  }

  /** Ein Flush mitten in der Methode, etwa vor einer Abfrage, scheitert schon dort. */
  @Test
  void a_conflict_within_the_method_becomes_a_business_rule_exception_as_well() {
    assertThat(catchThrowable(service::flushInTheMiddle)).isInstanceOf(BusinessRuleException.class)
        .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Test
  void any_other_failure_passes_unchanged() {
    assertThat(catchThrowable(service::failOtherwise)).isExactlyInstanceOf(IllegalStateException.class);
  }

  /** Übersetzt wird an den Services; was kein Service ist, behält die Ausnahme der Persistenz. */
  @Test
  void a_bean_that_is_no_service_is_left_alone() {
    assertThat(catchThrowable(component::changeSomething))
        .isExactlyInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @Configuration
  @EnableAspectJAutoProxy
  @EnableTransactionManagement
  @Import({ConcurrentModificationAspect.class, TestService.class, TestComponent.class})
  static class Config {

    @Bean
    CommitFailingTransactionManager transactionManager() {
      return new CommitFailingTransactionManager();
    }
  }

  @Service
  @Transactional
  static class TestService {

    public void changeSomething() {
    }

    public void flushInTheMiddle() {
      throw conflict();
    }

    public void failOtherwise() {
      throw new IllegalStateException("something else");
    }
  }

  @Component
  @Transactional
  static class TestComponent {

    public void changeSomething() {
    }
  }

  /** Scheitert beim Commit, wie Hibernate, wenn das UPDATE mit der Versionsnummer keine Zeile trifft. */
  static class CommitFailingTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      throw conflict();
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
    }
  }

  private static ObjectOptimisticLockingFailureException conflict() {
    return new ObjectOptimisticLockingFailureException(Object.class, 42L);
  }
}
