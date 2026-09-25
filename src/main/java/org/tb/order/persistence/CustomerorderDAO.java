package org.tb.order.persistence;

import static java.lang.Boolean.TRUE;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.common.Hiding;
import org.tb.common.Validity;
import org.tb.customer.domain.Customer_;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Employeeorder_;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;

/**
 * DAO class for 'Customerorder'
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class CustomerorderDAO {

    private final SuborderDAO suborderDAO;
    private final CustomerorderRepository customerorderRepository;

    /**
     * Gets the customerorder for the given id.
     */
    public Customerorder getCustomerorderById(long id) {
        return customerorderRepository.findById(id).orElse(null);
    }

    /**
     * Gets the customerorder for the given sign.
     */
    public Customerorder getCustomerorderBySign(String sign) {
        return customerorderRepository.findBySign(sign).orElse(null);
    }

    /**
     * Get a list of all Customerorders ordered by their sign.
     */
    public List<Customerorder> getCustomerorders() {
        return Lists.newArrayList(customerorderRepository.findAll(Sort.by(Customerorder_.SIGN)));
    }

    /**
     * Get a list of all Customerorders ordered by their sign.
     */
    public List<Customerorder> getInvoiceableCustomerorders() {
        return Lists.newArrayList(customerorderRepository.findAllInvoiceable());
    }

    /**
     * The orders a select box may offer: neither hidden nor inactive, ordered by sign.
     *
     * <p>Two independent criteria, joined with <em>and</em> — {@code hide} is the manual decision to
     * take an order out of the select boxes, "inactive" is the time criterion (→ ADR-0029). Joining
     * them with {@code or}, as this did until #1094, makes both of them ineffective.
     *
     * <p>The time criterion comes from {@link Validity} and therefore looks at the end alone: an
     * order beginning in the future is not inactive but merely not yet active and stays offered,
     * otherwise an order entered ahead of time is entered a second time.
     */
    public List<Customerorder> getVisibleCustomerorders() {
        var notInactive = Validity.<Customerorder>notInactive(Customerorder_.untilDate);
        return customerorderRepository.findAll(notHidden().and(notInactive),
            Sort.by(new Order(ASC, Customerorder_.SIGN).ignoreCase()));
    }

    /**
     * The orders a filter over existing bookings may offer: not hidden, ordered by sign —
     * <em>inactive ones included</em> (#1106).
     *
     * <p>The difference to {@link #getVisibleCustomerorders()} is the question, not the role asking
     * it. That one answers „worauf darf jetzt etwas Neues gebucht, angelegt, zugeordnet werden" and
     * an order that has ended is no longer an answer to it. This one answers „über welche Auftraege
     * laesst sich das Vorhandene einschraenken", and there an order that has ended is exactly what
     * somebody is looking for — its bookings do not end with it.
     *
     * <p>{@code hide} stays a criterion here: it is the manual decision to take an order out of the
     * select boxes and holds regardless of any date (→ ADR-0029). What drops out is the time
     * criterion alone.
     */
    public List<Customerorder> getNotHiddenCustomerorders() {
        return customerorderRepository.findAll(notHidden(),
            Sort.by(new Order(ASC, Customerorder_.SIGN).ignoreCase()));
    }

    /**
     * Nicht verborgen — und {@code null} zählt als nicht verborgen, so wie
     * {@link Customerorder#getHide()} es liest. Die Regel steht in {@link Hiding} (#1104).
     */
    private Specification<Customerorder> notHidden() {
        return Hiding.notHidden(Customerorder_.hide);
    }

    private Specification<Customerorder> matchingCustomerId(long customerId) {
        return (root, query, builder) -> builder.equal(root.join(Customerorder_.customer).get(Customer_.id), customerId);
    }

    private boolean filterMatchesInMemory(Customerorder co, String filter) {
        var upper = filter.toUpperCase();
        var customer = co.getCustomer();
        return containsIgnoreCase(customer.getShortname(), upper)
            || containsIgnoreCase(customer.getName(), upper)
            || containsIgnoreCase(co.getSign(), upper)
            || containsIgnoreCase(co.getDescription(), upper)
            || co.getResponsibleHbt().stream().anyMatch(e -> containsIgnoreCase(e.getName(), upper))
            || (co.getRespEmpHbtContract() != null && containsIgnoreCase(co.getRespEmpHbtContract().getName(), upper));
    }

    private static boolean containsIgnoreCase(String value, String upper) {
        return value != null && value.toUpperCase().contains(upper);
    }

    /**
     * Get a list of all Customerorders fitting to the given filters ordered by their sign.
     */
    public List<Customerorder> getCustomerordersByFilters(final Boolean showInactive, final String filter, final Long customerId, final Boolean showHidden) {
        boolean isFilter = filter != null && !filter.trim().isEmpty();
        var order = new Order(ASC, Customerorder_.SIGN).ignoreCase();
        return customerorderRepository.findAll((Specification<Customerorder>) (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInactive)) {
                predicates.add(Validity.<Customerorder>notInactive(Customerorder_.untilDate).toPredicate(root, query, builder));
            }
            if(!TRUE.equals(showHidden)) {
                predicates.add(notHidden().toPredicate(root, query, builder));
            }
            if(customerId != null && customerId > 0) {
                predicates.add(matchingCustomerId(customerId).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }, Sort.by(order)).stream()
            .filter(co -> !isFilter || filterMatchesInMemory(co, filter))
            .collect(Collectors.toList());
    }

    public List<Customerorder> getCustomerordersByCustomerId(long customerId) {
        return customerorderRepository.findAll((Specification<Customerorder>) (root, query, builder) -> {
            return builder.and(matchingCustomerId(customerId).toPredicate(root, query, builder));
        });
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     */
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        return customerorderRepository.findAllByResponsibleHbt(responsibleHbtId);
    }

    /**
     * Gets a list of all Customerorders by employee contract id.
     */
    public List<Customerorder> getCustomerordersByEmployeeContractId(long contractId) {
        return suborderDAO.getSubordersByEmployeeContractId(contractId).stream()
            .map(Suborder::getCustomerorder)
            .distinct()
            .sorted(Comparator.comparing(Customerorder::getSign))
            .collect(Collectors.toList());
    }

    public List<Customerorder> getCustomerordersWithValidEmployeeOrders(long employeeContractId, final LocalDate date) {
        return customerorderRepository.findAll((root, query, builder) -> {
            ListJoin<Suborder, Employeeorder> employeeorderJoin = root.join(Customerorder_.suborders).join(Suborder_.employeeorders);
            Join<Employeeorder, Employeecontract> employeecontractJoin = employeeorderJoin.join(Employeeorder_.employeecontract);
            var employeeContractIdEqual = builder.equal(employeecontractJoin.get(Employeecontract_.id), employeeContractId);
            var fromDateLess = builder.lessThanOrEqualTo(employeeorderJoin.get(Employeeorder_.fromDate), date);
            var untilDateNullOrGreater = builder.or(
                builder.isNull(employeeorderJoin.get(Employeeorder_.untilDate)),
                builder.greaterThanOrEqualTo(employeeorderJoin.get(Employeeorder_.untilDate), date)
            );
            query.distinct(true);
            return builder.and(employeeContractIdEqual, fromDateLess, untilDateNullOrGreater);
        }, Sort.by(Customerorder_.SIGN, Customerorder_.DESCRIPTION));
    }

}
