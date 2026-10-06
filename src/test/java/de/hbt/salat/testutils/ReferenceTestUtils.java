package de.hbt.salat.testutils;

import static org.springframework.test.util.ReflectionTestUtils.setField;

import de.hbt.salat.auth.domain.SalatUser;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Suborder;

/**
 * Master data that exists only as its id, for tests whose repositories are mocks (ADR-0036). An
 * entity of another module is held as a reference there; what a test of such a module needs from
 * it is the id the reference carries into the foreign key, nothing else.
 */
public final class ReferenceTestUtils {

  private ReferenceTestUtils() {
  }

  public static Customerorder customerorderWithId(Long id) {
    return withId(new Customerorder(), id);
  }

  public static Suborder suborderWithId(Long id) {
    return withId(new Suborder(), id);
  }

  public static Employee employeeWithId(Long id) {
    return withId(new Employee(), id);
  }

  public static Employeeorder employeeorderWithId(Long id) {
    return withId(new Employeeorder(), id);
  }

  public static SalatUser salatUserWithId(Long id) {
    return withId(new SalatUser(), id);
  }

  /** {@code null} for {@code null}, as an optional reference is. */
  private static <T> T withId(T entity, Long id) {
    if (id == null) {
      return null;
    }
    setField(entity, "id", id);
    return entity;
  }
}
