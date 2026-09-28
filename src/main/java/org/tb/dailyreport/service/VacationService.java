package org.tb.dailyreport.service;

import static org.tb.common.GlobalConstants.SUBRORDER_SIGN_VACATION_SPECIAL;
import static org.tb.common.util.DateUtils.today;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.domain.VacationInfo;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.service.EmployeeorderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class VacationService {

    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;

    public List<VacationInfo> getVacations(Employeecontract employeecontract) {
        var vacations = new ArrayList<VacationInfo>();
        var orders = employeeorderService.getVacationEmployeeOrders(employeecontract.getId());
        for (var employeeorder : orders) {
            if (SUBRORDER_SIGN_VACATION_SPECIAL.equals(employeeorder.getSuborder().getSign())) continue;
            var suborderSign = employeeorder.getSuborder().getSign();
            var budget = employeeorder.getDebithours();
            long usedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(
                employeeorder.getSuborder().getId(), employeecontract.getId());
            // schon gebucht, aber noch nicht genommen: gehoert zum Verbrauch und wird eigens ausgewiesen (#1175)
            long plannedVacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContractAfter(
                employeeorder.getSuborder().getId(), employeecontract.getId(), today());
            vacations.add(new VacationInfo(suborderSign, budget, usedVacationMinutes, plannedVacationMinutes,
                employeeorder.getSuborder().getId(), employeeorder.getFromDate(), employeeorder.getEffectiveUntilDate()));
        }
        return vacations;
    }
}
