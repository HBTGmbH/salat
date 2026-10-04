package de.hbt.salat.dailyreport.service;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static de.hbt.salat.common.exception.ErrorCode.EC_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_BOOKING_NO_CONTRACT;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_CONTRACT_NOT_FOUND;
import static de.hbt.salat.common.exception.ErrorCode.TR_EMPLOYEE_ORDER_NOT_FOUND;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.exception.AuthorizationException;
import de.hbt.salat.common.exception.BusinessRuleException;
import de.hbt.salat.common.exception.InvalidDataException;
import de.hbt.salat.common.util.DateUtils;
import de.hbt.salat.common.util.TicketReferences;
import de.hbt.salat.dailyreport.domain.Workingday;
import de.hbt.salat.dailyreport.domain.Workingday.WorkingDayType;
import de.hbt.salat.dailyreport.persistence.TimereportDAO;
import de.hbt.salat.dailyreport.persistence.WorkingdayDAO;
import de.hbt.salat.dailyreport.rest.DailyReportData;
import de.hbt.salat.dailyreport.rest.DailyWorkingReportData;
import de.hbt.salat.employee.domain.Employee;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.employee.persistence.EmployeecontractDAO;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.persistence.EmployeeorderDAO;

@Service
@AllArgsConstructor
@Transactional
@Authorized
public class DailyWorkingReportService {
    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final WorkingdayDAO workingdayDAO;
    private final WorkingdayService workingdayService;
    private final TimereportService timereportService;
    private final TimereportDAO timereportDAO;
    private final BookingOrderResolver bookingOrderResolver;

    @Transactional(readOnly = true)
    public List<DailyWorkingReportData> getReportsForMonth(YearMonth month, long employeeContractId) {
        return month.atDay(1).datesUntil(month.atEndOfMonth().plusDays(1))
            .map(day -> buildReportForDay(employeeContractId, day))
            .filter(Objects::nonNull)
            .toList();
    }

    private DailyWorkingReportData buildReportForDay(long contractId, LocalDate date) {
        var workingDay = workingdayDAO.getWorkingdayByDateAndEmployeeContractId(date, contractId);
        var timeReports = timereportService.getTimereportsByDateAndEmployeeContractId(contractId, date)
            .stream().map(DailyReportData::valueOf).toList();
        if (workingDay == null && timeReports.isEmpty()) return null;
        var builder = DailyWorkingReportData.builder().date(date).dailyReports(timeReports).type(WorkingDayType.WORKED);
        if (workingDay != null) {
            builder.type(workingDay.getType());
            if (workingDay.getType() != WorkingDayType.NOT_WORKED) {
                var bd = workingDay.getBreakLength();
                builder.startTime(workingDay.getStartOfWorkingDay().toLocalTime());
                builder.breakDuration(LocalTime.of(bd.toHoursPart(), bd.toMinutesPart()));
            }
        }
        return builder.build();
    }

    /**
     * The CSV import of the UI (#1142): the file belongs to {@code employee}, the employee of the contract
     * the page has selected. Which of their contracts a day goes to follows from the day, so a file spanning
     * a change of contract books each day on the contract valid then.
     */
    public ImportReport createReports(List<DailyWorkingReportData> reports, Employee employee)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return importReportOf(reports, employee, false);
    }

    /** As {@link #createReports(List, Employee)}, replacing the bookings of each day and order in the file. */
    public ImportReport updateReports(List<DailyWorkingReportData> reports, Employee employee)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return importReportOf(reports, employee, true);
    }

    private ImportReport importReportOf(List<DailyWorkingReportData> reports, Employee employee, boolean upsert) {
        return importReport(reports.stream()
            .sorted(Comparator.comparing(DailyWorkingReportData::getDate))
            .map(r -> withAssignedOrders(r, booking -> bookingOrderResolver.resolveFor(employee, booking, r.getDate())))
            .map(r -> doCreateReport(r, upsert, contractIdOf(employee, r.getDate())))
            .toList());
    }

    private long contractIdOf(Employee employee, LocalDate day) {
        var contract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employee.getId(), day);
        if (contract == null) {
            throw new InvalidDataException(TR_BOOKING_NO_CONTRACT, employee.getSign(), DateUtils.format(day));
        }
        return contract.getId();
    }

    public ImportReport createReports(List<DailyWorkingReportData> reports)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return importReport(groupByContractId(withAssignedOrders(reports)).entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(r -> doCreateReport(r, false, e.getKey())))
            .sorted(Comparator.comparing(ImportReport.DayResult::date))
            .toList());
    }

    public ImportReport updateReports(List<DailyWorkingReportData> reports)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        return importReport(groupByContractId(withAssignedOrders(reports)).entrySet().stream()
            .flatMap(e -> e.getValue().stream().map(r -> doCreateReport(r, true, e.getKey())))
            .sorted(Comparator.comparing(ImportReport.DayResult::date))
            .toList());
    }

    /** The REST API: an id takes precedence, the signs name the order only without one (#1142). */
    private List<DailyWorkingReportData> withAssignedOrders(List<DailyWorkingReportData> reports) {
        return reports.stream()
            .map(r -> withAssignedOrders(r, booking -> bookingOrderResolver.resolve(booking, r.getDate())))
            .toList();
    }

    /**
     * Assigns every booking of the day its employee order and takes over what follows from the order
     * (#1142). This has to happen before the bookings are grouped by order and compared with the stored
     * ones: a booking named by its signs has no id yet, and one with stale labels would never equal the
     * booking it stands for — in the mode "replace" each would be deleted and created again.
     */
    private static DailyWorkingReportData withAssignedOrders(DailyWorkingReportData report,
            Function<DailyReportData, Employeeorder> orderOf) {
        var bookings = ofNullable(report.getDailyReports()).orElse(List.of()).stream()
            .map(booking -> booking.assignedTo(orderOf.apply(booking), report.getDate()))
            .toList();
        return report.toBuilder().dailyReports(bookings).build();
    }

    private static ImportReport importReport(List<ImportReport.DayResult> days) {
        return new ImportReport(days.stream()
            .filter(ImportReport.DayResult::hasChanges)
            .sorted(Comparator.comparing(ImportReport.DayResult::date))
            .toList());
    }

    private Map<Long, List<DailyWorkingReportData>> groupByContractId(List<DailyWorkingReportData> reports) {
        return reports.stream().collect(groupingBy(this::contractIdForReport));
    }

    private long contractIdForReport(DailyWorkingReportData report) {
        return report.getDailyReports().stream()
            .map(dr -> employeeorderDAO.getEmployeeorderById(dr.getEmployeeorderId()))
            .filter(Objects::nonNull)
            .map(o -> o.getEmployeecontract().getId())
            .filter(Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND));
    }

    private ImportReport.DayResult doCreateReport(DailyWorkingReportData report, boolean upsert, long contractId)
            throws AuthorizationException, InvalidDataException, BusinessRuleException
    {
        var employeecontract = employeecontractDAO.getEmployeecontractById(contractId);
        if(employeecontract == null) {
            throw new AuthorizationException(EC_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        var wdResult = doCreateWorkingDay(report, employeecontract);

        var totals = report.getDailyReports().stream()
            .collect(groupingBy(DailyReportData::getEmployeeorderId))
            .entrySet().stream()
            .map(e -> doCreateDailyReports(report.getDate(), e.getKey(), e.getValue(), upsert, contractId))
            .reduce(new BookingCounts(List.of(), List.of(), List.of()), BookingCounts::add);

        return new ImportReport.DayResult(report.getDate(),
            wdResult.created(), wdResult.dataChanged(),
            wdResult.startTime(), wdResult.breakDuration(),
            totals.created(), totals.deleted(), totals.updated());
    }

    private WorkingDayResult doCreateWorkingDay(DailyWorkingReportData report, Employeecontract employeecontract) {
        var existingWorkingDay = ofNullable(workingdayDAO.getWorkingdayByDateAndEmployeeContractId(
                report.getDate(), requireNonNull(employeecontract.getId(), "ID of contract is required")));

        boolean created = existingWorkingDay.isEmpty();
        var workingDay = existingWorkingDay.orElseGet(() -> {
            var newWorkingDay = new Workingday();
            newWorkingDay.setEmployeecontract(employeecontract);
            newWorkingDay.setRefday(report.getDate());
            return newWorkingDay;
        });

        int oldStartHour = workingDay.getStarttimehour();
        int oldStartMinute = workingDay.getStarttimeminute();
        int oldBreakHour = workingDay.getBreakhours();
        int oldBreakMinute = workingDay.getBreakminutes();
        var oldType = workingDay.getType();

        ofNullable(report.getBreakDuration()).ifPresentOrElse(bd -> {
            workingDay.setBreakhours(bd.getHour());
            workingDay.setBreakminutes(bd.getMinute());
        }, () -> {
            workingDay.setBreakhours(0);
            workingDay.setBreakminutes(0);
        });
        ofNullable(report.getStartTime()).ifPresentOrElse(st -> {
            workingDay.setStarttimehour(st.getHour());
            workingDay.setStarttimeminute(st.getMinute());
        }, () -> {
            workingDay.setStarttimehour(0);
            workingDay.setStarttimeminute(0);
        });
        workingDay.setType(report.getType());
        workingdayService.upsertWorkingday(workingDay);

        boolean dataChanged = !created && (
            oldType != workingDay.getType() ||
            oldStartHour != workingDay.getStarttimehour() ||
            oldStartMinute != workingDay.getStarttimeminute() ||
            oldBreakHour != workingDay.getBreakhours() ||
            oldBreakMinute != workingDay.getBreakminutes()
        );
        boolean worked = workingDay.getType() != WorkingDayType.NOT_WORKED;
        LocalTime startTime = worked ? report.getStartTime() : null;
        LocalTime breakDuration = worked ? report.getBreakDuration() : null;
        return new WorkingDayResult(created, dataChanged, startTime, breakDuration);
    }

    private record WorkingDayResult(boolean created, boolean dataChanged, LocalTime startTime, LocalTime breakDuration) {}

    private BookingCounts doCreateDailyReports(LocalDate day, Long employeeOrderId, List<DailyReportData> incomingBookings, boolean upsert, long contractId) {
        var employeeOrder = employeeorderDAO.getEmployeeorderById(employeeOrderId);
        if (employeeOrder == null) {
            throw new InvalidDataException(TR_EMPLOYEE_ORDER_NOT_FOUND);
        }

        var employeeContract = employeeOrder.getEmployeecontract();
        if (employeeContract == null) {
            throw new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND);
        }
        if (employeeContract.getId() == null || employeeContract.getId() != contractId) {
            throw new InvalidDataException(TR_EMPLOYEE_CONTRACT_NOT_FOUND);
        }

        var existingBookings = storedBookings(day, employeeOrderId);
        var existingBookingsWithoutId = existingBookings
                .stream().map(DailyReportData::withoutId).toList();
        var bookings = withResolvedTicketReferences(incomingBookings, existingBookings);

        var newBookings = bookings.stream().filter(not(existingBookingsWithoutId::contains)).toList();
        var oldBookings = existingBookings.stream().filter(booking -> !bookings.contains(booking.withoutId())).toList();

        if (!oldBookings.isEmpty() && upsert){
            var ids = oldBookings.stream().map(DailyReportData::getId).filter(Objects::nonNull).toList();
            timereportService.deleteTimereportsById(ids);
        }

        newBookings.forEach(booking -> doCreateDailyReport(day, booking, employeeOrder, employeeContract));

        int pairCount = upsert ? Math.min(newBookings.size(), oldBookings.size()) : 0;
        var updatedDetails = IntStream.range(0, pairCount)
            .mapToObj(i -> new ImportReport.UpdatedBookingDetail(toBookingDetail(oldBookings.get(i)), toBookingDetail(newBookings.get(i))))
            .toList();
        var pureCreated = newBookings.subList(pairCount, newBookings.size()).stream().map(DailyWorkingReportService::toBookingDetail).toList();
        var pureDeleted = upsert ? oldBookings.subList(pairCount, oldBookings.size()).stream().map(DailyWorkingReportService::toBookingDetail).toList() : List.<ImportReport.BookingDetail>of();
        return new BookingCounts(pureCreated, pureDeleted, updatedDetails);
    }

    /**
     * Replaces the bookings of one employee order on one day with {@code bookings} — the
     * {@code PUT /list} of the REST API. A booking that does not say anything about its ticket
     * reference keeps the one stored for it, exactly as the import does (#1140).
     */
    public void replaceDailyReports(LocalDate day, Employeeorder employeeorder, List<DailyReportData> bookings) {
        var employeeOrderId = requireNonNull(employeeorder.getId(), "ID of order is required");
        var assigned = bookings.stream().map(booking -> booking.assignedTo(employeeorder, day)).toList();
        var resolved = withResolvedTicketReferences(assigned, storedBookings(day, employeeOrderId));
        timereportService.deleteTimeReports(day, employeeOrderId);
        resolved.forEach(booking -> doCreateDailyReport(day, booking, employeeorder, employeeorder.getEmployeecontract()));
    }

    private List<DailyReportData> storedBookings(LocalDate day, long employeeOrderId) {
        return timereportDAO.getTimereportsByDateAndEmployeeOrderId(day, employeeOrderId)
                .stream().map(DailyReportData::valueOf).toList();
    }

    /**
     * Settles what each incoming booking says about its ticket references (#1140, #1326), so that it can
     * be compared with the stored bookings of the same day and employee order by plain equality.
     *
     * <p>Given references — the list, or the single reference of an older client — are normalized as in
     * the booking form; an empty list or text means "no reference". Missing ones ({@code null}: a file
     * without the column, a client that does not know the field) say nothing about the references, and
     * dropping stored ones for that reason would lose them on every import of an older file. Such a
     * booking therefore takes over the references of a stored booking that equals it apart from them,
     * preferring one not taken yet, so that two stored bookings differing only in their references both
     * keep theirs.
     *
     * <p>Where no stored booking matches, the booking is new or changed, and it stays without
     * references: several bookings per order and day are normal, so which stored one a changed booking
     * replaces cannot be told — the pairing in the import report is a display aid, not a decision.
     */
    private static List<DailyReportData> withResolvedTicketReferences(List<DailyReportData> bookings, List<DailyReportData> stored) {
        var taken = new ArrayList<DailyReportData>();
        var resolved = new ArrayList<DailyReportData>();
        for (var booking : bookings) {
            var given = booking.givenTicketReferences();
            if (given != null) {
                resolved.add(booking.withTicketReferences(TicketReferences.normalize(given)));
                continue;
            }
            var unsaid = booking.withoutId().withTicketReferences(null);
            var matching = stored.stream()
                .filter(candidate -> candidate.withoutId().withTicketReferences(null).equals(unsaid))
                .toList();
            var chosen = matching.stream().filter(not(taken::contains)).findFirst()
                .or(() -> matching.stream().findFirst());
            chosen.ifPresent(taken::add);
            resolved.add(booking.withTicketReferences(chosen.map(DailyReportData::getTicketReferences).orElse(List.of())));
        }
        return resolved;
    }

    private static ImportReport.BookingDetail toBookingDetail(DailyReportData b) {
        return new ImportReport.BookingDetail(b.getSuborderSign(), b.getSuborderLabel(), b.getHours(), b.getMinutes(), b.getComment(),
            b.getTicketReferences() == null ? List.of() : b.getTicketReferences(), b.isTraining());
    }

    private record BookingCounts(List<ImportReport.BookingDetail> created, List<ImportReport.BookingDetail> deleted, List<ImportReport.UpdatedBookingDetail> updated) {
        BookingCounts add(BookingCounts other) {
            return new BookingCounts(
                Stream.concat(created.stream(), other.created.stream()).toList(),
                Stream.concat(deleted.stream(), other.deleted.stream()).toList(),
                Stream.concat(updated.stream(), other.updated.stream()).toList()
            );
        }
    }

    private void doCreateDailyReport(LocalDate day, DailyReportData booking, Employeeorder employeeorder, Employeecontract employeeContract) {
        timereportService.createTimereports(
                requireNonNull(employeeContract.getId(), "ID of contract is required"),
                requireNonNull(employeeorder.getId(), "ID of order is required"),
                day,
                booking.getComment(),
                booking.getTicketReferences(),
                booking.isTraining(),
                booking.getHours(),
                booking.getMinutes(),
                1
        );
    }
}
