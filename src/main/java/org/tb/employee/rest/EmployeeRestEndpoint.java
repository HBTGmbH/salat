package org.tb.employee.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.employee.domain.EmployeeFavoriteReport;
import org.tb.employee.persistence.FavoriteReportDAO;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "apikey",
    type = SecuritySchemeType.APIKEY,
    in = SecuritySchemeIn.HEADER,
    paramName = "x-api-key",
    description = "tokenId:secret"
)
@RequestMapping(path = "/rest/employee/")
public class EmployeeRestEndpoint {

  private final EmployeeFavoriteReportMapper favoriteReportMapper;
  private final AuthorizedUser authorizedUser;
  private final FavoriteReportDAO favoriteReportDAO;

  @GetMapping(path = "favorite", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(security = @SecurityRequirement(name = "apikey"))
  public List<EmployeeFavoriteReportDTO> getFavorites() {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    List<EmployeeFavoriteReport> res = favoriteReportDAO.getByEmployeeId();
    return favoriteReportMapper.toTarget(res);
  }

  @PostMapping(path = "favorite", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(security = @SecurityRequirement(name = "apikey"))
  public EmployeeFavoriteReportDTO getFavorites(EmployeeFavoriteReportDTO favoriteReport) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    EmployeeFavoriteReport res = favoriteReportDAO.save(
        favoriteReportMapper.toTarget(favoriteReport));
    return favoriteReportMapper.toTarget(res);
  }


  @DeleteMapping(path = "favorite/{id}", produces = APPLICATION_JSON_VALUE)
  @ResponseStatus(OK)
  @Operation(security = @SecurityRequirement(name = "apikey"))
  public void getFavorites(@PathVariable("id") Long favoriteReportId) {
    if (!authorizedUser.isAuthenticated()) {
      throw new ResponseStatusException(UNAUTHORIZED);
    }
    try {
      favoriteReportDAO.delete(favoriteReportId);
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(NOT_FOUND);
    }
  }
}
