package org.tb.order.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.tb.common.util.DateUtils.today;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;
import org.tb.order.domain.comparator.SubOrderComparator;

@Component
@RequiredArgsConstructor
public class SuborderDAO {

    private final SuborderRepository suborderRepository;

    /**
     * Gets the suborder for the given id.
     */
    public Suborder getSuborderById(long id) {
        return suborderRepository.findById(id).orElse(null);
    }

    /**
     * Gets a list of Suborders by employee contract id.
     */
    public List<Suborder> getSubordersByEmployeeContractId(long contractId) {
        return suborderRepository.findAllByEmployeecontractId(contractId);
    }

    /**
     * Gets a list of Suborders by employee contract id AND customerorder.
     */
    public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderId(long employeeContractId, long customerorderId, boolean onlyValid) {
        List<Suborder> employeeSpecificSuborders = getSubordersByEmployeeContractId(employeeContractId);
        if (!onlyValid) return employeeSpecificSuborders;

        var allSuborders = new ArrayList<Suborder>();
        for (Suborder so : employeeSpecificSuborders) {
            if (so.getCustomerorder().getId().equals(customerorderId)) {
                if (so.getCurrentlyValid()) {
                    allSuborders.add(so);
                }
            }
        }

        allSuborders.sort(SubOrderComparator.INSTANCE);
        return allSuborders;
    }

    /**
     * Gets all {@link Suborder}s for the given employee, restricted to those that have
     * valid {@link Employeeorder}s.
     *
     * @param employeecontractId id of the employee's contract
     * @param date the date to check validity against
     * @return a distinct list of matching {@link Suborder}s
     */
    public List<Suborder> getSubordersByEmployeeContractIdWithValidEmployeeOrders(long employeecontractId, LocalDate date) {
        return suborderRepository.findAllByEmployeecontractIdAndEmployeeorderValidAt(employeecontractId, date);
    }

    public List<Suborder> getSubordersByEmployeeContractIdAndCustomerorderIdWithValidEmployeeOrders(long employeecontractId, long customerorderId, LocalDate date) {
        return suborderRepository.findAllByEmployeecontractIdAndCustomerorderIdAndEmployeeorderValidAt(employeecontractId, customerorderId, date);
    }

    /**
     * Gets a list of Suborders by customer order id.
     */
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId, boolean onlyValid) {
        if (onlyValid) {
            return getSubordersByCustomerorderId(customerorderId, today());
        } else {
            var order = new Order(ASC, Suborder_.SIGN);
            return suborderRepository.findAllByCustomerorderId(customerorderId, Sort.by(order));
        }
    }

    /**
     * Gets a list of Suborders by customer order id.
     */
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId, LocalDate date) {
        var order = new Order(ASC, Suborder_.SIGN);
        return suborderRepository.findAllByCustomerorderId(customerorderId, Sort.by(order)).stream()
            .filter(s -> s.isValidAt(date))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Suborders ordered by their sign.
     *
     * @param onlyValid return only valid suborders
     */
    public List<Suborder> getSuborders(boolean onlyValid) {
        if (onlyValid) {
            return getSuborders(today());
        } else {
            return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
                .sorted(comparing(Suborder::getCompleteOrderSign))
                .collect(Collectors.toList());
        }
    }

    /**
     * Get a list of all Suborders ordered by their sign.
     */
    public List<Suborder> getSuborders(LocalDate date) {
        return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
            .filter(s -> s.isValidAt(date))
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .collect(Collectors.toList());
    }

    private Specification<Suborder> showOnlyValid() {
        LocalDate now = today();
        return (root, query, builder) -> {
            var fromDateLess = builder.lessThanOrEqualTo(root.get(Suborder_.fromDate), now);
            var untilDateNullOrGreater = builder.or(
                builder.isNull(root.get(Suborder_.untilDate)),
                builder.greaterThanOrEqualTo(root.get(Suborder_.untilDate), now)
            );
            var notHidden = builder.notEqual(root.get(Suborder_.hide), TRUE);
            return builder.and(fromDateLess, untilDateNullOrGreater, notHidden);
        };
    }

    private Specification<Suborder> matchingCustomerorderId(long customerorderId) {
        return (root, query, builder) -> builder.equal(root.join(Suborder_.customerorder).get(Customerorder_.id), customerorderId);
    }

    /**
     * Get a list of all suborders fitting to the given filters ordered by their sign.
     */
    public List<Suborder> getSubordersByFilters(Boolean showInvalid, String filter, Long customerorderId) {
        return suborderRepository.findAll((root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInvalid)) {
                predicates.add(showOnlyValid().toPredicate(root, query, builder));
            }
            if(customerorderId != null && customerorderId > 0) {
                predicates.add(matchingCustomerorderId(customerorderId).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }).stream()
            .filter(suborder -> matchesFilter(suborder, filter))
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .toList();
    }

    private static boolean matchesFilter(Suborder suborder, String filter) {
        if(filter == null || filter.trim().isEmpty()) {
            return true;
        }
        var customerorder = suborder.getCustomerorder();
        var matchCandidates = List.of(
            customerorder.getSign(),
            customerorder.getShortdescription(),
            customerorder.getDescription(),
            customerorder.getOrder_customer(),
            suborder.getCompleteOrderSign(),
            suborder.getShortdescription(),
            suborder.getDescription(),
            suborder.getSuborder_customer()
        );

        // do a case insensitive comparison
        final var filterValue = filter.toLowerCase();
        return matchCandidates.stream()
            .filter(Objects::nonNull)
            .map(String::toLowerCase)
            .anyMatch(candidate -> candidate.contains(filterValue));
    }

    /**
     * Get a list of all children of the suborder associated to the given soId ordered by their sign.
     */
    public List<Suborder> getSuborderChildren(long soId) {
        return suborderRepository.findById(soId)
            .map(Suborder::getSuborders)
            .orElse(Collections.emptyList())
            .stream()
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .collect(Collectors.toList());
    }

    /**
     * @return Returns all {@link Suborder}s where the standard flag is true and that did not end before today.
     */
    public List<Suborder> getStandardSuborders() {
        return suborderRepository.findAllStandardSubordersByUntilDateGreaterThanEqual(today());
    }

}
