package de.hbt.salat.order.persistence;

import static java.lang.Boolean.TRUE;
import static de.hbt.salat.common.util.ContainsPattern.like;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import de.hbt.salat.common.Hiding;
import de.hbt.salat.common.Validity;
import de.hbt.salat.common.util.ContainsPattern;
import de.hbt.salat.customer.domain.Customer_;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employee_;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.domain.Employeecontract_;
import de.hbt.salat.order.domain.Customerorder;
import de.hbt.salat.order.domain.CustomerorderOption;
import de.hbt.salat.order.domain.Customerorder_;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.domain.Employeeorder_;
import de.hbt.salat.order.domain.SearchHits;
import de.hbt.salat.order.domain.Suborder;
import de.hbt.salat.order.domain.Suborder_;
import de.hbt.salat.tag.domain.Tag_;

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
     * The orders with at least one invoiceable suborder, ordered by their sign.
     */
    public List<CustomerorderOption> getInvoiceableCustomerorders() {
        return customerorderRepository.findAllInvoiceable();
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

    /**
     * Get a list of all Customerorders fitting to the given filters ordered by their sign.
     */
    public List<Customerorder> getCustomerordersByFilters(final Boolean showInactive, final String filter, final Long customerId, final Boolean showHidden) {
        if (!ContainsPattern.canOccurInUtf8mb3(filter)) {
            return List.of();
        }
        return customerorderRepository.findAll(byFilters(showInactive, filter, customerId, showHidden), bySign());
    }

    /**
     * The first rows of the order list (#1380) and how many the filters hit, both counted in the database: the list
     * shows something without any filter, so it must not load every order to show the first few.
     */
    public SearchHits<Customerorder> findCustomerorders(Boolean showInactive, String filter, Long customerId,
        Boolean showHidden, Collection<Long> tagIds, int maxRows) {
        if (!ContainsPattern.canOccurInUtf8mb3(filter)) {
            return SearchHits.none();
        }
        var filters = byFilters(showInactive, filter, customerId, showHidden).and(carryingAnyOf(tagIds));
        // what every row shows, in the query of the rows; the count reads the filters alone
        Specification<Customerorder> withShownAssociations = filters.and((root, query, builder) -> {
            root.fetch(Customerorder_.customer);
            root.fetch(Customerorder_.respEmpHbtContract, JoinType.LEFT);
            root.fetch(Customerorder_.intermediary, JoinType.LEFT);
            return null;
        });
        var page = customerorderRepository.findAll(withShownAssociations, filters,
            PageRequest.of(0, maxRows, bySign()));
        return new SearchHits<>(page.getContent(), page.getTotalElements());
    }

    /** By sign regardless of case, the id deciding between equal ones so that the limit always cuts the same rows. */
    private static Sort bySign() {
        return Sort.by(new Order(ASC, Customerorder_.SIGN).ignoreCase(), new Order(ASC, Customerorder_.ID));
    }

    private Specification<Customerorder> byFilters(Boolean showInactive, String filter, Long customerId,
        Boolean showHidden) {
        var pattern = ContainsPattern.of(filter);
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if(!TRUE.equals(showInactive)) {
                predicates.add(Validity.<Customerorder>notInactive(Customerorder_.untilDate).toPredicate(root, query, builder));
            }
            if(!TRUE.equals(showHidden)) {
                predicates.add(notHidden().toPredicate(root, query, builder));
            }
            if(customerId != null && customerId > 0) {
                predicates.add(matchingCustomerId(customerId).toPredicate(root, query, builder));
            }
            if(pattern != null) {
                predicates.add(containing(pattern).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * The search text of the order lists (#1380), in the query rather than over the loaded orders: customer, sign,
     * descriptions and the names of the responsible employees. A name is {@link Employee#getName()}, first and last
     * name with a blank between them. The responsible employees are asked through {@code exists}, so that an order with
     * two of them stays one row.
     */
    private static Specification<Customerorder> containing(String pattern) {
        return (root, query, builder) -> {
            var customer = root.join(Customerorder_.customer);
            var contractually = root.join(Customerorder_.respEmpHbtContract, JoinType.LEFT);
            var responsible = query.subquery(Integer.class);
            var employee = responsible.correlate(root).join(Customerorder_.responsibleHbt);
            responsible.select(builder.literal(1)).where(like(builder, name(builder, employee), pattern));
            return builder.or(
                like(builder, customer.get(Customer_.shortname), pattern),
                like(builder, customer.get(Customer_.name), pattern),
                like(builder, root.get(Customerorder_.sign), pattern),
                like(builder, root.get(Customerorder_.shortdescription), pattern),
                like(builder, root.get(Customerorder_.description), pattern),
                like(builder, name(builder, contractually), pattern),
                builder.exists(responsible));
        };
    }

    /**
     * Orders carrying at least one of these tags (#847) — one is enough, the tags of a filter are alternatives. Asked
     * through {@code exists}, so that an order with two of them stays one row. No tags, no restriction.
     */
    private static Specification<Customerorder> carryingAnyOf(Collection<Long> tagIds) {
        return (root, query, builder) -> {
            if (tagIds == null || tagIds.isEmpty()) {
                return null;
            }
            var tagged = query.subquery(Integer.class);
            var tag = tagged.correlate(root).join(Customerorder_.tags);
            tagged.select(builder.literal(1)).where(tag.get(Tag_.id).in(tagIds));
            return builder.exists(tagged);
        };
    }

    private static Expression<String> name(CriteriaBuilder builder, From<?, Employee> employee) {
        return builder.concat(builder.concat(employee.get(Employee_.firstname), " "), employee.get(Employee_.lastname));
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
