package org.tb.employee.rest;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/rest/login/oauth2/code/")
public class LoginEndpoint {

  @GetMapping(path = "", produces = TEXT_PLAIN_VALUE)
  @ResponseStatus(OK)
  @Operation(security = {@SecurityRequirement(name = "bearerAuth")})
  public String getToken(@RequestParam(name = "code", required = false, defaultValue = "false") String code) {

    return code;
  }

}
