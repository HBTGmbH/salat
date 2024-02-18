package org.tb.employee.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.tb.common.util.DateUtils.today;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.domain.OvertimeStatus;
import org.tb.employee.persistence.EmployeecontractDAO;
import org.tb.employee.service.OvertimeService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/rest/overtimes")
@Tag(name = "overtime")
public class OvertimeRestEndpoint {

  private final AuthorizedUser authorizedUser;
  private final OvertimeService overtimeService;
  private final EmployeecontractDAO employeecontractDAO;

  @GetMapping(path = "/status", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation
  public OvertimeStatus getStatus(@RequestParam(name = "includeToday", required = false, defaultValue = "false") boolean includeToday) {
    if(!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }

    Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
        authorizedUser.getEmployeeId(),
        today()
    );
    if(employeecontract == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }

    return overtimeService.calculateOvertime(employeecontract.getId(), includeToday).orElseThrow();
  }

}
