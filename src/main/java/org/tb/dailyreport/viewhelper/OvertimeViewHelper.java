package org.tb.dailyreport.viewhelper;

import jakarta.servlet.http.HttpSession;
import org.tb.common.util.DateUtils;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.employee.domain.Employeecontract;

public class OvertimeViewHelper {

    public static void calculateAndSetOvertime(HttpSession session, Employeecontract employeecontract, OvertimeService overtimeService) {
        var overtimeStatus = overtimeService.calculateOvertime(employeecontract.getId(), true);
        overtimeStatus.ifPresentOrElse(
            status -> {
                var overtimeIsNegative = status.getTotal().isNegative();
                session.setAttribute("overtimeIsNegative", overtimeIsNegative);

                String overtimeString = DurationUtils.format(status.getTotal().getDuration());
                session.setAttribute("overtime", overtimeString);

                if(status.getCurrentMonth() != null) {
                    var monthlyOvertimeIsNegative = status.getCurrentMonth().isNegative();
                    session.setAttribute("monthlyOvertimeIsNegative", monthlyOvertimeIsNegative);

                    String monthlyOvertimeString = DurationUtils.format(status.getCurrentMonth().getDuration());
                    session.setAttribute("monthlyOvertime", monthlyOvertimeString);

                    session.setAttribute("overtimeMonth", DateUtils.format(status.getCurrentMonth().getBegin(), "yyyy-MM"));
                } else {
                    session.setAttribute("monthlyOvertimeIsNegative", false);
                    session.setAttribute("monthlyOvertime", "");
                    session.setAttribute("overtimeMonth", "");
                }
            },
            () -> {
                session.setAttribute("overtimeIsNegative", false);
                session.setAttribute("overtime", "");
                session.setAttribute("monthlyOvertimeIsNegative", false);
                session.setAttribute("monthlyOvertime", "");
                session.setAttribute("overtimeMonth", "");
            }
        );
    }
}
