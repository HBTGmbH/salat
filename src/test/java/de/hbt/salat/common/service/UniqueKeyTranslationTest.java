package de.hbt.salat.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.when;
import static de.hbt.salat.common.exception.ErrorCode.BU_EMPLOYEE_COST_OVERLAP;
import static de.hbt.salat.common.exception.ErrorCode.EM_SIGN_TAKEN;
import static de.hbt.salat.common.exception.ErrorCode.XX_DUPLICATE_KEY;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Service;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.budget.domain.EmployeeCost;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.ErrorCode;
import de.hbt.salat.common.exception.ErrorCodeException;
import de.hbt.salat.dailyreport.domain.Referenceday;
import de.hbt.salat.employee.domain.Employee;

/**
 * Ein doppelter fachlicher Schlüssel endet als Befund, nicht als 500er (#1208) — gegen die echte
 * Datenbank des Tests, denn verlassen muss sich die Übersetzung darauf, dass der Treiber die
 * Verletzung als Unique-Verletzung meldet und den Namen aus dem Mapping mitliefert.
 */
@DataJpaTest
@EnableAspectJAutoProxy
@Import({AuthorizedUserAuditorAware.class, ConcurrentModificationAspect.class, UniqueKeyTranslationTest.Store.class})
@DisplayNameGeneration(ReplaceUnderscores.class)
class UniqueKeyTranslationTest {

  @Autowired
  private Store store;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  @BeforeEach
  void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
  }

  @Test
  void a_second_employee_with_the_same_sign_is_refused_with_its_own_finding() {
    store.persist(employee("abc"));

    assertThat(findingOf(() -> store.persist(employee("abc")))).isEqualTo(EM_SIGN_TAKEN);
  }

  /** Die Spalte vergleicht binär, wie die Anwendung: ein Kürzel in anderer Schreibweise ist ein anderes. */
  @Test
  void a_sign_that_differs_only_in_case_is_another_sign() {
    store.persist(employee("abc"));

    store.persist(employee("ABC"));
  }

  @Test
  void a_second_time_slice_of_a_cost_category_on_the_same_day_is_an_overlap() {
    store.persist(cost("Senior", LocalDate.of(2026, 1, 1)));

    assertThat(findingOf(() -> store.persist(cost("Senior", LocalDate.of(2026, 1, 1)))))
        .isEqualTo(BU_EMPLOYEE_COST_OVERLAP);
  }

  /** Ohne eigenen Befund bleibt der allgemeine — der Tag ist meist gleichzeitig angelegt worden. */
  @Test
  void a_key_without_a_finding_of_its_own_gets_the_general_one() {
    store.persist(referenceday(LocalDate.of(2026, 10, 1)));

    assertThat(findingOf(() -> store.persist(referenceday(LocalDate.of(2026, 10, 1))))).isEqualTo(XX_DUPLICATE_KEY);
  }

  private static ErrorCode findingOf(Runnable storing) {
    var thrown = catchThrowable(storing::run);
    assertThat(thrown).isInstanceOf(BusinessRuleException.class);
    return ((ErrorCodeException) thrown).getMessages().getFirst().getErrorCode();
  }

  private static Employee employee(String sign) {
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname("Vorname");
    employee.setLastname("Nachname");
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    return employee;
  }

  private static EmployeeCost cost(String name, LocalDate validFrom) {
    var cost = new EmployeeCost();
    cost.setName(name);
    cost.setCostCentsPerHour(10_000);
    cost.setValidFrom(validFrom);
    cost.setValidUntil(LocalDate.of(9999, 12, 31));
    return cost;
  }

  private static Referenceday referenceday(LocalDate date) {
    var referenceday = new Referenceday();
    referenceday.setRefdate(date);
    referenceday.applyCalendar(null);
    return referenceday;
  }

  /** Schreibt sofort, damit die Verletzung innerhalb des Service fällt, wo der Aspekt sie sieht. */
  @Service
  @Transactional
  static class Store {

    @PersistenceContext
    private EntityManager entityManager;

    public void persist(Object entity) {
      entityManager.persist(entity);
      entityManager.flush();
    }
  }
}
