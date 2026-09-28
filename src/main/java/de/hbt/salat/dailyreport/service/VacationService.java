package de.hbt.salat.dailyreport.service;

import static de.hbt.salat.common.GlobalConstants.SUBRORDER_SIGN_VACATION_SPECIAL;
import static de.hbt.salat.common.util.DateUtils.today;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.common.LocalDateRange;
import de.hbt.salat.dailyreport.domain.VacationInfo;
import de.hbt.salat.employee.domain.Employeecontract;
import de.hbt.salat.order.domain.Employeeorder;
import de.hbt.salat.order.service.EmployeeorderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class VacationService {

    /** Wie weit geplanter Urlaub in die Zukunft gesucht wird — so weit wie die geplanten Tage der Kontenuebersicht. */
    public static final int PLANNED_HORIZON_YEARS = 2;

    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;

    /**
     * Die Urlaubsauftraege des Vertrags, die heute gelten, und dazu die, die erst in der Zukunft
     * beginnen, auf die aber schon gebucht ist (#1175): wer den Urlaub des naechsten Jahres schon
     * eingetragen hat, soll ihn sehen, bevor der Auftrag gilt. Ein kuenftiger Auftrag ohne Buchung
     * sagt noch nichts und bleibt weg.
     *
     * <p>Sonderurlaub hat kein Budget und steht deshalb nicht je Auftrag da, sondern als eine Zeile
     * am Ende: alles, was im laufenden Jahr genommen und was darueber hinaus schon geplant ist. Ohne
     * Buchung fehlt die Zeile.
     */
    public List<VacationInfo> getVacations(Employeecontract employeecontract) {
        var today = today();
        var yearStart = today.withDayOfYear(1);
        var fromThisYear = employeeorderService.getVacationEmployeeOrders(employeecontract.getId(),
            new LocalDateRange(yearStart, null));

        var orders = new LinkedHashMap<Long, Employeeorder>();
        employeeorderService.getVacationEmployeeOrders(employeecontract.getId()).stream()
            .filter(order -> !isSpecial(order))
            .forEach(order -> orders.put(order.getId(), order));
        fromThisYear.stream()
            .filter(order -> !isSpecial(order) && order.getFromDate().isAfter(today))
            .forEach(order -> orders.putIfAbsent(order.getId(), order));

        var vacations = new ArrayList<VacationInfo>();
        for (var employeeorder : orders.values()) {
            var suborderId = employeeorder.getSuborder().getId();
            long usedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(
                suborderId, employeecontract.getId());
            if (employeeorder.getFromDate().isAfter(today) && usedVacationMinutes == 0) continue;
            // schon gebucht, aber noch nicht genommen: gehoert zum Verbrauch und wird eigens ausgewiesen (#1175)
            long plannedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContractAfter(
                suborderId, employeecontract.getId(), today);
            vacations.add(new VacationInfo(employeeorder.getSuborder().getSign(), employeeorder.getDebithours(),
                usedVacationMinutes, plannedVacationMinutes, List.of(suborderId), employeeorder.getFromDate(),
                employeeorder.getEffectiveUntilDate(), false));
        }

        specialVacation(fromThisYear, yearStart, today).ifPresent(vacations::add);
        return vacations;
    }

    private Optional<VacationInfo> specialVacation(List<Employeeorder> fromThisYear, LocalDate yearStart,
            LocalDate today) {
        var horizon = today.plusYears(PLANNED_HORIZON_YEARS);
        long used = 0;
        long planned = 0;
        var suborderIds = new ArrayList<Long>();
        for (var order : fromThisYear.stream().filter(VacationService::isSpecial).toList()) {
            used += timereportService.getTotalDurationMinutesForEmployeeOrder(order.getId(), yearStart, horizon);
            planned += timereportService.getTotalDurationMinutesForEmployeeOrder(order.getId(), today.plusDays(1), horizon);
            if (!suborderIds.contains(order.getSuborder().getId())) suborderIds.add(order.getSuborder().getId());
        }
        if (used == 0) return Optional.empty();
        return Optional.of(new VacationInfo(SUBRORDER_SIGN_VACATION_SPECIAL, Duration.ZERO, used, planned,
            suborderIds, yearStart, horizon, true));
    }

    private static boolean isSpecial(Employeeorder order) {
        return SUBRORDER_SIGN_VACATION_SPECIAL.equals(order.getSuborder().getSign());
    }
}
