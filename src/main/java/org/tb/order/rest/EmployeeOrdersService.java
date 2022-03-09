package org.tb.order.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;
import org.tb.order.Employeeorder;
import org.tb.order.EmployeeorderDAO;
import org.tb.order.Suborder;
import org.tb.order.SuborderDAO;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "basicAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "basic"
)
@RequestMapping(path = "/rest/employeeorders")
public class EmployeeOrdersService {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final SuborderDAO suborderDAO;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/validOrders", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    @Operation(security = @SecurityRequirement(name = "basicAuth"))
    public List<EmployeeOrderData> getValidEmployeeOrders(
        @RequestParam("refDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate refDate
    ) {
        if(!authorizedUser.isAuthenticated()) {
          throw new ResponseStatusException(UNAUTHORIZED);
        }

        if (refDate == null) refDate = DateUtils.today();
        Employeecontract employeecontract = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
            authorizedUser.getEmployeeId(),
            refDate
        );
        if(employeecontract == null) {
          throw new ResponseStatusException(NOT_FOUND);
        }

        // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
        // was added to the SuborderDao class!!!
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(
            employeecontract.getId(),
            refDate
        );

        final LocalDate requestedRefDate = refDate; // make final for stream processing
        return suborders.stream()
            .map(s -> {
                Employeeorder eo = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
                    employeecontract.getId(),
                    s.getId(),
                    requestedRefDate
                );
                String suborderLabel = s.getCustomerorder().getSign() + "/" + s.getSign() + " " + s.getShortdescription();
                return new EmployeeOrderData(
                    new SuborderData(s.getId(), suborderLabel, s.getCommentnecessary()),
                    eo.getId()
                );
            })
            .collect(Collectors.toList());
    }

}
