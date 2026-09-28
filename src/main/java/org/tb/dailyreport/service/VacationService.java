package org.tb.dailyreport.service;

import static org.tb.common.GlobalConstants.SUBRORDER_SIGN_VACATION_SPECIAL;
import static org.tb.common.util.DateUtils.today;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.dailyreport.domain.VacationInfo;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class VacationService {

    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;

    /**
     * Die Urlaubsauftraege des Vertrags, die heute gelten, und dazu die, die erst in der Zukunft
     * beginnen, auf die aber schon gebucht ist (#1175): wer den Urlaub des naechsten Jahres schon
     * eingetragen hat, soll ihn sehen, bevor der Auftrag gilt. Ein kuenftiger Auftrag ohne Buchung
     * sagt noch nichts und bleibt weg.
     */
    public List<VacationInfo> getVacations(Employeecontract employeecontract) {
        var today = today();
        var orders = new LinkedHashMap<Long, Employeeorder>();
        employeeorderService.getVacationEmployeeOrders(employeecontract.getId())
            .forEach(order -> orders.put(order.getId(), order));
        var future = new LocalDateRange(today.plusDays(1), null);
        employeeorderService.getVacationEmployeeOrders(employeecontract.getId(), future).stream()
            .filter(order -> order.getFromDate().isAfter(today))
            .forEach(order -> orders.putIfAbsent(order.getId(), order));

        var vacations = new ArrayList<VacationInfo>();
        for (var employeeorder : orders.values()) {
            if (SUBRORDER_SIGN_VACATION_SPECIAL.equals(employeeorder.getSuborder().getSign())) continue;
            var suborderSign = employeeorder.getSuborder().getSign();
            var budget = employeeorder.getDebithours();
            long usedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(
                employeeorder.getSuborder().getId(), employeecontract.getId());
            if (employeeorder.getFromDate().isAfter(today) && usedVacationMinutes == 0) continue;
            // schon gebucht, aber noch nicht genommen: gehoert zum Verbrauch und wird eigens ausgewiesen (#1175)
            long plannedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContractAfter(
                employeeorder.getSuborder().getId(), employeecontract.getId(), today);
            vacations.add(new VacationInfo(suborderSign, budget, usedVacationMinutes, plannedVacationMinutes,
                employeeorder.getSuborder().getId(), employeeorder.getFromDate(), employeeorder.getEffectiveUntilDate()));
        }
        return vacations;
    }
}
