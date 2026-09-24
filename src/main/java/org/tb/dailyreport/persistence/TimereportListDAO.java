package org.tb.dailyreport.persistence;

import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.GlobalConstants.YESNO_YES;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.auth.TimereportVisibility;
import org.tb.dailyreport.domain.Referenceday_;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportListFilter;
import org.tb.dailyreport.domain.Timereport_;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract_;
import org.tb.customer.domain.Customer_;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Suborder_;

/**
 * The queries behind the booking list (#1092).
 *
 * <p>This is the one place in the application that builds criteria queries through the {@link EntityManager} rather
 * than through a Spring Data repository. The reason is the shape of the question, not a preference: the condition is
 * assembled at runtime from filter <em>and</em> visibility, and the same condition has to answer three different
 * things — the rows, the sums over all hits, and the values the filters may offer. A repository method can express
 * neither a dynamic disjunction nor a {@code distinct} projection under one.
 *
 * <p>Both the filter and the visibility go into the {@code where} of every one of them. Nothing is filtered in Java
 * afterwards; a list that checked its rows one by one would load the month of every employee to throw most of it away.
 */
@Component
@RequiredArgsConstructor
public class TimereportListDAO {

  private final EntityManager entityManager;

  /** The rows, in chronological order and cut to the maximum the filter asks for. */
  public List<Timereport> findRows(TimereportListFilter filter, TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Timereport> query = builder.createQuery(Timereport.class);
    var root = query.from(Timereport.class);
    // The DTO of a row reads the day, the contract with its employee, and the order with its customer. Without the
    // fetch joins every one of those is a statement of its own, per row.
    root.fetch(Timereport_.referenceday);
    root.fetch(Timereport_.employeecontract).fetch(Employeecontract_.employee);
    root.fetch(Timereport_.suborder).fetch(Suborder_.customerorder).fetch(Customerorder_.customer);
    root.fetch(Timereport_.employeeorder);
    query.where(conditions(filter, visibility, root, builder));
    query.orderBy(orderBy(filter, root, builder));

    var typed = entityManager.createQuery(query);
    if (filter.limited()) {
      typed.setMaxResults(filter.maxResults());
    }
    return typed.getResultList();
  }

  /**
   * Chronologisch als Voreinstellung: aeltester Tag zuerst, innerhalb eines Tages nach Kuerzel und der Reihenfolge,
   * in der gebucht wurde. Jede andere Spalte sortiert genauso in der Abfrage — und behaelt Datum und Kuerzel als
   * zweites Kriterium, damit zwei gleiche Werte nicht bei jedem Aufruf anders herum stehen.
   */
  private List<Order> orderBy(TimereportListFilter filter, Root<Timereport> root, CriteriaBuilder builder) {
    var refdate = root.join(Timereport_.referenceday).get(Referenceday_.refdate);
    var sign = root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.sign);
    var suborder = root.join(Timereport_.suborder);

    Expression<?> primary = switch (filter.sort()) {
      case DATE -> refdate;
      case EMPLOYEE -> sign;
      case ORDER -> suborder.join(Suborder_.customerorder).get(Customerorder_.sign);
      case SUBORDER -> suborder.get(Suborder_.sign);
      case DURATION -> builder.sum(
          builder.prod(root.get(Timereport_.durationhours).as(Long.class), (long) MINUTES_PER_HOUR),
          root.get(Timereport_.durationminutes).as(Long.class));
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
    return orders;
  }

  /**
   * Count, duration, billable duration and the spread over employees and orders — in one statement over all hits, not
   * only over the rows shown.
   */
  public Totals findTotals(TimereportListFilter filter, TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object[]> query = builder.createQuery(Object[].class);
    var root = query.from(Timereport.class);
    var suborder = root.join(Timereport_.suborder);

    Expression<Long> minutes = builder.sum(
        builder.prod(root.get(Timereport_.durationhours).as(Long.class), (long) MINUTES_PER_HOUR),
        root.get(Timereport_.durationminutes).as(Long.class));
    Expression<Long> billableMinutes = builder.<Long>selectCase()
        .when(builder.equal(suborder.get(Suborder_.invoice), YESNO_YES), minutes)
        .otherwise(0L);

    query.multiselect(
        builder.count(root),
        builder.sum(minutes),
        builder.sum(billableMinutes),
        builder.countDistinct(root.join(Timereport_.employeecontract).join(Employeecontract_.employee)),
        builder.countDistinct(suborder.join(Suborder_.customerorder)));
    query.where(conditions(filter, visibility, root, builder));

    var row = entityManager.createQuery(query).getSingleResult();
    return new Totals(
        toLong(row[0]),
        minutesToDuration(row[1]),
        minutesToDuration(row[2]),
        toLong(row[3]),
        toLong(row[4]));
  }

  /**
   * The values the filters may offer: everything that occurs in a booking the user is allowed to read. Deliberately
   * <em>not</em> restricted to the chosen period — the lists would empty themselves while somebody pages through the
   * months. A value offered here can therefore still have no hit in the period that is currently shown.
   */
  public FilterValues findFilterValues(TimereportVisibility visibility) {
    return new FilterValues(
        distinctLongs(visibility, (root, builder) ->
            root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.id)),
        distinctLongs(visibility, (root, builder) ->
            root.join(Timereport_.suborder).join(Suborder_.customerorder).join(Customerorder_.customer).get(Customer_.id)),
        distinctLongs(visibility, (root, builder) ->
            root.join(Timereport_.suborder).join(Suborder_.customerorder).get(Customerorder_.id)),
        distinctLongs(visibility, (root, builder) ->
            root.join(Timereport_.suborder).get(Suborder_.id)),
        distinctTicketReferences(visibility));
  }

  private List<Long> distinctLongs(TimereportVisibility visibility, Projection<Long> projection) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = builder.createQuery(Long.class);
    var root = query.from(Timereport.class);
    query.select(projection.of(root, builder)).distinct(true);
    query.where(visibleAndAlive(visibility, root, builder));
    return entityManager.createQuery(query).getResultList();
  }

  private List<String> distinctTicketReferences(TimereportVisibility visibility) {
    var builder = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> query = builder.createQuery(String.class);
    var root = query.from(Timereport.class);
    query.select(root.get(Timereport_.ticketReference)).distinct(true);
    query.where(builder.and(
        visibleAndAlive(visibility, root, builder),
        builder.isNotNull(root.get(Timereport_.ticketReference)),
        builder.notEqual(root.get(Timereport_.ticketReference), "")));
    return entityManager.createQuery(query).getResultList();
  }

  private Predicate visibleAndAlive(TimereportVisibility visibility, Root<Timereport> root, CriteriaBuilder builder) {
    var predicates = new ArrayList<Predicate>();
    predicates.add(builder.isFalse(root.get(Timereport_.deleted)));
    visibilityCondition(visibility, root, builder).ifPresent(predicates::add);
    return builder.and(predicates.toArray(Predicate[]::new));
  }

  private Predicate conditions(TimereportListFilter filter, TimereportVisibility visibility,
      Root<Timereport> root, CriteriaBuilder builder) {

    var predicates = new ArrayList<Predicate>();
    predicates.add(builder.isFalse(root.get(Timereport_.deleted)));

    var refdate = root.join(Timereport_.referenceday).get(Referenceday_.refdate);
    predicates.add(builder.greaterThanOrEqualTo(refdate, filter.from()));
    predicates.add(builder.lessThanOrEqualTo(refdate, filter.until()));

    var suborder = root.join(Timereport_.suborder);
    var customerorder = suborder.join(Suborder_.customerorder);

    if (!filter.employeeIds().isEmpty()) {
      predicates.add(root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.id)
          .in(filter.employeeIds()));
    }
    if (!filter.customerIds().isEmpty()) {
      predicates.add(customerorder.join(Customerorder_.customer).get(Customer_.id).in(filter.customerIds()));
    }
    // An order and a suborder are alternatives to each other: whoever picks both means either of them, not both at
    // once. Bookings hang on the suborder, so a chosen order stands for its whole tree all by itself.
    if (!filter.customerOrderIds().isEmpty() || !filter.suborderIds().isEmpty()) {
      var alternatives = new ArrayList<Predicate>();
      if (!filter.customerOrderIds().isEmpty()) {
        alternatives.add(customerorder.get(Customerorder_.id).in(filter.customerOrderIds()));
      }
      if (!filter.suborderIds().isEmpty()) {
        alternatives.add(suborder.get(Suborder_.id).in(filter.suborderIds()));
      }
      predicates.add(builder.or(alternatives.toArray(Predicate[]::new)));
    }
    if (!filter.ticketKeys().isEmpty()) {
      predicates.add(builder.upper(root.get(Timereport_.ticketReference)).in(filter.ticketKeys()));
    }
    switch (filter.billable()) {
      case BILLABLE -> predicates.add(builder.equal(suborder.get(Suborder_.invoice), YESNO_YES));
      case NOT_BILLABLE -> predicates.add(builder.notEqual(suborder.get(Suborder_.invoice), YESNO_YES));
      case ALL -> { /* no restriction */ }
    }

    visibilityCondition(visibility, root, builder).ifPresent(predicates::add);
    return builder.and(predicates.toArray(Predicate[]::new));
  }

  /**
   * The visibility as a condition: an {@code or} over the clauses, each of them an {@code and} over the dimensions it
   * restricts. Never a cross product of all employees with all orders — that would grant more than any single clause.
   */
  private Optional<Predicate> visibilityCondition(TimereportVisibility visibility,
      Root<Timereport> root, CriteriaBuilder builder) {

    if (visibility.unrestricted()) return Optional.empty();
    if (visibility.isEmpty()) return Optional.of(builder.disjunction());

    var clauses = new ArrayList<Predicate>();
    for (var clause : visibility.clauses()) {
      var parts = new ArrayList<Predicate>();
      if (!clause.employeeIds().isEmpty()) {
        parts.add(root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.id)
            .in(clause.employeeIds()));
      }
      if (!clause.customerOrderIds().isEmpty()) {
        parts.add(root.join(Timereport_.suborder).join(Suborder_.customerorder).get(Customerorder_.id)
            .in(clause.customerOrderIds()));
      }
      if (!clause.suborderIds().isEmpty()) {
        parts.add(root.join(Timereport_.suborder).get(Suborder_.id).in(clause.suborderIds()));
      }
      if (clause.billableOnly()) {
        parts.add(builder.equal(root.join(Timereport_.suborder).get(Suborder_.invoice), YESNO_YES));
      }
      clauses.add(parts.isEmpty() ? builder.conjunction() : builder.and(parts.toArray(Predicate[]::new)));
    }
    return Optional.of(builder.or(clauses.toArray(Predicate[]::new)));
  }

  private static long toLong(Object value) {
    return value == null ? 0L : ((Number) value).longValue();
  }

  private static Duration minutesToDuration(Object value) {
    return Duration.ofMinutes(toLong(value));
  }

  @FunctionalInterface
  private interface Projection<T> {
    Expression<T> of(Root<Timereport> root, CriteriaBuilder builder);
  }

  /** @param count how many bookings, over all hits rather than over the rows shown */
  public record Totals(long count, Duration duration, Duration billableDuration, long employees, long orders) {

    public static Totals none() {
      return new Totals(0, Duration.ZERO, Duration.ZERO, 0, 0);
    }
  }

  /** The values the filters offer, as ids and as the ticket references they were typed as. */
  public record FilterValues(List<Long> employeeIds, List<Long> customerIds, List<Long> customerOrderIds,
                             List<Long> suborderIds, List<String> ticketReferences) {

  }
}
