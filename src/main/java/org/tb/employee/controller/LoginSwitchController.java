package org.tb.employee.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.domain.Authorized;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.auth.service.AuthService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequiredArgsConstructor
@Authorized
public class LoginSwitchController {

    private final EmployeeService employeeService;
    private final AuthService authService;
    private final AuthorizedUser authorizedUser;

    @PostMapping("/auth/switch-login")
    public String switchLogin(@RequestParam Long loginEmployeeId) {
        var employee = employeeService.getEmployeeById(loginEmployeeId);
        authService.switchLogin(employee.getLoginname());
        return "redirect:/dailyreport/dashboard";
    }

    @PostMapping("/auth/exit-impersonation")
    public String exitImpersonation() {
        authService.switchLogin(authorizedUser.getLoginSign());
        return "redirect:/dailyreport/dashboard";
    }

}
