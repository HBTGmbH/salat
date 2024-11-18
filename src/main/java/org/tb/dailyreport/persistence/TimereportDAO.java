package org.tb.dailyreport.persistence;

import static java.util.Comparator.comparing;
import static org.springframework.data.jpa.domain.Specification.where;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_COMMITED;
import static org.tb.common.GlobalConstants.TIMEREPORT_STATUS_OPEN;
import static org.tb.common.GlobalConstants.YESNO_YES;

import jakarta.persistence.criteria.Order;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.tb.auth.AccessLevel;
import org.tb.auth.AuthService;
import org.tb.dailyreport.domain.Referenceday_;
import org.tb.dailyreport.domain.Timereport;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.domain.Timereport_;
import org.tb.employee.domain.Employee;
import org.tb.employee.domain.Employee_;
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
    private final AuthService authService;

    /**
     * Gets the timereport for the given id.
     */
    public TimereportDTO getTimereportById(long id) {
        return toDao(timereportRepository.findById(id)).orElse(null);
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
    public long getTotalDurationMinutesForEmployeeOrder(long eoId) {
        return timereportRepository.getReportedMinutesForEmployeeorder(eoId).orElse(0L);
    }

    /**
     * Gets a list of Timereports by employee contract id and date.
     */
    public List<TimereportDTO> getTimereportsByDateAndEmployeeContractId(long contractId, LocalDate date) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdate(contractId, date).stream()
                   .sorted(comparing(Timereport::getSequencenumber))
                   .collect(Collectors.toList()));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
     * 1) associated to the given employee contract
     * 2) refdate out of range of the employee contract
     *
     * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
     */
    public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeContract(long employeecontractId) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingEmployeecontractValidity(employeecontractId));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
     * 1) associated to the given employee contract
     * 2) refdate out of range of the associated employee order
     *
     * @return Returns a {@link List} with all {@link TimereportDTO}s, that fulfill the criteria.
     */
    public List<TimereportDTO> getTimereportsOutOfRangeForEmployeeOrder(long employeecontractId) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingEmployeeorderValidity(employeecontractId));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s, that have no duration and are associated to the given ecId.
     */
    public List<TimereportDTO> getTimereportsWithoutDurationForEmployeeContractId(long employeecontractId, LocalDate releaseDate) {
        if(releaseDate == null) {
            releaseDate = LocalDate.of(2000, 1, 1); // HACK to mimic "take everything"
        }
        var timereports = timereportRepository.findAllByEmployeecontractIdAndInvalidRegardingZeroDuration(
            employeecontractId,
            releaseDate
        );
        return toDaoList(timereports.stream().collect(Collectors.toList()));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is open
     */
    public List<TimereportDTO> getOpenTimereportsByEmployeeContractIdBeforeDate(long contractId, LocalDate date) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(
            contractId,
            TIMEREPORT_STATUS_OPEN,
            date
        ));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid before and at the given date
     * 3) status is commited
     */
    public List<TimereportDTO> getCommitedTimereportsByEmployeeContractIdBeforeDate(long contractId, LocalDate date) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndStatusAndReferencedayRefdateIsLessThanEqual(
            contractId,
            TIMEREPORT_STATUS_COMMITED,
            date
        ));
    }

    /**
     * Gets a list of all {@link TimereportDTO}s that fulfill following criteria:
     * 1) associated to the given employee contract id
     * 2) valid after and at the given date
     */
    public List<TimereportDTO> getTimereportsByEmployeeContractIdAfterDate(long contractId, LocalDate dt) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndReferencedayRefdateIsGreaterThanEqual(contractId, dt));
    }

    /**
     * Gets a list of Timereports by employee contract id and two dates.
     */
    public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractId(long contractId, LocalDate begin, LocalDate end) {
        return toDaoList(timereportRepository.findAllByEmployeecontractIdAndReferencedayBetween(contractId, begin, end));
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
    public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndCustomerOrderId(long contractId, LocalDate begin, LocalDate end, long customerOrderId) {
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
        return toDaoList(allTimereports);
    }

    /**
     * Gets a list of Timereports by associated to the given employee contract id, suborder id and the time period between the two given dates.
     */
    public List<TimereportDTO> getTimereportsByDatesAndEmployeeContractIdAndSuborderId(long contractId, LocalDate begin, LocalDate end, long suborderId) {
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
        return toDaoList(allTimereports);
    }

    /**
     * Gets a list of Timereports by employee order id.
     */
    public List<TimereportDTO> getTimereportsByEmployeeOrderId(long employeeOrderId) {
        return toDaoList(timereportRepository.findAll(
            where(matchesEmployeeorderId(employeeOrderId))
                .and(orderedByCustomerorder())
        ));
    }

    /**
     * Gets a list of Timereports by date.
     */
    public List<TimereportDTO> getTimereportsByDate(LocalDate date) {
        return toDaoList(timereportRepository.findAll(
            where(reportedAt(date))
                .and(orderedBySequencenumber())
        ));
    }

    /**
     * Gets a list of timereports, which lay between two dates.
     */
    public List<TimereportDTO> getTimereportsByDates(LocalDate begin, LocalDate end) {
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
        return toDaoList(allTimereports);
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Customerorder} id.
     */
    public List<TimereportDTO> getTimereportsByDatesAndCustomerOrderId(LocalDate begin, LocalDate end, long coId) {
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
        return toDaoList(allTimereports);
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     */
    public List<TimereportDTO> getTimereportsByDatesAndSuborderId(LocalDate begin, LocalDate end, long suborderId) {
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
        return toDaoList(allTimereports);
    }

    /**
     * Gets a list of timereports, which lay between two dates and belong to the given {@link Suborder} id.
     */
    public List<TimereportDTO> getTimereportsByDatesAndSuborderIdOrderedByDateAndEmployeeSign(LocalDate begin, LocalDate end, long suborderId) {
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
        return toDaoList(allTimereports);
    }

    public List<TimereportDTO> getTimereportsByEmployeeorderIdInvalidForDates(LocalDate begin, LocalDate end, Long employeeOrderId) {
        if (end == null) {
            return toDaoList(timereportRepository.findAll(
                where(matchesEmployeeorderId(employeeOrderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        } else {
            return toDaoList(timereportRepository.findAll(
                where(matchesEmployeeorderId(employeeOrderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        }
    }

    public List<TimereportDTO> getTimereportsBySuborderIdInvalidForDates(LocalDate begin, LocalDate end, Long suborderId) {
        if (end == null) {
            return toDaoList(timereportRepository.findAll(
                where(matchesSuborderId(suborderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        } else {
            return toDaoList(timereportRepository.findAll(
                where(matchesSuborderId(suborderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        }
    }

    public List<TimereportDTO> getTimereportsByCustomerOrderIdInvalidForDates(LocalDate begin, LocalDate end, Long customerOrderId) {
        if (end == null) {
            return toDaoList(timereportRepository.findAll(
                where(matchesCustomerorderId(customerOrderId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        } else {
            return toDaoList(timereportRepository.findAll(
                where(matchesCustomerorderId(customerOrderId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        }
    }

    public List<TimereportDTO> getTimereportsByEmployeeContractIdInvalidForDates(LocalDate begin, LocalDate end, Long employeeContractId) {
        if (end == null) {
            return toDaoList(timereportRepository.findAll(
                where(matchesEmployeecontractId(employeeContractId))
                    .and(reportedBefore(begin))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        } else {
            return toDaoList(timereportRepository.findAll(
                where(matchesEmployeecontractId(employeeContractId))
                    .and(reportedNotBetween(begin, end))
                    .and(orderedByReferenceday())
                    .and(orderedByCustomerorder())
            ));
        }
    }

    /**
     * Calls {@link TimereportDAO#save(Timereport, Employee, boolean)} with {@link Employee} = null.
     */
    public void save(Timereport tr) {
        timereportRepository.save(tr);
    }

    /**
     * Deletes the given timereport.
     */
    public void deleteTimereportById(long trId) {
        timereportRepository.deleteById(trId);
    }

    public List<TimereportDTO> getTimereportsByEmployeecontractId(long employeecontractId) {
        return toDaoList(timereportRepository.findAllByEmployeecontractId(employeecontractId));
    }

    public List<TimereportDTO> getTimereportsBySuborderId(long suborderId) {
        return toDaoList(timereportRepository.findAllByEmployeecontractId(suborderId));
    }

    private Optional<TimereportDTO> toDao(Optional<Timereport> timereport) {
        return timereport.filter(this::accessible).map(this::toDao);
    }

    private TimereportDTO toDao(Timereport timereport) {
        return TimereportDTO.builder()
            .id(timereport.getId())
            .referenceday(timereport.getReferenceday().getRefdate())
            .holiday(Optional.ofNullable(timereport.getReferenceday().getHoliday()).orElse(false))
            .duration(timereport.getDuration())
            .durationhours(timereport.getDurationhours())
            .durationminutes(timereport.getDurationminutes())
            .taskdescription(timereport.getTaskdescription())
            .sequencenumber(timereport.getSequencenumber())
            .training(Optional.ofNullable(timereport.getTraining()).orElse(false))
            .status(timereport.getStatus())
            .billable(timereport.getSuborder().getInvoice() == YESNO_YES)
            .employeeorderId(timereport.getEmployeeorder().getId())
            .employeecontractId(timereport.getEmployeecontract().getId())
            .employeeId(timereport.getEmployeecontract().getEmployee().getId())
            .employeeName(timereport.getEmployeecontract().getEmployee().getName())
            .employeeSign(timereport.getEmployeecontract().getEmployee().getSign())
            .customerShortname(timereport.getSuborder().getCustomerorder().getCustomer().getShortname())
            .customerorderId(timereport.getSuborder().getCustomerorder().getId())
            .customerorderSign(timereport.getSuborder().getCustomerorder().getSign())
            .customerorderDescription(timereport.getSuborder().getCustomerorder().getShortdescription())
            .orderType(timereport.getSuborder().getEffectiveOrderType())
            .suborderId(timereport.getSuborder().getId())
            .suborderSign(timereport.getSuborder().getSign())
            .suborderDescription(timereport.getSuborder().getShortdescription())
            .employeeOrderAsString(timereport.getEmployeeorder().getEmployeeOrderAsString())
            .timeReportAsString(timereport.getTimeReportAsString())
            .releasedby(timereport.getReleasedby())
            .released(timereport.getReleased())
            .acceptedby(timereport.getAcceptedby())
            .accepted(timereport.getAccepted())
            .created(timereport.getCreated())
            .createdby(timereport.getCreatedby())
            .lastupdate(timereport.getLastupdate())
            .lastupdatedby(timereport.getLastupdatedby())
            .fitsToContract(timereport.getFitsToContract())
            .build();
    }

    private List<TimereportDTO> toDaoList(List<Timereport> timereports) {
        return timereports.stream()
            .filter(this::accessible)
            .map(this::toDao)
            .collect(Collectors.toList());
    }

    private boolean accessible(Timereport timereport) {
        return authService.isAuthorized(timereport, AccessLevel.READ);
    }

}
