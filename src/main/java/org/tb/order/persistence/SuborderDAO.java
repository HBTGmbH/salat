package org.tb.order.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static org.tb.common.util.DateUtils.today;

import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.common.Validity;
import org.tb.customer.domain.Customer_;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;

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
     * Gets the suborders for the given ids, in one statement. Hidden ones included and unknown ids
     * silently absent — the caller knows which ids it asked for.
     */
    public List<Suborder> getSubordersByIds(Collection<Long> ids) {
        return StreamSupport.stream(suborderRepository.findAllById(ids).spliterator(), false).toList();
    }

    /**
     * Gets a list of Suborders by employee contract id.
     */
    public List<Suborder> getSubordersByEmployeeContractId(long contractId) {
        return suborderRepository.findAllByEmployeecontractId(contractId);
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
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId) {
        return suborderRepository.findAllByCustomerorderId(customerorderId, Sort.unsorted()).stream()
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .collect(Collectors.toList());
    }

    /**
     * Gets a list of Suborders by customer order id that are valid on the given date.
     *
     * <p>Asks whether the suborder applies on {@code date} — that includes its start and is
     * therefore <em>not</em> the active/inactive question of {@link org.tb.common.Validity}.
     */
    public List<Suborder> getSubordersByCustomerorderId(long customerorderId, LocalDate date) {
        return suborderRepository.findAllByCustomerorderId(customerorderId, Sort.unsorted()).stream()
            .filter(s -> s.isValidAt(date))
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Suborders ordered by their sign.
     */
    public List<Suborder> getSuborders() {
        return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
            .sorted(comparing(Suborder::getCompleteOrderSign))
            .collect(Collectors.toList());
    }

    private Specification<Suborder> notHidden() {
        return (root, query, builder) -> builder.notEqual(root.get(Suborder_.hide), TRUE);
    }

    private Specification<Suborder> matchingCustomerorderId(long customerorderId) {
        return (root, query, builder) -> builder.equal(root.join(Suborder_.customerorder).get(Customerorder_.id), customerorderId);
    }

    private Specification<Suborder> matchingCustomerId(long customerId) {
        return (root, query, builder) -> builder.equal(
            root.join(Suborder_.customerorder).join(Customerorder_.customer).get(Customer_.id), customerId);
    }

    /**
     * Get a list of all suborders fitting to the given filters ordered by their sign.
     */
    public List<Suborder> getSubordersByFilters(Boolean showInactive, String filter, Long customerorderId, Long customerId, Boolean showHidden) {
        return suborderRepository.findAll((root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInactive)) {
                predicates.add(Validity.<Suborder>notInactive(Suborder_.untilDate).toPredicate(root, query, builder));
            }
            if(!TRUE.equals(showHidden)) {
                predicates.add(notHidden().toPredicate(root, query, builder));
            }
            if(customerorderId != null && customerorderId > 0) {
                predicates.add(matchingCustomerorderId(customerorderId).toPredicate(root, query, builder));
            }
            if(customerId != null && customerId > 0) {
                predicates.add(matchingCustomerId(customerId).toPredicate(root, query, builder));
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
        var co = suborder.getCustomerorder();
        var customer = co.getCustomer();
        var matchCandidates = List.of(
            customer.getShortname(),
            customer.getName(),
            co.getSign(),
            co.getShortdescription(),
            suborder.getCompleteOrderSign(),
            suborder.getCompleteOrderDescription(true, false),
            suborder.getShortdescription()
        );

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
