package org.tb.budget.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.persistence.AuthorizedUserAuditorAware;
import org.tb.budget.domain.AssignedEmployeeDay;
import org.tb.budget.domain.BudgetEmployeeMinutes;
import org.tb.budget.domain.OrderBudget;
import org.tb.budget.domain.TimereportBudgetAssignment;
import org.tb.common.GlobalConstants;
import org.tb.customer.domain.Customer;
import org.tb.dailyreport.domain.Referenceday;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.OrderType;
import org.tb.order.domain.Suborder;

/**
 * The two aggregates behind the "Mitarbeitende" card and the overview column (#964).
 *
 * <p>Both are grouped and summed in the database, and the overview one is additionally ordered by
 * the sum. Nothing else in the project orders by an aggregate, so that ordering is pinned here
 * rather than assumed — and so is the grouping key of the card, which has to stay fine enough for
 * the two rate lookups to resolve by suborder and date.
 */
@DataJpaTest
@Import(AuthorizedUserAuditorAware.class)
@DisplayNameGeneration(ReplaceUnderscores.class)
public class BudgetEmployeeQueryTest {

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

  // --- the card ---------------------------------------------------------------------------------

  @Test
  public void counts_and_sums_the_bookings_of_one_person_on_one_suborder_and_day() {
    book(plan, DAY, "abc", suborderA, 2, 30);
    book(plan, DAY, "abc", suborderA, 1, 15);

    assertThat(days()).singleElement().satisfies(row -> {
      assertThat(row.employeeSign()).isEqualTo("abc");
      assertThat(row.suborderId()).isEqualTo(suborderA.getId());
      assertThat(row.day()).isEqualTo(DAY);
      assertThat(row.bookings()).isEqualTo(2);
      assertThat(row.duration()).isEqualTo(Duration.ofMinutes(150 + 75));
    });
  }

  /**
   * Suborder and day stay in the key: both rate lookups resolve by them, so a coarser grouping
   * would throw away what the resolution needs.
   */
  @Test
  public void keeps_suborder_and_day_apart_within_one_person() {
    book(plan, DAY, "abc", suborderA, 1, 0);
    book(plan, DAY, "abc", suborderB, 2, 0);
    book(plan, DAY.plusDays(1), "abc", suborderA, 3, 0);

    assertThat(days()).hasSize(3)
        .extracting(AssignedEmployeeDay::duration)
        .containsExactlyInAnyOrder(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3));
  }

  /** The name the card shows comes out of the query, concatenated as {@code Employee#getName()}. */
  @Test
  public void carries_the_name_of_the_person() {
    book(plan, DAY, "abc", suborderA, 1, 0);

    assertThat(days()).singleElement()
        .satisfies(row -> assertThat(row.employeeName()).isEqualTo("abc abc"));
  }

  @Test
  public void leaves_out_bookings_outside_the_period_and_of_other_plans() {
    var other = plan("other plan");
    book(plan, DAY, "abc", suborderA, 1, 0);
    book(plan, DAY.plusDays(3), "def", suborderA, 1, 0);
    book(other, DAY, "ghi", suborderA, 1, 0);

    var rows = assignmentRepository.findAssignedEmployeeDays(
        plan.getId(), DAY.minusDays(1), DAY.plusDays(1));

    assertThat(rows).extracting(AssignedEmployeeDay::employeeSign).containsExactly("abc");
  }

  /**
   * Neither aggregate names the soft delete: {@code Timereport} carries
   * {@code @SQLRestriction("deleted = false")} and Hibernate puts it into both statements. Pinned
   * here because nothing in the queries says so.
   */
  @Test
  public void leaves_out_a_deleted_booking_in_both_aggregates() {
    book(plan, DAY, "abc", suborderA, 1, 0);
    softDelete(book(plan, DAY, "abc", suborderA, 5, 0));

    assertThat(days()).singleElement()
        .satisfies(row -> {
          assertThat(row.bookings()).isEqualTo(1);
          assertThat(row.duration()).isEqualTo(Duration.ofHours(1));
        });
    assertThat(minutes()).singleElement()
        .satisfies(row -> assertThat(row.duration()).isEqualTo(Duration.ofHours(1)));
  }

  /**
   * A booking without a duration drops out of the sum — {@code h * 60 + m} is null then, and the
   * defence {@code AssignedBooking} carries in Java does not reach into an aggregate. The same
   * happens in {@code findAssignedBookingTotals}, so at least the two agree. The count keeps it.
   */
  @Test
  public void drops_a_booking_without_a_duration_out_of_the_sum_but_not_out_of_the_count() {
    book(plan, DAY, "abc", suborderA, 1, 0);
    bookWithoutDuration(plan, DAY, "abc", suborderA);

    assertThat(days()).singleElement().satisfies(row -> {
      assertThat(row.bookings()).isEqualTo(2);
      assertThat(row.duration()).isEqualTo(Duration.ofHours(1));
    });
  }

  @Test
  public void reports_no_rows_for_a_plan_without_bookings() {
    assertThat(days()).isEmpty();
  }

  // --- the overview column ----------------------------------------------------------------------

  /** One statement for every row of the page, and the people of one plan stay with that plan. */
  @Test
  public void groups_several_plans_with_overlapping_people_in_one_query() {
    var second = plan("second plan");
    book(plan, DAY, "abc", suborderA, 4, 0);
    book(second, DAY, "abc", suborderA, 2, 0);
    book(second, DAY, "def", suborderA, 1, 0);

    var rows = assignmentRepository.findEmployeeMinutesByBudgetIds(List.of(plan.getId(), second.getId()));

    assertThat(rows).hasSize(3);
    assertThat(rows).filteredOn(row -> row.orderBudgetId() == plan.getId())
        .extracting(BudgetEmployeeMinutes::employeeSign).containsExactly("abc");
    assertThat(rows).filteredOn(row -> row.orderBudgetId() == second.getId())
        .extracting(BudgetEmployeeMinutes::employeeSign).containsExactly("abc", "def");
  }

  /**
   * The order the column shows. It is the only {@code ORDER BY sum(...)} in the project, so it is
   * pinned against the database rather than trusted.
   */
  @Test
  public void orders_the_people_of_a_plan_by_hours_descending() {
    book(plan, DAY, "abc", suborderA, 1, 0);
    book(plan, DAY, "def", suborderA, 8, 0);
    book(plan, DAY, "ghi", suborderA, 4, 0);

    assertThat(minutes()).extracting(BudgetEmployeeMinutes::employeeSign)
        .containsExactly("def", "ghi", "abc");
  }

  /** The bookings of one person add up across suborders and days. */
  @Test
  public void sums_the_bookings_of_one_person_over_the_whole_plan() {
    book(plan, DAY, "abc", suborderA, 2, 30);
    book(plan, DAY.plusDays(40), "abc", suborderB, 1, 30);

    assertThat(minutes()).singleElement()
        .satisfies(row -> assertThat(row.duration()).isEqualTo(Duration.ofHours(4)));
  }

  /** No period parameter: an assignment only exists for a booking inside the plan's validity. */
  @Test
  public void counts_every_assigned_booking_regardless_of_its_date() {
    book(plan, FROM, "abc", suborderA, 1, 0);
    book(plan, UNTIL, "abc", suborderA, 1, 0);

    assertThat(minutes()).singleElement()
        .satisfies(row -> assertThat(row.duration()).isEqualTo(Duration.ofHours(2)));
  }

  @Test
  public void reports_nothing_for_a_plan_without_bookings() {
    var empty = plan("empty plan");
    book(plan, DAY, "abc", suborderA, 1, 0);

    var rows = assignmentRepository.findEmployeeMinutesByBudgetIds(List.of(plan.getId(), empty.getId()));

    assertThat(rows).extracting(BudgetEmployeeMinutes::orderBudgetId).containsExactly(plan.getId());
  }

  // --- fixtures ---------------------------------------------------------------------------------

  private List<AssignedEmployeeDay> days() {
    return assignmentRepository.findAssignedEmployeeDays(plan.getId(), FROM, UNTIL);
  }

  private List<BudgetEmployeeMinutes> minutes() {
    return assignmentRepository.findEmployeeMinutesByBudgetIds(List.of(plan.getId()));
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

  private long book(OrderBudget budget, LocalDate day, String employeeSign, Suborder suborder,
                    int hours, int minutes) {
    return persistBooking(budget, day, employeeSign, suborder, hours, minutes);
  }

  private void bookWithoutDuration(OrderBudget budget, LocalDate day, String employeeSign,
                                   Suborder suborder) {
    persistBooking(budget, day, employeeSign, suborder, null, null);
  }

  private long persistBooking(OrderBudget budget, LocalDate day, String employeeSign,
                              Suborder suborder, Integer hours, Integer minutes) {
    var contract = employeecontract(employeeSign);

    var employeeorder = new Employeeorder();
    employeeorder.setSuborder(suborder);
    employeeorder.setEmployeecontract(contract);
    employeeorder.setSign(suborder.getSign());
    employeeorder.setFromDate(FROM);
    entityManager.persist(employeeorder);

    var timereport = new Timereport();
    timereport.setEmployeecontract(contract);
    timereport.setEmployeeorder(employeeorder);
    timereport.setSuborder(suborder);
    timereport.setReferenceday(referenceday(day));
    timereport.setDurationhours(hours);
    timereport.setDurationminutes(minutes);
    timereport.setStatus(GlobalConstants.TIMEREPORT_STATUS_OPEN);
    timereport.setTaskdescription("");
    timereport.setTraining(false);
    entityManager.persist(timereport);

    var assignment = new TimereportBudgetAssignment();
    assignment.setTimereportId(timereport.getId());
    assignment.setOrderBudget(budget);
    entityManager.persist(assignment);
    entityManager.flush();
    return timereport.getId();
  }

  /** One row per date, as in production — the reference day is shared by every booking of that day. */
  private Referenceday referenceday(LocalDate day) {
    var existing = entityManager.getEntityManager()
        .createQuery("select r from Referenceday r where r.refdate = :day", Referenceday.class)
        .setParameter("day", day)
        .getResultList();
    if (!existing.isEmpty()) {
      return existing.getFirst();
    }
    var referenceday = new Referenceday();
    referenceday.setRefdate(day);
    return entityManager.persist(referenceday);
  }

  private OrderBudget plan(String name) {
    var budget = new OrderBudget();
    budget.setName(name);
    budget.setCustomerorderSign(customerorder.getSign());
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
    customer.setShortname("TK");
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

  /**
   * A new person per booking, sharing sign and name where the sign repeats — which is what makes
   * the grouping keys do their work rather than the identity of one row.
   */
  private Employeecontract employeecontract(String sign) {
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
