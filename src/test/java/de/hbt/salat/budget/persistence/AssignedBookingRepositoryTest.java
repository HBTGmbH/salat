package de.hbt.salat.budget.persistence;

import static de.hbt.salat.testutils.CustomerTestUtils.uniqueShortname;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static de.hbt.salat.testutils.ReferencedayTestUtils.referenceday;

import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.persistence.AuthorizedUserAuditorAware;
import de.hbt.salat.budget.domain.AssignedBooking;
import de.hbt.salat.budget.domain.OrderBudget;
import de.hbt.salat.budget.domain.PlanBooking;
import de.hbt.salat.budget.domain.TimereportBudgetAssignment;
import de.hbt.salat.common.GlobalConstants;
import de.hbt.salat.customer.domain.Customer;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.OrderType;
import de.hbt.salat.order.domain.Suborder;

/**
 * The bookings the budget detail page lists (#997). Both the order and the cap belong to the
 * database: the page shows at most 200 of what can be thousands of rows, and which 200 those are is
 * decided here, not by whoever consumes the result.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class AssignedBookingRepositoryTest {

  private static final LocalDate DAY = LocalDate.of(2026, 6, 15);
  private static final LocalDate FROM = DAY.minusYears(1);
  private static final LocalDate UNTIL = DAY.plusYears(1);

  @Autowired
  private TimereportBudgetAssignmentRepository assignmentRepository;

  @Autowired
  private TestEntityManager entityManager;

  @MockitoBean
  private AuthorizedUser authorizedUser;

  private Customerorder customerorder;
  private Suborder suborderA;
  private Suborder suborderB;
  private OrderBudget plan;

  @BeforeEach
  public void setUp() {
    when(authorizedUser.isAuthenticated()).thenReturn(true);
    when(authorizedUser.getLoginSign()).thenReturn("test");
    customerorder = customerorder("co");
    suborderA = suborder("so-a");
    suborderB = suborder("so-b");
    plan = plan("plan");
  }

  @Test
  public void lists_the_youngest_booking_first() {
    book(plan, DAY, "abc", suborderA);
    book(plan, DAY.plusDays(10), "abc", suborderA);
    book(plan, DAY.plusDays(5), "abc", suborderA);

    assertThat(newest(10)).extracting(AssignedBooking::day)
        .containsExactly(DAY.plusDays(10), DAY.plusDays(5), DAY);
  }

  /**
   * Same day, same order on every call — otherwise the list reshuffles itself between two reloads.
   * The suborder is keyed by id, not by the sign the list displays: that sign is the whole chain of
   * parents, assembled in Java and stored nowhere.
   */
  @Test
  public void orders_bookings_of_one_day_by_employee_then_suborder_then_id() {
    var fourth = book(plan, DAY, "def", suborderA);
    var third = book(plan, DAY, "abc", suborderB);
    var first = book(plan, DAY, "abc", suborderA);
    var second = book(plan, DAY, "abc", suborderA);

    assertThat(newest(10)).extracting(AssignedBooking::id)
        .containsExactly(first, second, third, fourth);
  }

  /**
   * The point of #997: before, the cap was applied to a list ordered by employee sign, so it showed
   * every booking of the alphabetically first person and none at all of anybody else.
   */
  @Test
  public void caps_the_list_to_the_youngest_bookings_across_all_employees() {
    for (int i = 0; i < 5; i++) {
      book(plan, DAY.minusDays(i), "abc", suborderA);
    }
    var youngest = book(plan, DAY.plusDays(1), "xyz", suborderA);

    var rows = newest(3);

    assertThat(rows).hasSize(3);
    assertThat(rows.getFirst().id()).isEqualTo(youngest);
    assertThat(rows).extracting(AssignedBooking::employeeSign).contains("xyz");
  }

  @Test
  public void leaves_out_bookings_outside_the_period_and_of_other_plans() {
    var other = plan("other plan");
    var inside = book(plan, DAY, "abc", suborderA);
    book(plan, DAY.plusDays(3), "abc", suborderA);
    book(other, DAY, "abc", suborderA);

    var rows = assignmentRepository.findAssignedBookings(
        plan.getId(), DAY.minusDays(1), DAY.plusDays(1), Limit.of(10));

    assertThat(rows).extracting(AssignedBooking::id).containsExactly(inside);
  }

  @Test
  public void carries_the_columns_the_list_shows() {
    book(plan, DAY, "abc", suborderA, 2, 30, "Implementierung");

    assertThat(newest(10)).singleElement().satisfies(row -> {
      assertThat(row.day()).isEqualTo(DAY);
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.employeeName()).contains("abc");
      assertThat(row.suborderId()).isEqualTo(suborderA.getId());
      assertThat(row.duration()).isEqualTo(Duration.ofMinutes(150));
      assertThat(row.taskDescription()).isEqualTo("Implementierung");
    });
  }

  /** Derived from the capped list, the figures would understate every plan that has more. */
  @Test
  public void counts_and_sums_over_the_whole_period_not_over_the_capped_list() {
    book(plan, DAY, "abc", suborderA, 1, 0, "");
    book(plan, DAY.plusDays(1), "abc", suborderA, 2, 30, "");
    book(plan, DAY.plusDays(2), "abc", suborderA, 0, 45, "");

    assertThat(newest(1)).hasSize(1);

    var totals = assignmentRepository.findAssignedBookingTotals(plan.getId(), FROM, UNTIL);
    assertThat(totals.bookings()).isEqualTo(3);
    assertThat(totals.totalDuration()).isEqualTo(Duration.ofMinutes(60 + 150 + 45));
  }

  /**
   * Neither query names the soft delete: {@code Timereport} carries
   * {@code @SQLRestriction("deleted = false")} and Hibernate puts it into both statements. Pinned
   * here because nothing in the queries says so — a switch to a native query would drop the filter
   * silently, and a deleted booking would reappear in the list <em>and</em> in the hours.
   */
  @Test
  public void leaves_out_a_deleted_booking_in_the_list_and_in_the_figures() {
    var kept = book(plan, DAY, "abc", suborderA, 1, 0, "");
    var deleted = book(plan, DAY.plusDays(1), "abc", suborderA, 5, 0, "");
    softDelete(deleted);

    assertThat(newest(10)).extracting(AssignedBooking::id).containsExactly(kept);

    var totals = assignmentRepository.findAssignedBookingTotals(plan.getId(), FROM, UNTIL);
    assertThat(totals.bookings()).isEqualTo(1);
    assertThat(totals.totalDuration()).isEqualTo(Duration.ofHours(1));
  }

  /** An empty period yields no sum at all, not a zero — the record has to absorb that. */
  @Test
  public void reports_zero_for_a_plan_without_bookings() {
    var totals = assignmentRepository.findAssignedBookingTotals(plan.getId(), FROM, UNTIL);

    assertThat(totals.bookings()).isZero();
    assertThat(totals.totalDuration()).isEqualTo(Duration.ZERO);
  }

  /**
   * The bookings the dashboard prices (#1222): those of the plans asked about, up to the day, with
   * the plan, suborder, person, day and duration each — and without a deleted one.
   */
  @Test
  public void reads_the_bookings_of_the_plans_asked_about_up_to_the_day() {
    var other = plan("other plan");
    var unasked = plan("unasked plan");
    book(plan, DAY, "abc", suborderA, 1, 15, "");
    book(other, DAY.minusDays(1), "def", suborderB, 2, 0, "");
    book(plan, DAY.plusDays(1), "abc", suborderA, 3, 0, "");
    book(unasked, DAY, "abc", suborderA, 4, 0, "");
    softDelete(book(plan, DAY, "abc", suborderB, 5, 0, ""));

    var rows = assignmentRepository.findPlanBookings(java.util.List.of(plan.getId(), other.getId()), DAY);

    assertThat(rows).extracting(PlanBooking::orderBudgetId, PlanBooking::suborderId, PlanBooking::day,
            PlanBooking::duration)
        .containsExactlyInAnyOrder(
            tuple(plan.getId(), suborderA.getId(), DAY, Duration.ofMinutes(75)),
            tuple(other.getId(), suborderB.getId(), DAY.minusDays(1),
                Duration.ofHours(2)));
    assertThat(rows).extracting(PlanBooking::employeeId).doesNotContainNull().doesNotHaveDuplicates();
  }

  /** Written straight to the column: reading the booking back would already be filtered out. */
  private void softDelete(long timereportId) {
    entityManager.getEntityManager()
        .createNativeQuery("update Timereport set deleted = true where id = :id")
        .setParameter("id", timereportId)
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }

  private java.util.List<AssignedBooking> newest(int limit) {
    return assignmentRepository.findAssignedBookings(plan.getId(), FROM, UNTIL, Limit.of(limit));
  }

  private long book(OrderBudget budget, LocalDate day, String employeeSign, Suborder suborder) {
    return book(budget, day, employeeSign, suborder, 8, 0, "");
  }

  private long book(OrderBudget budget, LocalDate day, String employeeSign, Suborder suborder,
                    int hours, int minutes, String taskDescription) {
    var contract = employeecontract(employeeSign);

    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setFromDate(FROM);
    entityManager.persist(employeeorder);

    var timereport = new Timereport();
    timereport.setEmployeeorder(employeeorder);
    timereport.setReferenceday(referenceday(entityManager, day));
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(minutes);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription(taskDescription);
    timereport.setTraining(false);
    entityManager.persist(timereport);

    var assignment = new TimereportBudgetAssignment();
    assignment.setTimereportId(timereport.getId());
    assignment.setOrderBudget(budget);
    entityManager.persist(assignment);
    entityManager.flush();
    return timereport.getId();
  }

  private OrderBudget plan(String name) {
    var budget = new OrderBudget();
    budget.setName(name);
    budget.setCustomerorder(customerorder);
    budget.setValidFrom(FROM);
    budget.setValidUntil(UNTIL);
    budget.setActive(true);
    return entityManager.persist(budget);
  }

  private Suborder suborder(String sign) {
    var suborder = new Suborder();
    suborder.setCustomerorder(customerorder);
    suborder.setSign(sign);
    suborder.setDescription(sign);
    suborder.setShortdescription(sign);
    suborder.setInvoice(GlobalConstants.INVOICE_YES);
    suborder.setFromDate(FROM);
    suborder.setDebithours(Duration.ZERO);
    suborder.setHide(false);
    return entityManager.persist(suborder);
  }

  private Customerorder customerorder(String sign) {
    var customer = new Customer();
    customer.setName("Testkunde");
    customer.setShortname(uniqueShortname("TK"));
    customer.setAddress("Teststraße 1");
    entityManager.persist(customer);

    var order = new Customerorder();
    order.setCustomer(customer);
    order.setSign(sign);
    order.setDescription(sign);
    order.setFromDate(FROM);
    order.setOrderType(OrderType.STANDARD);
    order.setDebithours(Duration.ZERO);
    order.setHide(false);
    return entityManager.persist(order);
  }

  /** One person per sign, as the unique key demands (#1208) — the bookings of one sign share the contract. */
  private Employeecontract employeecontract(String sign) {
    var existing = entityManager.getEntityManager()
        .createQuery("select c from Employeecontract c where c.employee.sign = :sign", Employeecontract.class)
        .setParameter("sign", sign)
        .getResultList();
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    var employee = new Employee();
    employee.setSign(sign);
    employee.setFirstname(sign);
    employee.setLastname(sign);
    employee.setGender(GlobalConstants.GENDER_FEMALE);
    employee.setHide(false);
    entityManager.persist(employee);

    var contract = new Employeecontract();
    contract.setEmployee(employee);
    contract.setValidFrom(FROM);
    contract.setDailyWorkingTime(Duration.ofHours(8));
    contract.setHide(false);
    return entityManager.persist(contract);
  }

}
