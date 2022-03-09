package org.tb.order;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static org.springframework.data.domain.Sort.Direction.ASC;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employee;

@Component
@RequiredArgsConstructor
public class SuborderDAO {
    private static final Logger LOG = LoggerFactory.getLogger(SuborderDAO.class);

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
            return getSubordersByCustomerorderId(customerorderId, DateUtils.today());
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
            return getSuborders(DateUtils.today());
        } else {
            return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
                .sorted(comparing(Suborder::getSign))
                .collect(Collectors.toList());
        }
    }

    /**
     * Get a list of all Suborders ordered by their sign.
     */
    public List<Suborder> getSuborders(LocalDate date) {
        return StreamSupport.stream(suborderRepository.findAll().spliterator(), false)
            .filter(s -> s.isValidAt(date))
            .sorted(comparing(Suborder::getSign))
            .collect(Collectors.toList());
    }

    private Specification<Suborder> showOnlyValid() {
        LocalDate now = DateUtils.today();
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

    private Specification<Suborder> filterMatches(String filter) {
        final var filterValue = ('%' + filter + '%').toUpperCase();
        return (root, query, builder) -> builder.or(
            builder.like(builder.upper(root.get(Suborder_.description)), filterValue),
            builder.like(builder.upper(root.get(Suborder_.shortdescription)), filterValue),
            builder.like(builder.upper(root.get(Suborder_.sign)), filterValue),
            builder.like(builder.upper(root.join(Suborder_.customerorder).get(Customerorder_.sign)), filterValue),
            builder.like(builder.upper(root.join(Suborder_.customerorder).get(Customerorder_.description)), filterValue),
            builder.like(builder.upper(root.join(Suborder_.customerorder).get(Customerorder_.shortdescription)), filterValue)
        );
    }

    /**
     * Get a list of all suborders fitting to the given filters ordered by their sign.
     */
    public List<Suborder> getSubordersByFilters(Boolean showInvalid, String filter, Long customerorderId) {
        var order = new Order(ASC, Suborder_.SIGN);
        return suborderRepository.findAll((root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInvalid)) {
                predicates.add(showOnlyValid().toPredicate(root, query, builder));
            }
            if(customerorderId != null && customerorderId > 0) {
                predicates.add(matchingCustomerorderId(customerorderId).toPredicate(root, query, builder));
            }
            boolean isFilter = filter != null && !filter.trim().isEmpty();
            if(isFilter) {
                predicates.add(filterMatches(filter).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }, Sort.by(order));
    }

    /**
     * Get a list of all children of the suborder associated to the given soId ordered by their sign.
     */
    public List<Suborder> getSuborderChildren(long soId) {
        return suborderRepository.findById(soId)
            .map(Suborder::getSuborders)
            .orElse(Collections.emptyList())
            .stream()
            .sorted(comparing(Suborder::getSign))
            .collect(Collectors.toList());
    }

    /**
     * @return Returns all {@link Suborder}s where the standard flag is true and that did not end before today.
     */
    public List<Suborder> getStandardSuborders() {
        return suborderRepository.findAllStandardSubordersByUntilDateGreaterThanEqual(DateUtils.today());
    }

    /**
     * Calls {@link SuborderDAO#save(Suborder, Employee)} with {@link Employee} = null.
     */
    public void save(Suborder so) {
        save(so, null);
    }

    /**
     * Saves the given suborderand sets creation-/update-user and creation-/update-date.
     */
    public void save(Suborder so, Employee loginEmployee) {
        suborderRepository.save(so);
    }

    /**
     * Deletes the given suborder.
     */
    public boolean deleteSuborderById(long soId) {
        return suborderRepository.findById(soId).map(suborder -> {
            // check if related timereports, employee orders, suborders exist - if so, no deletion possible
            if (suborder.getEmployeeorders() != null && !suborder.getEmployeeorders().isEmpty()) return false;
            if (suborder.getTimereports() != null && !suborder.getTimereports().isEmpty()) return false;
            if (suborder.getSuborders() != null && !suborder.getSuborders().isEmpty()) return false;
            suborderRepository.delete(suborder);
            LOG.debug("SuborderDAO.deleteSuborderById - deleted object {}", suborder);
            return true;
        }).orElse(false);
    }

}
