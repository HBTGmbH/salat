package de.hbt.salat.dailyreport.persistence;

import static de.hbt.salat.common.GlobalConstants.YESNO_YES;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import de.hbt.salat.dailyreport.auth.TimereportVisibility;
import de.hbt.salat.dailyreport.domain.Referenceday_;
import de.hbt.salat.dailyreport.domain.Timereport;
import de.hbt.salat.dailyreport.domain.TimereportListFilter;
import de.hbt.salat.dailyreport.domain.Timereport_;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employee_;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Employeecontract_;
import de.hbt.salat.customer.domain.Customer_;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.Customerorder_;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Employeeorder_;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.Suborder_;

/**
 * The queries behind the booking list (#1092).
 *
 * <p>This is the one place in the application that builds criteria queries through the {@link EntityManager} rather
 * than through a Spring Data repository. The reason is the shape of the question, not a preference: the condition is
 * assembled at runtime from filter <em>and</em> visibility, and the same condition has to answer three different
 * things — the rows, the sums over all hits, and the values the filters may offer. A repository method can express
 * neither a dynamic disjunction nor a {@code distinct} projection under one. The third is asked of the employee orders
 * rather than of the bookings (#1127, see {@link #findFilterValues}), under the very same visibility condition.
 *
 * <p>The visibility goes into the {@code where} of every one of them, the filter into those of the rows and the sums.
 * Nothing is filtered in Java afterwards; a list that checked its rows one by one would load the month of every
 * employee to throw most of it away.
 */
@Component
@RequiredArgsConstructor
public class TimereportListDAO {

  private final EntityManager entityManager;

  /** The rows, in chronological order and cut to the maximum the filter asks for, from its offset on. */
  public List<Timereport> findRows(TimereportListFilter filter, TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Timereport> query = builder.createQuery(Timereport.class);
    var root = query.from(Timereport.class);
    // The DTO of a row reads the day, the contract with its employee, and the order with its customer. Without the
    // fetch joins every one of those is a statement of its own, per row. Contract and suborder are those of the
    // employee order (#1210).
    root.fetch(Timereport_.referenceday);
    var employeeorder = root.fetch(Timereport_.employeeorder);
    employeeorder.fetch(Employeeorder_.employeecontract).fetch(Employeecontract_.employee);
    var fetchedOrder = employeeorder.fetch(Employeeorder_.suborder).fetch(Suborder_.customerorder);
    fetchedOrder.fetch(Customerorder_.customer);
    fetchedOrder.fetch(Customerorder_.intermediary, JoinType.LEFT);
    var joins = BookingJoins.of(root);
    query.where(conditions(filter, visibility, query, root, joins, builder));
    query.orderBy(orderBy(filter, root, joins, builder));

    var typed = entityManager.createQuery(query);
    if (filter.limited()) {
      typed.setMaxResults(filter.maxResults());
    }
    if (filter.offset() > 0) {
      typed.setFirstResult(filter.offset());
    }
    return typed.getResultList();
  }

  /**
   * Chronologisch als Voreinstellung: aeltester Tag zuerst, innerhalb eines Tages nach Kuerzel und der Reihenfolge,
   * in der gebucht wurde. Jede andere Spalte sortiert genauso in der Abfrage — und behaelt Datum und Kuerzel als
   * zweites Kriterium, damit zwei gleiche Werte nicht bei jedem Aufruf anders herum stehen.
   */
  private List<Order> orderBy(TimereportListFilter filter, Root<Timereport> root, BookingJoins joins,
      CriteriaBuilder builder) {
    var refdate = root.join(Timereport_.referenceday).get(Referenceday_.refdate);
    var sign = joins.employee().get(Employee_.sign);

    Expression<?> primary = switch (filter.sort()) {
      case DATE -> refdate;
      case EMPLOYEE -> sign;
      case ORDER -> joins.customerorder().get(Customerorder_.sign);
      case SUBORDER -> joins.suborder().get(Suborder_.sign);
      case DURATION -> root.get(Timereport_.duration).as(Long.class);
    };

    var orders = new ArrayList<Order>();
    orders.add(filter.descending() ? builder.desc(primary) : builder.asc(primary));
    if (filter.sort() != TimereportListFilter.Sort.DATE) {
      orders.add(builder.asc(refdate));
    }
    if (filter.sort() != TimereportListFilter.Sort.EMPLOYEE) {
      orders.add(builder.asc(sign));
    }
    orders.add(builder.asc(root.get(Timereport_.sequencenumber)));
    // the id last, so that two bookings alike in everything else keep their places and a page cut by an offset
    // neither repeats nor skips one (#1539)
    orders.add(builder.asc(root.get(Timereport_.id)));
    return orders;
  }

  /**
   * The days a person booked on, per order, with what was booked there that day — under the same visibility as the
   * list, over all time (#1534). What the order history cuts into periods; one row per order and day, so a person with
   * years of bookings yields a few thousand rows rather than every booking.
   *
   * <p>With {@code billableOnly}, only orders for a customer count: those with at least one billable suborder. Such an
   * order counts with all its bookings, those on its non-billable suborders included — the history tells where
   * somebody worked, not what was invoiced. Without it every order counts (#1539). The order module answers the same
   * question about an order in {@code CustomerorderRepository.findInvoiceableIdsByIdIn}.
   */
  public List<BookedDay> findBookedDays(long employeeId, TimereportVisibility visibility, boolean billableOnly) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<BookedDay> query = builder.createQuery(BookedDay.class);
    var root = query.from(Timereport.class);
    var joins = BookingJoins.of(root);
    var refdate = root.join(Timereport_.referenceday).get(Referenceday_.refdate);
    var customerorderId = joins.customerorder().get(Customerorder_.id);
    var customerorderSign = joins.customerorder().get(Customerorder_.sign);
    var suborder = joins.suborder();
    var dimensions = new Dimensions(joins.employee().get(Employee_.id), customerorderId, suborder.get(Suborder_.id),
        suborder.get(Suborder_.invoice), refdate);

    var predicates = new ArrayList<Predicate>();
    predicates.add(builder.isFalse(root.get(Timereport_.deleted)));
    predicates.add(builder.equal(dimensions.employeeId(), employeeId));
    if (billableOnly) {
      // customer orders only: an order with at least one billable suborder, then with all of its bookings — internal
      // orders, vacation and sickness have none
      var billable = query.subquery(Long.class);
      var billableSuborder = billable.from(Suborder.class);
      billable.select(billableSuborder.get(Suborder_.id)).where(
          builder.equal(billableSuborder.get(Suborder_.customerorder), joins.customerorder()),
          builder.equal(billableSuborder.get(Suborder_.invoice), YESNO_YES));
      predicates.add(builder.exists(billable));
    }
    visibilityCondition(visibility, dimensions, builder).ifPresent(predicates::add);

    query.select(builder.construct(BookedDay.class, customerorderId, customerorderSign, refdate,
            builder.sum(root.get(Timereport_.duration).as(Long.class))))
        .where(predicates.toArray(Predicate[]::new))
        .groupBy(customerorderId, customerorderSign, refdate)
        .orderBy(builder.asc(customerorderId), builder.asc(refdate));
    return entityManager.createQuery(query).getResultList();
  }

  /**
   * Count, duration, billable duration and the spread over employees and orders — in one statement over all hits, not
   * only over the rows shown.
   */
  public Totals findTotals(TimereportListFilter filter, TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object[]> query = builder.createQuery(Object[].class);
    var root = query.from(Timereport.class);
    var joins = BookingJoins.of(root);

    Expression<Long> minutes = root.get(Timereport_.duration).as(Long.class);
    Expression<Long> billableMinutes = builder.<Long>selectCase()
        .when(builder.equal(joins.suborder().get(Suborder_.invoice), YESNO_YES), minutes)
        .otherwise(0L);
    Expression<Long> surchargeMinutes = root.get(Timereport_.surchargeCredit).as(Long.class);

    query.multiselect(
        builder.count(root),
        builder.sum(minutes),
        builder.sum(billableMinutes),
        builder.countDistinct(joins.employee()),
        builder.countDistinct(joins.customerorder()),
        builder.sum(surchargeMinutes));
    query.where(conditions(filter, visibility, query, root, joins, builder));

    var row = entityManager.createQuery(query).getSingleResult();
    return new Totals(
        toLong(row[0]),
        minutesToDuration(row[1]),
        minutesToDuration(row[2]),
        toLong(row[3]),
        toLong(row[4]),
        minutesToDuration(row[5]));
  }

  /**
   * The values the filters may offer: every employee, customer, order and suborder that occurs in a booking the user is
   * allowed to read. Deliberately <em>not</em> restricted to the chosen period — the lists would empty themselves while
   * somebody pages through the months. A value offered here can therefore still have no hit in the period that is
   * currently shown.
   *
   * <p>Asked of the employee orders, not of the bookings (#1127). Every booking hangs on an employee order with the same
   * employee and the same suborder, so the visibility holds for it unchanged, and the {@code exists} keeps out an
   * employee order nobody has booked on — the answer stays the one the bookings give. Over the bookings themselves a
   * visibility that spans two dimensions — my own bookings <em>or</em> those on my orders, my own <em>or</em> the
   * billable ones — reads the whole table, because no single index serves an {@code or} across both. That is the
   * position of everybody responsible for an order, and it cost more than a second per list on every call.
   *
   * <p>Since #1210 a booking has no suborder and no contract of its own; both are those of its employee order, so the
   * two questions cannot give different answers.
   */
  public FilterValues findFilterValues(TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<FilterValueRow> query = builder.createQuery(FilterValueRow.class);
    var root = query.from(Employeeorder.class);
    var suborder = root.join(Employeeorder_.suborder);
    var customerorder = suborder.join(Suborder_.customerorder);
    var dimensions = new Dimensions(
        root.join(Employeeorder_.employeecontract).join(Employeecontract_.employee).get(Employee_.id),
        customerorder.get(Customerorder_.id),
        suborder.get(Suborder_.id),
        suborder.get(Suborder_.invoice),
        null);

    query.select(builder.construct(FilterValueRow.class,
        dimensions.employeeId(),
        customerorder.join(Customerorder_.customer).get(Customer_.id),
        customerorder.join(Customerorder_.intermediary, JoinType.LEFT).get(Customer_.id),
        dimensions.customerOrderId(),
        dimensions.suborderId())).distinct(true);

    var booking = query.subquery(Long.class);
    var timereport = booking.from(Timereport.class);
    booking.select(timereport.get(Timereport_.id)).where(
        builder.equal(timereport.get(Timereport_.employeeorder), root),
        builder.isFalse(timereport.get(Timereport_.deleted)));

    var predicates = new ArrayList<Predicate>();
    predicates.add(builder.exists(booking));
    visibilityCondition(visibility, dimensions, builder).ifPresent(predicates::add);
    query.where(predicates.toArray(Predicate[]::new));

    var rows = entityManager.createQuery(query).getResultList();
    return new FilterValues(
        distinct(rows, FilterValueRow::employeeId),
        // the intermediaries are offered as customers, since the filter by customer finds their orders (#1536)
        rows.stream()
            .flatMap(row -> Stream.of(row.customerId(), row.intermediaryId()))
            .filter(Objects::nonNull)
            .distinct()
            .toList(),
        distinct(rows, FilterValueRow::customerOrderId),
        distinct(rows, FilterValueRow::suborderId));
  }

  private static List<Long> distinct(List<FilterValueRow> rows, Function<FilterValueRow, Long> value) {
    return rows.stream().map(value).distinct().toList();
  }

  private Predicate conditions(TimereportListFilter filter, TimereportVisibility visibility,
      AbstractQuery<?> query, Root<Timereport> root, BookingJoins joins, CriteriaBuilder builder) {

    var predicates = new ArrayList<Predicate>();
    predicates.add(builder.isFalse(root.get(Timereport_.deleted)));

    var refdate = root.join(Timereport_.referenceday).get(Referenceday_.refdate);
    predicates.add(builder.greaterThanOrEqualTo(refdate, filter.from()));
    predicates.add(builder.lessThanOrEqualTo(refdate, filter.until()));

    var suborder = joins.suborder();
    var customerorder = joins.customerorder();
    var dimensions = new Dimensions(
        joins.employee().get(Employee_.id),
        customerorder.get(Customerorder_.id),
        suborder.get(Suborder_.id),
        suborder.get(Suborder_.invoice),
        refdate);

    if (!filter.employeeIds().isEmpty()) {
      predicates.add(dimensions.employeeId().in(filter.employeeIds()));
    }
    // a customer stands for the orders it is the customer of and those it placed as intermediary (#1536)
    if (!filter.customerIds().isEmpty()) {
      predicates.add(builder.or(
          customerorder.join(Customerorder_.customer).get(Customer_.id).in(filter.customerIds()),
          customerorder.join(Customerorder_.intermediary, JoinType.LEFT).get(Customer_.id).in(filter.customerIds())));
    }
    // An order and a suborder are alternatives to each other: whoever picks both means either of them, not both at
    // once. Bookings hang on the suborder, so a chosen order stands for its whole tree all by itself.
    if (!filter.customerOrderIds().isEmpty() || !filter.suborderIds().isEmpty()) {
      var alternatives = new ArrayList<Predicate>();
      if (!filter.customerOrderIds().isEmpty()) {
        alternatives.add(dimensions.customerOrderId().in(filter.customerOrderIds()));
      }
      if (!filter.suborderIds().isEmpty()) {
        alternatives.add(dimensions.suborderId().in(filter.suborderIds()));
      }
      predicates.add(builder.or(alternatives.toArray(Predicate[]::new)));
    }
    // a booking is found through any of its references (#1326); exists rather than a join, so a booking
    // with two of the sought tickets is still one hit
    if (!filter.ticketKeys().isEmpty()) {
      var references = query.subquery(Integer.class);
      var reference = references.correlate(root).join(Timereport_.ticketReferences);
      references.select(builder.literal(1)).where(builder.upper(reference).in(filter.ticketKeys()));
      predicates.add(builder.exists(references));
    }
    // Tags narrow the rest of the filter (#847): a booking whose order carries one, or whose suborder lies below a
    // suborder carrying one. Chosen tags nothing carries leave nothing.
    if (filter.tags().active()) {
      var tagged = new ArrayList<Predicate>();
      if (!filter.tags().customerOrderIds().isEmpty()) {
        tagged.add(dimensions.customerOrderId().in(filter.tags().customerOrderIds()));
      }
      if (!filter.tags().suborderIds().isEmpty()) {
        tagged.add(dimensions.suborderId().in(filter.tags().suborderIds()));
      }
      predicates.add(tagged.isEmpty() ? builder.disjunction() : builder.or(tagged.toArray(Predicate[]::new)));
    }
    switch (filter.billable()) {
      case BILLABLE -> predicates.add(builder.equal(dimensions.invoice(), YESNO_YES));
      case NOT_BILLABLE -> predicates.add(builder.notEqual(dimensions.invoice(), YESNO_YES));
      case ALL -> { /* no restriction */ }
    }

    visibilityCondition(visibility, dimensions, builder).ifPresent(predicates::add);
    return builder.and(predicates.toArray(Predicate[]::new));
  }

  /**
   * The visibility as a condition: an {@code or} over the clauses, each of them an {@code and} over the dimensions it
   * restricts. Never a cross product of all employees with all orders — that would grant more than any single clause.
   *
   * <p>It is asked of a booking and of an employee order alike, which is why it takes the paths rather than a root: the
   * condition stays in one place, whichever of the two it restricts.
   */
  private Optional<Predicate> visibilityCondition(TimereportVisibility visibility, Dimensions dimensions,
      CriteriaBuilder builder) {

    if (visibility.unrestricted()) return Optional.empty();
    if (visibility.isEmpty()) return Optional.of(builder.disjunction());

    var clauses = new ArrayList<Predicate>();
    for (var clause : visibility.clauses()) {
      var parts = new ArrayList<Predicate>();
      if (!clause.employeeIds().isEmpty()) {
        parts.add(dimensions.employeeId().in(clause.employeeIds()));
      }
      if (!clause.customerOrderIds().isEmpty()) {
        parts.add(dimensions.customerOrderId().in(clause.customerOrderIds()));
      }
      if (!clause.suborderIds().isEmpty()) {
        parts.add(dimensions.suborderId().in(clause.suborderIds()));
      }
      if (clause.billableOnly()) {
        parts.add(builder.equal(dimensions.invoice(), YESNO_YES));
      }
      if (clause.days() != null && dimensions.refdate() != null) {
        if (!clause.days().isInfiniteFrom()) {
          parts.add(builder.greaterThanOrEqualTo(dimensions.refdate(), clause.days().getFrom()));
        }
        if (!clause.days().isInfiniteUntil()) {
          parts.add(builder.lessThanOrEqualTo(dimensions.refdate(), clause.days().getUntil()));
        }
      }
      clauses.add(parts.isEmpty() ? builder.conjunction() : builder.and(parts.toArray(Predicate[]::new)));
    }
    return Optional.of(builder.or(clauses.toArray(Predicate[]::new)));
  }

  /**
   * The joins a booking query reads suborder, order and employee from, made once per query and shared by condition,
   * ordering and sums. Suborder and contract of a booking are those of its employee order (#1210), so all of them hang
   * on the one join to it.
   */
  private record BookingJoins(Join<Employeeorder, Suborder> suborder, Join<Suborder, Customerorder> customerorder,
                              Join<Employeecontract, Employee> employee) {

    static BookingJoins of(Root<Timereport> root) {
      var employeeorder = root.join(Timereport_.employeeorder);
      var suborder = employeeorder.join(Employeeorder_.suborder);
      return new BookingJoins(suborder, suborder.join(Suborder_.customerorder),
          employeeorder.join(Employeeorder_.employeecontract).join(Employeecontract_.employee));
    }
  }

  /**
   * Where the dimensions of a visibility clause sit — on a booking, or on an employee order. An employee order has no
   * day, so there the days of a clause restrict nothing: the filter values may offer a value whose only bookings lie
   * outside the days of a rule, and the list then finds nothing for it.
   */
  private record Dimensions(Path<Long> employeeId, Path<Long> customerOrderId, Path<Long> suborderId,
                            Path<Character> invoice, Path<LocalDate> refdate) {}

  private static long toLong(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }

  private static Duration minutesToDuration(Object value) {
    return Duration.ofMinutes(toLong(value));
  }

  /**
   * @param count how many bookings, over all hits rather than over the rows shown
   * @param surchargeCredit the time surcharge credit of the hits (#1469)
   */
  public record Totals(long count, Duration duration, Duration billableDuration, long employees, long orders,
                       Duration surchargeCredit) {

    public static Totals none() {
      return new Totals(0, Duration.ZERO, Duration.ZERO, 0, 0, Duration.ZERO);
    }
  }

  /** The values the filters offer, as ids. */
  public record FilterValues(List<Long> employeeIds, List<Long> customerIds, List<Long> customerOrderIds,
                             List<Long> suborderIds) {

  }

  /**
   * What a person booked on one customer order on one day.
   *
   * @param minutes the sum of the bookings, in minutes as the column stores them
   */
  public record BookedDay(Long customerorderId, String customerorderSign, LocalDate day, Long minutes) {

    public Duration duration() {
      return Duration.ofMinutes(minutes == null ? 0L : minutes);
    }
  }

  /** One combination the query finds; the four lists are taken apart from these in Java. */
  public record FilterValueRow(Long employeeId, Long customerId, Long intermediaryId, Long customerOrderId,
                               Long suborderId) {}
}
