package de.hbt.salat.employee.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import de.hbt.salat.auth.domain.Authorized;
import de.hbt.salat.auth.domain.AuthorizedUser;
import de.hbt.salat.auth.service.AuthService;
import de.hbt.salat.employee.service.EmployeeService;

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
