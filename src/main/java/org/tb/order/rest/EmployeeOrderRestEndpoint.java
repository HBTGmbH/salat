package org.tb.order.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.domain.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.AuthorizedEmployee;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.domain.Suborder;
import org.tb.order.service.EmployeeorderService;
import org.tb.order.service.SuborderService;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = { "/api/employee-orders", "/rest/employee-orders" })
@Tag(name = "order", description = "API zur Abfrage von Mitarbeiteraufträgen")
public class EmployeeOrderRestEndpoint {

    private final EmployeecontractService employeecontractService;
    private final EmployeeorderService employeeorderService;
    private final SuborderService suborderService;
    private final AuthorizedUser authorizedUser;
    private final AuthorizedEmployee authorizedEmployee;

    @GetMapping(path = "/list", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(
        summary = "Gibt gültige Mitarbeiteraufträge zurück",
        description = "Liefert eine Liste aller gültigen Mitarbeiteraufträge für den authentifizierten Benutzer zum angegebenen Referenzdatum",
        tags = {"order"}
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Erfolgreiche Abfrage", 
            content = @Content(mediaType = APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = EmployeeOrderData.class))),
        @ApiResponse(responseCode = "401", description = "Nicht autorisiert"),
        @ApiResponse(responseCode = "404", description = "Mitarbeitervertrag nicht gefunden")
    })
    public List<EmployeeOrderData> getValidEmployeeOrders(
        @Parameter(description = "Referenzdatum für die Gültigkeit der Aufträge", required = true)
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate
    ) {
        if(!authorizedUser.isAuthenticated()) {
          throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (refDate == null) refDate = DateUtils.today();
        Employeecontract employeecontract = employeecontractService.getEmployeeContractValidAt(
            authorizedEmployee.getEmployeeId(),
            refDate
        );
        if(employeecontract == null) {
          throw new ResponseStatusException(NOT_FOUND);
        }

        // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
        // was added to the SuborderDao class!!!
        List<Suborder> suborders = suborderService.getSubordersByEmployeeContractIdWithValidEmployeeOrders(
            employeecontract.getId(),
            refDate
        );

        final LocalDate requestedRefDate = refDate; // make final for stream processing
        return suborders.stream()
            .map(s -> {
                Employeeorder eo = employeeorderService.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
                    employeecontract.getId(),
                    s.getId(),
                    requestedRefDate
                );
                String suborderLabel = s.getCompleteOrderDescription(true, false);
                return new EmployeeOrderData(
                    new SuborderData(s.getId(), suborderLabel, s.getCommentnecessary()),
                    eo.getId()
                );
            })
            .collect(Collectors.toList());
    }

}
