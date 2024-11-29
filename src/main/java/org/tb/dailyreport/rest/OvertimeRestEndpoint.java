package org.tb.dailyreport.rest;

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
import org.tb.auth.domain.AuthorizedUser;
import org.tb.dailyreport.domain.OvertimeStatus;
import org.tb.dailyreport.service.OvertimeService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/overtimes", "/rest/overtimes" })
@Tag(name = "overtime")
public class OvertimeRestEndpoint {

  private final AuthorizedUser authorizedUser;
  private final OvertimeService overtimeService;
  private final EmployeecontractService employeecontractService;

  @GetMapping(path = "/status", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation
  public OvertimeStatus getStatus(@RequestParam(required = false, defaultValue = "false") boolean includeToday) {
    if(!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }

    Employeecontract employeecontract = employeecontractService.getEmployeeContractValidAt(
        authorizedUser.getEmployeeId(),
        today()
    );
    if(employeecontract == null) {
      throw new ResponseStatusException(NOT_FOUND);
    }

    return overtimeService.calculateOvertime(employeecontract.getId(), includeToday).orElseThrow();
  }

}
