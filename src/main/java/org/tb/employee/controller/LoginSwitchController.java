package org.tb.employee.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.tb.auth.service.AuthService;
import org.tb.employee.service.EmployeeService;

@Controller
@RequiredArgsConstructor
@PreAuthorize("not hasRole('RESTRICTED')")
public class LoginSwitchController {

    private final EmployeeService employeeService;
    private final AuthService authService;

    @PostMapping("/auth/switch-login")
    public String switchLogin(@RequestParam Long loginEmployeeId, HttpServletRequest request) {
        var employee = employeeService.getEmployeeById(loginEmployeeId);
        authService.switchLogin(employee.getLoginname());
        var referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/dailyreport/dashboard");
    }

}
