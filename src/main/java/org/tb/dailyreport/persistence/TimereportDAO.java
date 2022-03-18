package org.tb.dailyreport.persistence;

import static java.util.Comparator.comparing;
import static org.springframework.data.jpa.domain.Specification.where;
import static org.tb.common.GlobalConstants.SUBORDER_SIGN_OVERTIME_COMPENSATION;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.util.DateUtils.getBeginOfMonth;
import static org.tb.common.util.DateUtils.getEndOfMonth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.criteria.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.domain.Referenceday_;
import org.tb.dailyreport.domain.Timereport_;
import org.tb.dailyreport.domain.Timereport;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.Employeecontract_;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.Customerorder_;
import org.tb.order.domain.Employeeorder_;
import org.tb.order.domain.Suborder;
import org.tb.order.domain.Suborder_;

@Component
@RequiredArgsConstructor
public class TimereportDAO {

    private final TimereportRepository timereportRepository;

    /**
     * Gets the timereport for the given id.
     */
    public Timereport getTimereportById(long id) {
        return timereportRepository.findById(id).orElse(null);
    }

    public long getTotalDurationMinutesForSuborderAndEmployeeContract(long soId, long ecId) {
        return timereportRepository.getReportedMinutesForSuborderAndEmployeeContract(soId, ecId).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     */
    public long getTotalDurationMinutesForSuborders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return 0;
        return timereportRepository.getReportedMinutesForSuborders(ids).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes within a range of time WITH considering the hours.
     */
    public long getTotalDurationMinutesForSuborder(long soId, LocalDate fromDate, LocalDate untilDate) {
        return timereportRepository.getReportedMinutesForSuborderAndBetween(soId, fromDate, untilDate).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes WITH consideration of the hours.
     */
    public long getTotalDurationMinutesForCustomerOrder(long coId) {
        return timereportRepository.getReportedMinutesForCustomerorder(coId).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     */
    public long getTotalDurationMinutesForEmployeeOrder(long employeeorderId, LocalDate fromDate, LocalDate untilDate) {
        return timereportRepository.getReportedMinutesForEmployeeorderAndBetween(employeeorderId, fromDate, untilDate).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     */
    public long getTotalDurationMinutesForEmployeecontract(long employeecontractId, LocalDate fromDate, LocalDate untilDate) {
        return timereportRepository.getReportedMinutesForEmployeecontractAndBetween(employeecontractId, fromDate, untilDate).orElse(0L);
    }

    /**
     * Gets the sum of all duration minutes WITH considering the hours.
     */
    public long getTotalDurationMinutesForEmployeeOrder(long eoId) {
        return timereportRepository.getReportedMinutesForEmployeeorder(eoId).orElse(0L);
    }

    /**
     * Gets a list of Timereports by employee contract id and date.
     */
    public List<Timereport> getTimereportsByDateAndEmployeeContractId(long contractId, LocalDate date) {
        return timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate(contractId, date).stream()
                   .sorted(comparing(Timereport::getSequencenumber))
                   .collect(Collectors.toList());
    }

    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria:
     * 1) associated to the given employee contract
     * 2) refdate out of range of the employee contract
     *
     * @return Returns a {@link List} with all {@link Timereport}s, that fulfill the criteria.
     */
    public List<Timereport> getTimereportsOutOfRangeForEmployeeContract(Employeecontract employeecontract) {
        return timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(employeecontract.getId());
    }

    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria:
     * 1) associated to the given employee contract
     * 2) refdate out of range of the associated employee order
     *
     * @return Returns a {@link List} with all {@link Timereport}s, that fulfill the criteria.
     */
    public List<Timereport> getTimereportsOutOfRangeForEmployeeOrder(Employeecontract employeecontract) {
        return timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(employeecontract.getId());
    }

    /**
     * Gets a list of all {@link Timereport}s, that have no duration and are associated to the given ecId.
     */
    public List<Timereport> getTimereportsWithoutDurationForEmployeeContractId(long ecId, LocalDate releaseDate) {
        var timereports = timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingZeroDuration(
            ecId,
            releaseDate
        );
        return timereports.stream()
            .filter(t -> t.getSuborder().getSign().equals(SUBORDER_SIGN_OVERTIME_COMPENSATION))
            .collect(Collectors.toList());
    }

    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is open
     */
    public List<Timereport> getOpenTimereportsByEmployeeContractIdBeforeDate(long contractId, LocalDate date) {
        return timereportRepository.findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(
            contractId,
            TIMEREPORT_STATUS_OPEN,
            date
        );
    }

    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is commited
     */
    public List<Timereport> getCommitedTimereportsByEmployeeContractIdBeforeDate(long contractId, LocalDate date) {
        return timereportRepository.findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(
            contractId,
            TIMEREPORT_STATUS_COMMITED,
            date
        );
    }

    /**
     * Gets a list of all {@link Timereport}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid after and at the given date
     */
    public List<Timereport> getTimereportsByEmployeeContractIdAfterDate(long contractId, LocalDate dt) {
        return timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdateIsGreaterThanEqual(contractId, dt);
    }

    /**
     * Gets a list of Timereports by employee contract id and two dates.
     */
    public List<Timereport> getTimereportsByDatesAndEmployeeContractId(long contractId, LocalDate begin, LocalDate end) {
        return timereportRepository.findAllByEmployeecontractIdAndReferencedayBetween(contractId, begin, end);
    }

    private Specification<Timereport> reportedBetween(LocalDate begin, LocalDate end) {
        return (root, query, builder) -> builder.and(
            builder.greaterThanOrEqualTo(root.join(Timereport_.referenceday).get(Referenceday_.refdate), begin),
            builder.lessThanOrEqualTo(root.join(Timereport_.referenceday).get(Referenceday_.refdate), end)
        );
    }

    private Specification<Timereport> reportedNotBetween(LocalDate begin, LocalDate end) {
        return (root, query, builder) -> builder.or(
            builder.lessThan(root.join(Timereport_.referenceday).get(Referenceday_.refdate), begin),
            builder.greaterThan(root.join(Timereport_.referenceday).get(Referenceday_.refdate), end)
        );
    }

    private Specification<Timereport> reportedAt(LocalDate date) {
        return (root, query, builder) ->
            builder.equal(root.join(Timereport_.referenceday).get(Referenceday_.refdate), date);
    }

    private Specification<Timereport> reportedNotBefore(LocalDate date) {
        return (root, query, builder) ->
            builder.greaterThanOrEqualTo(root.join(Timereport_.referenceday).get(Referenceday_.refdate), date);
    }

    private Specification<Timereport> reportedBefore(LocalDate date) {
        return (root, query, builder) ->
            builder.lessThan(root.join(Timereport_.referenceday).get(Referenceday_.refdate), date);
    }

    private Specification<Timereport> matchesEmployeecontractId(long employeecontractId) {
        return (root, query, builder) ->
            builder.equal(root.join(Timereport_.employeecontract).get(Employeecontract_.id), employeecontractId);
    }

    private Specification<Timereport> matchesCustomerorderId(long customerorderId) {
        return (root, query, builder) ->
            builder.equal(root.join(Timereport_.suborder).join(Suborder_.customerorder).get(Customerorder_.id), customerorderId);
    }

    private Specification<Timereport> matchesSuborderId(long suborderId) {
        return (root, query, builder) ->
            builder.equal(root.join(Timereport_.suborder).get(Suborder_.id), suborderId);
    }

    private Specification<Timereport> matchesEmployeeorderId(long employeeorderId) {
        return (root, query, builder) ->
            builder.equal(root.join(Timereport_.employeeorder).get(Employeeorder_.id), employeeorderId);
    }

    private Specification<Timereport> orderedBySequencenumber() {
        return (root, query, builder) -> {
            var orderList = new ArrayList<Order>();
            orderList.addAll(query.getOrderList());
            orderList.add(builder.asc(root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.sign)));
            orderList.add(builder.asc(root.join(Timereport_.referenceday).get(Referenceday_.refdate)));
            orderList.add(builder.asc(root.get(Timereport_.sequencenumber)));
            query.orderBy(orderList);
            return null;
        };
    }

    private Specification<Timereport> orderedByCustomerorder() {
        return (root, query, builder) -> {
            var orderList = new ArrayList<Order>();
            orderList.addAll(query.getOrderList());
            orderList.add(builder.asc(root.join(Timereport_.employeecontract).join(Employeecontract_.employee).get(Employee_.sign)));
            orderList.add(builder.asc(root.join(Timereport_.referenceday).get(Referenceday_.refdate)));
            orderList.add(builder.asc(root.join(Timereport_.suborder).join(Suborder_.customerorder).get(Customerorder_.sign)));
            orderList.add(builder.asc(root.join(Timereport_.suborder).get(Suborder_.sign)));
            query.orderBy(orderList);
            return null;
        };
    }

    private Specification<Timereport> orderedByReferenceday() {
        return (root, query, builder) -> {
            var orderList = new ArrayList<Order>();
            orderList.addAll(query.getOrderList());
            orderList.add(builder.asc(root.join(Timereport_.referenceday).get(Referenceday_.refdate)));
            query.orderBy(orderList);
            return null;
        };
    }

    /**
     * Gets a list of Timereports by associated to the given employee contract id, customer order id and the time period between the two given dates.
     */
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long contractId, LocalDate begin, LocalDate end, long customerOrderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = timereportRepository.findAll(
                where(matchesEmployeecontractId(contractId))
                    .and(reportedAt(begin))
                    .and(matchesCustomerorderId(customerOrderId))
                    .and(orderedBySequencenumber())
            );
        } else {
            allTimereports = timereportRepository.findAll(
                where(matchesEmployeecontractId(contractId))
                    .and(reportedBetween(begin, end))
                    .and(matchesCustomerorderId(customerOrderId))
                    .and(orderedByCustomerorder())
            );
        }
        return allTimereports;
    }

    /**
     * Gets a list of Timereports by associated to the given employee contract id, suborder id and the time period between the two given dates.
     */
    public List<Timereport> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long contractId, LocalDate begin, LocalDate end, long suborderId) {
        List<Timereport> allTimereports;
        if (end == null) {
            allTimereports = timereportRepository.findAll(
                where(matchesEmployeecontractId(contractId))
                    .and(reportedNotBefore(begin))
                    .and(matchesEmployeecontractId(contractId))
                    .and(matchesSuborderId(suborderId))
                    .and(orderedByCustomerorder())
            );
        } else {
            if (begin.equals(end)) {
                allTimereports = timereportRepository.findAll(
                    where(matchesEmployeecontractId(contractId))
                        .and(reportedAt(begin))
                        .and(matchesEmployeecontractId(contractId))
                        .and(matchesSuborderId(suborderId))
                        .and(orderedBySequencenumber())
                );
            } else {
                allTimereports = timereportRepository.findAll(
                    where(matchesEmployeecontractId(contractId))
                        .and(reportedBetween(begin, end))
                        .and(matchesEmployeecontractId(contractId))
                        .and(matchesSuborderId(suborderId))
                        .and(orderedByCustomerorder())
                );
            }
        }
        return allTimereports;
    }

    /**
     * Gets a list of Timereports by employee order id.
     */
    public List<Timereport> getTimereportsByEmployeeOrderId(long employeeOrderId) {
        return timereportRepository.findAll(
            where(matchesEmployeeorderId(employeeOrderId))
                .and(orderedByCustomerorder())
        );
    }

    /**
     * Gets a list of Timereports by date.
     */
    public List<Timereport> getTimereportsByDate(LocalDate date) {
        return timereportRepository.findAll(
            where(reportedAt(date))
                .and(orderedBySequencenumber())
        );
    }

    /**
     * Gets a list of timereports, which lay between two dates.
     */
    public List<Timereport> getTimereportsByDates(LocalDate begin, LocalDate end) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = timereportRepository.findAll(
                where(reportedAt(begin))
                    .and(orderedBySequencenumber())
            );
        } else {
            allTimereports = timereportRepository.findAll(
                where(reportedBetween(begin, end))
                    .and(orderedByCustomerorder())
            );
        }
        return allTimereports;
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Customerorder} id.
     */
    public List<Timereport> getTimereportsByDatesAndCustomerOrderId(LocalDate begin, LocalDate end, long coId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = timereportRepository.findAll(
                where(reportedAt(begin))
                    .and(matchesCustomerorderId(coId))
                    .and(orderedBySequencenumber())
            );
        } else {
            allTimereports = timereportRepository.findAll(
                where(reportedBetween(begin, end))
                    .and(matchesCustomerorderId(coId))
                    .and(orderedByCustomerorder())
            );
        }
        return allTimereports;
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     */
    public List<Timereport> getTimereportsByDatesAndSuborderId(LocalDate begin, LocalDate end, long suborderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = timereportRepository.findAll(
                where(reportedAt(begin))
                    .and(matchesSuborderId(suborderId))
                    .and(orderedBySequencenumber())
            );
        } else {
            allTimereports = timereportRepository.findAll(
                where(reportedBetween(begin, end))
                    .and(matchesSuborderId(suborderId))
                    .and(orderedByCustomerorder())
            );
        }
        return allTimereports;
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     */
    public List<Timereport> getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(LocalDate begin, LocalDate end, long suborderId) {
        List<Timereport> allTimereports;
        if (begin.equals(end)) {
            allTimereports = timereportRepository.findAll(
                where(reportedAt(begin))
                    .and(matchesSuborderId(suborderId))
                    .and(orderedByReferenceday())
                    .and(orderedBySequencenumber())
            );
        } else {
            allTimereports = timereportRepository.findAll(
                where(reportedBetween(begin, end))
                    .and(matchesSuborderId(suborderId))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        }
        return allTimereports;
    }

    /**
     * @return Returns a timereport thats valid between the first and the last day of the given date and belonging to employeecontractid
     */
    public Timereport getLastAcceptedTimereportByDateAndEmployeeContractId(LocalDate end, long ecId) {
        LocalDate firstDay = getBeginOfMonth(end);
        LocalDate lastDay = getEndOfMonth(end);
        List<Timereport> timereportList = timereportRepository.findAll(
            where(reportedBetween(firstDay, lastDay))
                .and(matchesEmployeecontractId(ecId))
                .and((root, query, builder) -> {
            query.orderBy(builder.desc(root.join(Timereport_.referenceday).get(Referenceday_.refdate)));
            return builder.isNotNull(root.get(Timereport_.accepted));
        }));
        if (!timereportList.isEmpty()) {
            return timereportList.get(0);
        } else {
            return null;
        }
    }

    public List<Timereport> getTimereportsByEmployeeorderIdInvalidForDates(LocalDate begin, LocalDate end, Long employeeOrderId) {
        if (end == null) {
            return timereportRepository.findAll(
                where(matchesEmployeeorderId(employeeOrderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        } else {
            return timereportRepository.findAll(
                where(matchesEmployeeorderId(employeeOrderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        }
    }

    public List<Timereport> getTimereportsBySuborderIdInvalidForDates(LocalDate begin, LocalDate end, Long suborderId) {
        if (end == null) {
            return timereportRepository.findAll(
                where(matchesSuborderId(suborderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        } else {
            return timereportRepository.findAll(
                where(matchesSuborderId(suborderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        }
    }

    public List<Timereport> getTimereportsByCustomerOrderIdInvalidForDates(LocalDate begin, LocalDate end, Long customerOrderId) {
        if (end == null) {
            return timereportRepository.findAll(
                where(matchesCustomerorderId(customerOrderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        } else {
            return timereportRepository.findAll(
                where(matchesCustomerorderId(customerOrderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        }
    }

    public List<Timereport> getTimereportsByEmployeeContractIdInvalidForDates(LocalDate begin, LocalDate end, Long employeeContractId) {
        if (end == null) {
            return timereportRepository.findAll(
                where(matchesEmployeecontractId(employeeContractId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        } else {
            return timereportRepository.findAll(
                where(matchesEmployeecontractId(employeeContractId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            );
        }
    }

    /**
     * Calls {@link TimereportDAO#save(Timereport, Employee, boolean)} with {@link Employee} = null.
     */
    public void save(Timereport tr) {
        save(tr, null, true);
    }

    /**
     * Saves the given timereport and sets creation-/update-user and creation-/update-date.
     */
    public void save(Timereport tr, Employee loginEmployee, boolean changeUpdateDate) {
        timereportRepository.save(tr);
    }

    /**
     * Deletes the given timereport.
     */
    public boolean deleteTimereportById(long trId) {
        timereportRepository.deleteById(trId);
        return true;
    }

    public void saveOrUpdate(Timereport timereport) {
        timereportRepository.save(timereport);
    }

}
