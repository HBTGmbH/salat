package org.tb.auth.rest;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.common.util.SecureHashUtils;
import org.tb.employee.Employee;
import org.tb.employee.EmployeeDAO;

@RestController("/rest/AuthenticationService")
@RequiredArgsConstructor
public class AuthenticationService {

    private final EmployeeDAO employeeDAO;
    private final HttpServletRequest httpServletRequest;

    @GetMapping(path = "/authenticate")
    @ResponseStatus(OK)
    public void authenticate(
        @RequestParam("username") String username,
        @RequestParam("password") String password
    ) {
        Employee employee = employeeDAO.getLoginEmployee(username);
        if (employee != null && SecureHashUtils.passwordMatches(password, employee.getPassword())) {
            httpServletRequest.getSession().setAttribute("employeeId", employee.getId());
        } else {
            throw new ResponseStatusException(UNAUTHORIZED);
        }
    }

    @GetMapping(path = "/logout")
    @ResponseStatus(OK)
    public void logout() {
        httpServletRequest.getSession().removeAttribute("employeeId");
        httpServletRequest.getSession().invalidate();
    }

}
