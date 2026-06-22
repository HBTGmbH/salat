package org.tb.dailyreport.service;

import static org.tb.common.GlobalConstants.SUBRORDER_SIGN_VACATION_SPECIAL;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tb.auth.domain.Authorized;
import org.tb.dailyreport.viewhelper.VacationViewHelper;
import org.tb.employee.domain.Employeecontract;
import org.tb.order.service.EmployeeorderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Authorized
public class VacationService {

    private final EmployeeorderService employeeorderService;
    private final TimereportService timereportService;

    public List<VacationViewHelper> getVacations(Employeecontract employeecontract) {
        var vacations = new ArrayList<VacationViewHelper>();
        var orders = employeeorderService.getVacationEmployeeOrders(employeecontract.getId());
        for (var employeeorder : orders) {
            if (SUBRORDER_SIGN_VACATION_SPECIAL.equals(employeeorder.getSuborder().getSign())) continue;
            var vacationView = new VacationViewHelper(employeecontract);
            vacationView.setSuborderSign(employeeorder.getSuborder().getSign());
            if (employeeorder.getDebithours() != null) {
                vacationView.setBudget(employeeorder.getDebithours());
            }
            long vacationMinutes = timereportService.getTotalDurationMinutesForSuborderAndEmployeeContract(
                employeeorder.getSuborder().getId(), employeecontract.getId());
            vacationView.addVacationMinutes(vacationMinutes);
            vacations.add(vacationView);
        }
        return vacations;
    }
}
