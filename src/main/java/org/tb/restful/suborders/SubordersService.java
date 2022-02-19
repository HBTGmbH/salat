package org.tb.restful.suborders;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.util.DateUtils;

@RestController("/rest/SubordersService")
@RequiredArgsConstructor
public class SubordersService {

    private final EmployeecontractDAO employeecontractDAO;
    private final SuborderDAO suborderDAO;

    @GetMapping(path = "/availableSuborders", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public List<SuborderData> getAvailableSuborders(
        @RequestParam("refDate") Date refDate,
        @RequestParam("employeeId") long employeeId
    ) {
        if (refDate == null) refDate = DateUtils.today();

        // FIXME check that provided employeeId matches the authenticated user

        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(
            employeeId,
            refDate
        );

        // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
        // was added to the SuborderDao class!!!
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(
            ec.getId(),
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
