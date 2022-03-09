package org.tb.order.persistence;

import static java.lang.Boolean.TRUE;
import static org.springframework.data.domain.Sort.Direction.ASC;

import com.google.common.collect.Lists;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.ListJoin;
import javax.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.common.util.DateUtils;
import org.tb.customer.Customer_;
import org.tb.employee.domain.Employee;
import org.tb.employee.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.Employeecontract_;
import org.tb.order.domain.Customerorder;
import org.tb.order.Customerorder_;
import org.tb.order.domain.Employeeorder;
import org.tb.order.Employeeorder_;
import org.tb.order.domain.Suborder;
import org.tb.order.Suborder_;

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
     * Get a list of all vivible Customerorders ordered by their sign.
     */
    public List<Customerorder> getVisibleCustomerorders() {
        return customerorderRepository.findAllValidAtAndNotHidden(DateUtils.today());
    }

    private Specification<Customerorder> showOnlyValid() {
        LocalDate now = DateUtils.today();
        return (root, query, builder) -> {
            var fromDateLess = builder.lessThanOrEqualTo(root.get(Customerorder_.fromDate), now);
            var untilDateNullOrGreater = builder.or(
                builder.isNull(root.get(Customerorder_.untilDate)),
                builder.greaterThanOrEqualTo(root.get(Customerorder_.untilDate), now)
            );
            var notHidden = builder.notEqual(root.get(Customerorder_.hide), TRUE);
            return builder.and(fromDateLess, untilDateNullOrGreater, notHidden);
        };
    }

    private Specification<Customerorder> matchingCustomerId(long customerId) {
        return (root, query, builder) -> builder.equal(root.join(Customerorder_.customer).get(Customer_.id), customerId);
    }

    private Specification<Customerorder> filterMatches(String filter) {
        final var filterValue = ('%' + filter + '%').toUpperCase();
        return (root, query, builder) -> builder.or(
            builder.like(builder.upper(root.get(Customerorder_.sign)), filterValue),
            builder.like(builder.upper(root.get(Customerorder_.description)), filterValue),
            builder.like(builder.upper(root.get(Customerorder_.responsible_customer_contractually)), filterValue),
            builder.like(builder.upper(root.get(Customerorder_.responsible_customer_technical)), filterValue),
            builder.like(builder.upper(root.get(Customerorder_.order_customer)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.customer).get(Customer_.name)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.customer).get(Customer_.shortname)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.responsible_hbt).get(Employee_.firstname)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.responsible_hbt).get(Employee_.lastname)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.respEmpHbtContract).get(Employee_.firstname)), filterValue),
            builder.like(builder.upper(root.join(Customerorder_.respEmpHbtContract).get(Employee_.lastname)), filterValue)
        );
    }

    /**
     * Get a list of all Customerorders fitting to the given filters ordered by their sign.
     */
    public List<Customerorder> getCustomerordersByFilters(final Boolean showInvalid, final String filter, final Long customerId) {
        var order = new Order(ASC, Customerorder_.SIGN).ignoreCase();
        return customerorderRepository.findAll((Specification<Customerorder>) (root, query, builder) -> {
            Set<Predicate> predicates = new HashSet<>();
            if(!TRUE.equals(showInvalid)) {
                predicates.add(showOnlyValid().toPredicate(root, query, builder));
            }
            if(customerId != null && customerId > 0) {
                predicates.add(matchingCustomerId(customerId).toPredicate(root, query, builder));
            }
            boolean isFilter = filter != null && !filter.trim().isEmpty();
            if(isFilter) {
                predicates.add(filterMatches(filter).toPredicate(root, query, builder));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        }, Sort.by(order));
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     */
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        return customerorderRepository.findAllByResponsibleHbt(responsibleHbtId);
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible and statusreports are necessary.
     */
    public List<Customerorder> getCustomerOrdersByResponsibleEmployeeIdWithStatusReports(long responsibleHbtId) {
        var statusreports = new ArrayList<Integer>();
        statusreports.add(4);
        statusreports.add(6);
        statusreports.add(12);
        return customerorderRepository.findAllByResponsibleHbtAndStatusReportIn(responsibleHbtId, statusreports);
    }

    /**
     * Returns a list of all {@link Customerorder}s, where the given {@link Employee} is responsible.
     */
    public List<Customerorder> getVisibleCustomerOrdersByResponsibleEmployeeId(long responsibleHbtId) {
        final var now = DateUtils.today();
        return customerorderRepository.findAllByResponsibleHbt(responsibleHbtId).stream()
            .filter(c -> !TRUE.equals(c.getHide()))
            .filter(c -> !c.getFromDate().isAfter(now))
            .filter(c -> c.getUntilDate() == null || !c.getUntilDate().isBefore(now))
            .sorted(Comparator.comparing(Customerorder::getSign))
            .collect(Collectors.toList());
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

    /**
     * Calls {@link CustomerorderDAO#save(Customerorder, Employee)} with {@link Employee} = null.
     */
    public void save(Customerorder co) {
        save(co, null);
    }

    /**
     * Saves the given order and sets creation-/update-user and creation-/update-date.
     */
    public void save(Customerorder co, Employee loginEmployee) {
        customerorderRepository.save(co);
    }

    /**
     * Deletes the given customer order.
     */
    public boolean deleteCustomerorderById(long coId) {
        var customerorder = getCustomerorderById(coId);

        // check if related suborders exist - if so, no deletion possible
        if (!customerorder.getSuborders().isEmpty()) {
            return false;
        }

        customerorderRepository.delete(customerorder);
        return true;
    }

}
