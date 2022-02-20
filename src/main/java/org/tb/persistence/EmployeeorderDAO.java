package org.tb.persistence;

import static java.lang.Boolean.TRUE;
import static java.util.Comparator.comparing;
import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_EXTRA_VACATION;
import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_REMAINING_VACATION;
import static org.tb.GlobalConstants.CUSTOMERORDER_SIGN_VACATION;
import static org.tb.GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.bdom.Customerorder_;
import org.tb.bdom.Employee;
import org.tb.bdom.Employee_;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeecontract_;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Employeeorder_;
import org.tb.bdom.Suborder_;
import org.tb.bdom.Timereport;
import org.tb.util.DateUtils;

@Component
@RequiredArgsConstructor
public class EmployeeorderDAO {

    private static final Logger LOG = LoggerFactory.getLogger(EmployeeorderDAO.class);

    private final TimereportDAO timereportDAO;
    private final EmployeeorderRepository employeeorderRepository;

    /**
     * Gets the employeeorder for the given id.
     */
    public Employeeorder getEmployeeorderById(long id) {
        return employeeorderRepository.findById(id).orElse(null);
    }

    public List<Employeeorder> getEmployeeordersForEmployeeordercontentWarning(Employeecontract ec) {
        return employeeorderRepository.findAllByEmployeeIdAndEmployeeOrderContentUncommitted(ec.getEmployee().getId());
    }

    public List<Employeeorder> getVacationEmployeeOrdersByEmployeeContractIdAndDate(long employeecontractId, final Date date) {
        LOG.debug("starting read vacation list");
        var customerOrderSigns = new ArrayList<String>();
        customerOrderSigns.add(CUSTOMERORDER_SIGN_REMAINING_VACATION);
        customerOrderSigns.add(CUSTOMERORDER_SIGN_EXTRA_VACATION);
        customerOrderSigns.add(CUSTOMERORDER_SIGN_VACATION);
        var employeeorders= employeeorderRepository.findAllByEmployeecontractIdAndSuborderCustomerorderSignIn(
            employeecontractId,
            customerOrderSigns
        );

        employeeorders = employeeorders.stream()
            .filter(eo -> !eo.getFromDate().after(date))
            .filter(eo -> eo.getUntilDate() == null || !eo.getUntilDate().before(date))
            .filter(eo -> !eo.getSuborder().getSign().equals(SUBORDER_SIGN_OVERTIME_COMPENSATION))
            .collect(Collectors.toList());

        LOG.debug("read vacations.");
        return employeeorders;
    }

    /**
     * Returns the {@link Employeeorder} associated to the given employeecontractID and suborderId, that is valid for the given date.
     */
    public Employeeorder getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(long employeecontractId, long suborderId, Date date) {
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
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
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
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
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
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate2(long employeeContractId, long suborderId, Date date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(
            employeeContractId,
            suborderId,
            date
        ).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Gets the list of employeeorders for the given employee contract and suborder id and date.
     */
    public List<Employeeorder> getEmployeeOrderByEmployeeContractIdAndSuborderIdAndDate3(long employeeContractId, long suborderId, Date date) {
        return employeeorderRepository.findAllByEmployeecontractIdAndSuborderIdAndUntilDateGreaterThanEqual(
            employeeContractId,
            suborderId,
            date
        ).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
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
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * @param eocId The id of the associated {@link org.tb.bdom.Employeeordercontent}
     * @return Returns the {@link Employeeorder} associated to the given eocId.
     */
    public Employeeorder getEmployeeOrderByContentId(long eocId) {
        return employeeorderRepository.findByEmployeeOrderContentId(eocId).orElse(null);
    }

    /**
     * @return Returns a list of all {@link Employeeorder}s associated to the given orderId and employeeContractId.
     */
    public List<Employeeorder> getEmployeeordersByOrderIdAndEmployeeContractId(long orderId, long employeeContractId) {
        return employeeorderRepository.findAllByCustomerorderIdAndEmployeecontractId(orderId, employeeContractId).stream()
            .sorted(comparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    private Specification<Employeeorder> showOnlyValid() {
        Date now = DateUtils.now();
        return (root, query, builder) -> {
            var fromDateLess = builder.lessThanOrEqualTo(root.get(Employeeorder_.fromDate), now);
            var untilDateNullOrGreater = builder.or(
                builder.isNull(root.get(Employeeorder_.untilDate)),
                builder.greaterThanOrEqualTo(root.get(Employeeorder_.untilDate), now)
            );
            return builder.and(fromDateLess, untilDateNullOrGreater);
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

    private Specification<Employeeorder> matchingSuborderId(long suborderId) {
        return (root, query, builder) -> builder.equal(
            root.join(Employeeorder_.suborder).get(Suborder_.id),
            suborderId
        );
    }

    private Specification<Employeeorder> filterMatches(String filter) {
        final var filterValue = ('%' + filter + '%').toUpperCase();
        return (root, query, builder) -> builder.or(
            builder.like(builder.upper(root.join(Employeeorder_.employeecontract).join(Employeecontract_.employee).get(Employee_.sign)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.employeecontract).join(Employeecontract_.employee).get(Employee_.firstname)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.employeecontract).join(Employeecontract_.employee).get(Employee_.lastname)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).join(Suborder_.customerorder).get(Customerorder_.sign)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).join(Suborder_.customerorder).get(Customerorder_.description)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).join(Suborder_.customerorder).get(Customerorder_.shortdescription)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).get(Suborder_.sign)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).get(Suborder_.description)), filterValue),
            builder.like(builder.upper(root.join(Employeeorder_.suborder).get(Suborder_.shortdescription)), filterValue)
        );
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, and suborder.
     */
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId, Long customerSuborderId) {
        return employeeorderRepository.findAll((Specification<Employeeorder>) (root, query, builder) -> {
                Set<Predicate> predicates = new HashSet<>();
                if(!TRUE.equals(showInvalid)) {
                    predicates.add(showOnlyValid().toPredicate(root, query, builder));
                }
                if(employeeContractId != null && employeeContractId > 0) {
                    predicates.add(matchingEmployeecontractId(employeeContractId).toPredicate(root, query, builder));
                }
                if(customerOrderId != null && customerOrderId > 0) {
                    predicates.add(matchingCustomerorderId(customerOrderId).toPredicate(root, query, builder));
                }
                if(customerSuborderId != null && customerSuborderId > 0) {
                    predicates.add(matchingSuborderId(customerSuborderId).toPredicate(root, query, builder));
                }
                boolean isFilter = filter != null && !filter.trim().isEmpty();
                if(isFilter) {
                    predicates.add(filterMatches(filter).toPredicate(root, query, builder));
                }
                return builder.and(predicates.toArray(new Predicate[0]));
            }).stream()
            .sorted(comparing((Employeeorder e) -> e.getEmployeecontract().getEmployee().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getCustomerorder().getSign())
                .thenComparing((Employeeorder e) -> e.getSuborder().getSign())
                .thenComparing(Employeeorder::getFromDate))
            .collect(Collectors.toList());
    }

    /**
     * Get a list of all Employeeorders fitting to the given filters ordered by employee, customer order, suborder.
     *
     * @return List<Employeeorder>
     */
    public List<Employeeorder> getEmployeeordersByFilters(Boolean showInvalid, String filter, Long employeeContractId, Long customerOrderId) {
        return getEmployeeordersByFilters(showInvalid, filter, employeeContractId, customerOrderId, null);
    }

    /**
     * Calls {@link EmployeeorderDAO#save(Employeeorder, Employee)} with {@link Employee} = null.
     */
    public void save(Employeeorder eo) {
        save(eo, null);
    }

    /**
     * Saves the given Employeeorder and sets creation-/update-user and creation-/update-date.
     */
    public void save(Employeeorder eo, Employee loginEmployee) {
        employeeorderRepository.save(eo);
    }

    /**
     * Deletes the given employee order.
     */
    public boolean deleteEmployeeorderById(long eoId) {
        boolean deleteOk = true;

        List<Timereport> timereports = timereportDAO.getTimereportsByEmployeeOrderId(eoId);
        if (timereports != null && !timereports.isEmpty()) {
            deleteOk = false;
        }

        if (deleteOk) {
            employeeorderRepository.deleteById(eoId);
        }
        return deleteOk;
    }
}
