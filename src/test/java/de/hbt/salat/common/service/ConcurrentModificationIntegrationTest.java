package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW;
import static org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes;
import static org.springframework.web.context.request.RequestContextHolder.setRequestAttributes;
import static de.hbt.salat.common.exception.ErrorCode.XX_CONCURRENT_MODIFICATION;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.auth.persistence.SalatUserRepository;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.common.scheduling.SchedulerRequestAttributes;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeeRepository;
import de.hbt.salat.employee.persistence.EmployeecontractRepository;

/**
 * Zwei Anfragen ändern denselben Vertrag (#1237), im echten Anwendungskontext: mit Hibernate, der
 * Versionsnummer aus {@code AuditedEntity} und der Reihenfolge, in der die Anwendung Aspekt und
 * Transaktion um einen Service legt.
 *
 * <p>Nachgestellt wird die Lage der zweiten Anfrage eines doppelten Klicks: sie hat den Vertrag
 * gelesen, und bevor sie schreibt, hat die erste ihn schon geändert und festgeschrieben. Die erste
 * läuft dafür in einer eigenen Transaktion mitten in der zweiten. Gemerkt wird der Konflikt erst beim
 * Commit — dort, wo ihn kein {@code catch} im Service sehen kann.
 *
 * <p>Eigene H2-Datenbank, wie {@code WorkingdayConcurrentCreationTest}: der zusätzliche Service macht
 * einen eigenen Anwendungskontext, und der legte mit {@code ddl-auto: create} die gemeinsame neu an.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:salat-1237;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false;MODE=MySQL;NON_KEYWORDS=YEAR"
})
@DisplayNameGeneration(ReplaceUnderscores.class)
class ConcurrentModificationIntegrationTest {

  private static final String SIGN = "c37";

  @Autowired
  private ContractEditor editor;
  @Autowired
  private EmployeeRepository employeeRepository;
  @Autowired
  private EmployeecontractRepository employeecontractRepository;
  @Autowired
  private SalatUserRepository salatUserRepository;
  @Autowired
  private AuthorizedUser authorizedUser;

  private long contractId;

  @BeforeEach
  void seedContractAndAuthorizeAsJob() {
    // ADR-0006: ohne laufende Anfrage gibt es keine request-scoped Bohne; der Job-Modus ist der Weg
    setRequestAttributes(new SchedulerRequestAttributes());
    authorizedUser.initForJob();
    contractId = employeeRepository.findBySign(SIGN)
        .map(employee -> employeecontractRepository.findAllByEmployeeId(employee.getId()).getFirst())
        .orElseGet(this::createContract)
        .getId();
  }

  @AfterEach
  void unbind() {
    resetRequestAttributes();
  }

  @Test
  void the_request_that_writes_second_gets_a_business_rule_exception_instead_of_a_persistence_failure() {
    var thrown = catchThrowable(() -> editor.releaseAfterAnotherRequestDid(contractId, LocalDate.of(2026, 8, 31)));

    assertThat(thrown).isInstanceOf(BusinessRuleException.class)
        .hasCauseInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(((ErrorCodeException) thrown).getMessages())
        .singleElement()
        .satisfies(message -> assertThat(message.getErrorCode()).isEqualTo(XX_CONCURRENT_MODIFICATION));
    // stored is what the first request wrote
    assertThat(employeecontractRepository.findById(contractId).orElseThrow().getReportReleaseDate())
        .isEqualTo(LocalDate.of(2026, 8, 31));
  }

  @TestConfiguration
  static class Config {

    @Bean
    ContractEditor contractEditor(EmployeecontractRepository repository, PlatformTransactionManager transactionManager) {
      return new ContractEditor(repository, transactionManager);
    }
  }

  /** Schreibt wie {@code EmployeecontractService.updateReportReleaseData}: nur über die geladene Entität. */
  @Service
  @Transactional
  static class ContractEditor {

    private final EmployeecontractRepository repository;
    private final TransactionTemplate otherRequest;

    ContractEditor(EmployeecontractRepository repository, PlatformTransactionManager transactionManager) {
      this.repository = repository;
      this.otherRequest = new TransactionTemplate(transactionManager);
      this.otherRequest.setPropagationBehavior(PROPAGATION_REQUIRES_NEW);
    }

    public void releaseAfterAnotherRequestDid(long contractId, LocalDate releaseDate) {
      var contract = repository.findById(contractId).orElseThrow();
      otherRequest.executeWithoutResult(status ->
          repository.findById(contractId).orElseThrow().setReportReleaseDate(releaseDate));
      contract.setReportReleaseDate(releaseDate);
    }
  }

  private Employeecontract createContract() {
    var salatUser = new SalatUser();
    salatUser.setLoginname(SIGN);
    salatUser.setStatus(GlobalConstants.EMPLOYEE_STATUS_MA);
    salatUser = salatUserRepository.save(salatUser);

    var employee = new Employee();
    employee.setSign(SIGN);
    employee.setFirstname("Vorname");
    employee.setLastname(SIGN);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setSalatUser(salatUser);
    employee = employeeRepository.save(employee);

    var employeecontract = new Employeecontract();
    employeecontract.setEmployee(employee);
    employeecontract.setValidFrom(LocalDate.of(2000, 1, 1));
    employeecontract.setDailyWorkingTime(Duration.ofHours(8));
    return employeecontractRepository.save(employeecontract);
  }
}
