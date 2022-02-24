package org.tb.order.rest;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tb.auth.AuthorizedUser;
import org.tb.common.util.DateUtils;
import org.tb.employee.Employeecontract;
import org.tb.employee.EmployeecontractDAO;
import org.tb.order.Suborder;
import org.tb.order.SuborderDAO;

@RestController("/rest/SubordersService")
@RequiredArgsConstructor
public class SubordersService {

    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;
    private final AuthorizedUser authorizedUser;

    @GetMapping(path = "/availableSuborders", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public List<SuborderData> getAvailableSuborders(
        @RequestParam("refDate") LocalDate refDate
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

        List<SuborderData> suborderResult = new ArrayList<>(suborders.size());
        return suborders.stream()
            .map(s -> {
                String suborderLabel = s.getCustomerorder().getSign() +
                    "/" +
                    s.getSign() +
                    " " +
                    s.getShortdescription();
                return new SuborderData(s.getId(), suborderLabel, s.getCommentnecessary());
            })
            .collect(Collectors.toList());
    }

}
