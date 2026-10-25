package de.hbt.salat.order.persistence;

import static java.lang.Boolean.TRUE;
import static de.hbt.salat.common.util.ContainsPattern.like;
import static java.util.Comparator.comparing;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.common.Validity;
import de.hbt.salat.common.util.ContainsPattern;
import de.hbt.salat.customer.domain.Customer_;
import de.hbt.salat.employee.domain.Employee_;
import de.hbt.salat.employee.domain.Employeecontract_;
import de.hbt.salat.order.auth.EmployeeorderAuthorization;
import de.hbt.salat.order.domain.Customerorder_;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Employeeorder_;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.Suborder_;

@Component
@RequiredArgsConstructor
public class EmployeeorderDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeorderDAO.class);

    /**
     * The order of the employee order list (#1380): by order, suborder and begin, then by employee — the id last, so
     * that the limit always cuts the same rows.
     */
    private static final Sort LIST_ORDER = Sort.by(
        Employeeorder_.SUBORDER + "." + Suborder_.CUSTOMERORDER + "." + Customerorder_.SIGN,
        Employeeorder_.SUBORDER + "." + Suborder_.COMPLETE_ORDER_SIGN,
        Employeeorder_.FROM_DATE,
        Employeeorder_.EMPLOYEECONTRACT + "." + Employeecontract_.EMPLOYEE + "." + Employee_.SIGN,
        Employeeorder_.ID);

    private final EmployeeorderRepository employeeorderRepository;
    private final EmployeeorderAuthorization employeeorderAuthorization;
    private final SuborderDAO suborderDAO;

    /**
     * Gets the employeeorder for the given id.
     */
    public Employeeorder getEmployeeorderById(long id) {
        return employeeorderRepository.findById(id).orElse(null);
    }

    /** The employee orders of the contract on the vacation order, by its id (#1341), valid on the day. */
    public List<Employeeorder> getVacationEmployeeOrdersByEmployeeContractIdAndDate(long employeecontractId,
        long vacationCustomerorderId, final LocalDate date) {
        var employeeorders = employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderId(
            employeecontractId, vacationCustomerorderId);
        return employeeorders.stream().filter(eo -> eo.isValidAt(date)).collect(Collectors.toList());
    }

    /** Like {@link #getVacationEmployeeOrdersByEmployeeContractIdAndDate}, overlapping the range. */
    public List<Employeeorder> getVacationEmployeeOrders(long employeecontractId, long vacationCustomerorderId,
        final LocalDateRange range) {
        var employeeorders = employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderId(
            employeecontractId, vacationCustomerorderId);
        return employeeorders.stream().filter(eo -> eo.getValidity().overlaps(range)).collect(Collectors.toList());
    }

    /**
     * Returns the {@link Employeeorder} associated to the given employeecontractID and suborderId, that is valid for the given date.
     */
    public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId, long suborderId, LocalDate date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderId(employeecontractId, suborderId).stream()
            .filter(e -> e.isValidAt(date))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets the list of employeeorders for the given employee contract id.
     */
    public List<Employeeorder> getEmployeeOrdersByEmployeeContractId(long employeeContractId) {
        return employeeorderRepository.findAllByEmployeecontractId(employeeContractId).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given suborder id.
     */
    public List<Employeeorder> getEmployeeOrdersBySuborderId(long suborderId) {
        return employeeorderRepository.findAllBySuborderId(suborderId).stream()
            .sorted(comparing((Employeeorder e) -> e.getEmployeecontract().getEmployee().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id.
     */
    public List<Employeeorder> getEmployeeOrdersByEmployeeContractIdAndSuborderId(long employeeContractId, long suborderId) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderId(
            employeeContractId,
            suborderId
        ).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(long employeeContractId, long suborderId, LocalDate date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(
            employeeContractId,
            suborderId,
            date
        ).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    public long getEmployeeorderCount(long employeeContractId, long suborderId) {
        return employeeorderRepository.countEmployeeorders(
            employeeContractId,
            suborderId
        );
    }

    /**
     * Get a list of all Employeeorders ordered by their sign.
     *
     * @return List<Employeeorder>
     */
    public List<Employeeorder> getEmployeeorders() {
        return StreamSupport.stream(employeeorderRepository.findAll().spliterator(), false)
            .sorted(comparing((Employeeorder e) -> e.getEmployeecontract().getEmployee().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * @return Returns a list of all {@link Employeeorder}s associated to the given orderId and employeeContractId.
     */
    public List<Employeeorder> getEmployeeordersByOrderIdAndEmployeeContractId(long orderId, long employeeContractId) {
        return employeeorderRepository.findAllByCustomerorderIdAndEmployeecontractId(orderId, employeeContractId).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Nicht verborgen — {@link Employeeorder} hat kein eigenes {@code hide} und erbt die
     * Sichtbarkeit von seinen Eltern, erreicht also beide Flags über einen Join. Die Regel dahinter
     * ist dieselbe wie überall und steht in {@link Hiding} (#1104): {@code null} zählt als nicht
     * verborgen.
     */
    private Specification<Employeeorder> notHidden() {
        return (root, query, builder) -> {
            var suborder = root.join(Employeeorder_.suborder);
            return builder.and(
                Hiding.notHidden(builder, suborder.get(Suborder_.hide)),
                Hiding.notHidden(builder, suborder.join(Suborder_.customerorder).get(Customerorder_.hide))
            );
        };
    }

    private Specification<Employeeorder> matchingEmployeecontractId(long employeecontractId) {
        return (root, query, builder) -> builder.equal(
            root.join(Employeeorder_.employeecontract).get(Employeecontract_.id),
            employeecontractId
        );
    }

    private Specification<Employeeorder> matchingCustomerorderId(long customerorderId) {
        return (root, query, builder) -> builder.equal(
            root.join(Employeeorder_.suborder)
                .join(Suborder_.customerorder)
                .get(Customerorder_.id),
            customerorderId
        );
    }

    /** On an order of the customer, as customer or as intermediary (#1536). */
    private Specification<Employeeorder> matchingCustomerId(long customerId) {
        return (root, query, builder) -> {
            var customerorder = root.join(Employeeorder_.suborder).join(Suborder_.customerorder);
            return builder.or(
                builder.equal(customerorder.join(Customerorder_.customer).get(Customer_.id), customerId),
                builder.equal(customerorder.join(Customerorder_.intermediary, JoinType.LEFT).get(Customer_.id),
                    customerId));
        };
    }

    private Specification<Employeeorder> matchingSuborderIds(Set<Long> suborderIds) {
        return (root, query, builder) -> root.join(Employeeorder_.suborder).get(Suborder_.id).in(suborderIds);
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters that the user may read, ordered like the list.
     */
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInactive, String filter, Long employeeContractId, Long customerId, Long customerOrderId, Long customerSuborderId, Boolean showHidden) {
        if (!ContainsPattern.canOccurInUtf8mb3(filter)) {
            return List.of();
        }
        return employeeorderRepository.findAll(
            byFilters(showInactive, filter, employeeContractId, customerId, customerOrderId, customerSuborderId, showHidden),
            LIST_ORDER);
    }

    /**
     * The first rows of the employee order list (#1380) and how many the filters hit, both counted in the database:
     * the list shows something without any filter, so it must not load twenty thousand employee orders to show the
     * first few. Only the rows the user may read are asked for ({@link EmployeeorderAuthorization#readable()}).
     */
    public SearchHits<Employeeorder> findEmployeeorders(Boolean showInactive, String filter, Long employeeContractId,
        Long customerId, Long customerOrderId, Long customerSuborderId, Boolean showHidden, int maxRows) {
        if (!ContainsPattern.canOccurInUtf8mb3(filter)) {
            return SearchHits.none();
        }
        var filters = byFilters(showInactive, filter, employeeContractId, customerId, customerOrderId,
            customerSuborderId, showHidden);
        // what every row shows, in the query of the rows; the count reads the filters alone
        Specification<Employeeorder> withShownAssociations = filters.and((root, query, builder) -> {
            var customerorder = root.fetch(Employeeorder_.suborder).fetch(Suborder_.customerorder);
            customerorder.fetch(Customerorder_.customer);
            customerorder.fetch(Customerorder_.intermediary, JoinType.LEFT);
            root.fetch(Employeeorder_.employeecontract).fetch(Employeecontract_.employee);
            return null;
        });
        var page = employeeorderRepository.findAll(withShownAssociations, filters,
            PageRequest.of(0, maxRows, LIST_ORDER));
        return new SearchHits<>(page.getContent(), page.getTotalElements());
    }

    private Specification<Employeeorder> byFilters(Boolean showInactive, String filter, Long employeeContractId,
        Long customerId, Long customerOrderId, Long customerSuborderId, Boolean showHidden) {
        var pattern = ContainsPattern.of(filter);
        var suborderIds = branchOf(customerSuborderId);
        var readable = employeeorderAuthorization.readable();
        return (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            predicates.add(readable.toPredicate(root, query, builder));
            if(!TRUE.equals(showInactive)) {
                predicates.add(Validity.<Employeeorder>notInactive(Employeeorder_.untilDate).toPredicate(root, query, builder));
            }
            if(!TRUE.equals(showHidden)) {
                predicates.add(notHidden().toPredicate(root, query, builder));
            }
            if(employeeContractId != null && employeeContractId > 0) {
                predicates.add(matchingEmployeecontractId(employeeContractId).toPredicate(root, query, builder));
            }
            if(customerId != null && customerId > 0) {
                predicates.add(matchingCustomerId(customerId).toPredicate(root, query, builder));
            }
            if(customerOrderId != null && customerOrderId > 0) {
                predicates.add(matchingCustomerorderId(customerOrderId).toPredicate(root, query, builder));
            }
            if(suborderIds != null) {
                predicates.add(matchingSuborderIds(suborderIds).toPredicate(root, query, builder));
            }
            if(pattern != null) {
                predicates.add(containing(pattern).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** The suborder and every one below it; {@code null} for none or an unknown one, which then filters nothing. */
    private Set<Long> branchOf(Long suborderId) {
        if(suborderId == null || suborderId <= 0) {
            return null;
        }
        var suborder = suborderDAO.getSuborderById(suborderId);
        if(suborder == null) {
            return null;
        }
        return suborder.getAllChildren().stream()
            .map(Suborder::getId)
            .collect(Collectors.toSet());
    }

    /**
     * The search text of the employee order list (#1380): the employee's sign and name, and everything the suborder
     * list searches ({@link SuborderSearchText#containing}).
     */
    private static Specification<Employeeorder> containing(String pattern) {
        return (root, query, builder) -> {
            var employee = root.join(Employeeorder_.employeecontract).join(Employeecontract_.employee);
            return builder.or(
                like(builder, employee.get(Employee_.sign), pattern),
                like(builder, employee.get(Employee_.firstname), pattern),
                like(builder, employee.get(Employee_.lastname), pattern),
                SuborderSearchText.containing(root.join(Employeeorder_.suborder), query, builder, pattern));
        };
    }

    public List<Employeeorder> getEmployeeordersByEmployeeContractIdAndValidAt(long employeeContractId,
        LocalDate date) {
        return employeeorderRepository.findAll((Specification<Employeeorder>) (root, query, builder) -> {
                Set<Predicate> predicates = new HashSet<>();
                predicates.add(Validity.<Employeeorder>notInactiveOn(Employeeorder_.untilDate, date).toPredicate(root, query, builder));
                predicates.add(matchingEmployeecontractId(employeeContractId).toPredicate(root, query, builder));
                return builder.and(predicates.toArray(new Predicate[0]));
            }).stream()
            .sorted(comparing((Employeeorder e) -> e.getEmployeecontract().getEmployee().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

}
