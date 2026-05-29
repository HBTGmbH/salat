package org.tb.order.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static java.util.List.of;
import static org.tb.common.GlobalConstants.CUSTOMERORDER_SIGN_VACATION;

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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.domain.AccessLevel;
import org.tb.common.LocalDateRange;
import org.tb.common.util.DateUtils;
import org.tb.customer.domain.Customer_;
import org.tb.employee.domain.Employeecontract_;
import org.tb.order.auth.EmployeeorderAuthorization;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Employeeorder_;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;

@Component
@RequiredArgsConstructor
public class EmployeeorderDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeorderDAO.class);

    private final EmployeeorderRepository employeeorderRepository;
    private final EmployeeorderAuthorization employeeorderAuthorization;
    private final SuborderDAO suborderDAO;

    /**
     * Gets the employeeorder for the given id.
     */
    public Employeeorder getEmployeeorderById(long id) {
        return employeeorderRepository.findById(id).orElse(null);
    }

    public List<Employeeorder> getVacationEmployeeOrdersByEmployeeContractIdAndDate(long employeecontractId, final LocalDate date) {
        var customerOrderSigns = of(CUSTOMERORDER_SIGN_VACATION);
        var employeeorders = employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(
            employeecontractId,
            customerOrderSigns
        );
        return employeeorders.stream().filter(eo -> eo.isValidAt(date)).collect(Collectors.toList());
    }

    public List<Employeeorder> getVacationEmployeeOrders(long employeecontractId, final LocalDateRange range) {
        var customerOrderSigns = of(CUSTOMERORDER_SIGN_VACATION);
        var employeeorders = employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(
            employeecontractId,
            customerOrderSigns
        );
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

    private Specification<Employeeorder> showOnlyValid(LocalDate date) {
        return (root, query, builder) -> builder.or(
            builder.isNull(root.get(Employeeorder_.untilDate)),
            builder.greaterThanOrEqualTo(root.get(Employeeorder_.untilDate), date)
        );
    }

    private Specification<Employeeorder> notHidden() {
        return (root, query, builder) -> builder.and(
            builder.notEqual(root.join(Employeeorder_.suborder).get(Suborder_.hide), TRUE),
            builder.notEqual(root.join(Employeeorder_.suborder).join(Suborder_.customerorder).get(Customerorder_.hide), TRUE)
        );
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

    private Specification<Employeeorder> matchingCustomerId(long customerId) {
        return (root, query, builder) -> builder.equal(
            root.join(Employeeorder_.suborder)
                .join(Suborder_.customerorder)
                .join(Customerorder_.customer)
                .get(Customer_.id),
            customerId
        );
    }

    private Specification<Employeeorder> matchingSuborderIds(Set<Long> suborderIds) {
        return (root, query, builder) -> root.join(Employeeorder_.suborder).get(Suborder_.id).in(suborderIds);
    }

    private boolean filterMatchesInMemory(Employeeorder eo, String filter) {
        var upper = filter.toUpperCase();
        var sub = eo.getSuborder();
        var emp = eo.getEmployeecontract().getEmployee();
        var co = sub.getCustomerorder();
        return containsIgnoreCase(emp.getSign(), upper)
            || containsIgnoreCase(emp.getFirstname(), upper)
            || containsIgnoreCase(emp.getLastname(), upper)
            || containsIgnoreCase(co.getSign(), upper)
            || containsIgnoreCase(co.getDescription(), upper)
            || containsIgnoreCase(co.getShortdescription(), upper)
            || containsIgnoreCase(sub.getSign(), upper)
            || containsIgnoreCase(sub.getDescription(), upper)
            || containsIgnoreCase(sub.getShortdescription(), upper);
    }

    private static boolean containsIgnoreCase(String value, String upper) {
        return value != null && value.toUpperCase().contains(upper);
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, and suborder.
     */
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerId, Long customerOrderId, Long customerSuborderId, Boolean showHidden) {
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        return employeeorderRepository.findAll((Specification<Employeeorder>) (root, query, builder) -> {
                Set<Predicate> predicates = new HashSet<>();
                if(!TRUE.equals(showInvalid)) {
                    predicates.add(showOnlyValid(DateUtils.today()).toPredicate(root, query, builder));
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
                if(customerSuborderId != null && customerSuborderId > 0) {
                    var suborder = suborderDAO.getSuborderById(customerSuborderId);
                    if(suborder != null) {
                        var ids = suborder.getAllChildren().stream()
                            .map(Suborder::getId)
                            .collect(Collectors.toSet());
                        predicates.add(matchingSuborderIds(ids).toPredicate(root, query, builder));
                    }
                }
                return builder.and(predicates.toArray(new Predicate[0]));
            }).stream()
            .filter(eo -> employeeorderAuthorization.isAuthorized(eo, AccessLevel.READ))
            .filter(eo -> !isFilter || filterMatchesInMemory(eo, filter))
            .sorted(comparing((Employeeorder e) -> e.getEmployeecontract().getEmployee().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCompleteOrderSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    public List<Employeeorder> getEmployeeordersByEmployeeContractIdAndValidAt(long employeeContractId,
        LocalDate date) {
        return employeeorderRepository.findAll((Specification<Employeeorder>) (root, query, builder) -> {
                Set<Predicate> predicates = new HashSet<>();
                predicates.add(showOnlyValid(date).toPredicate(root, query, builder));
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
