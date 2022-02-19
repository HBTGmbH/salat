package org.tb.restful.employeeorders;

import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.restful.suborders.SuborderData;
import org.tb.util.DateUtils;

@RestController("/rest/employeeorders")
@RequiredArgsConstructor
public class EmployeeOrdersService {

    private final EmployeecontractDAO employeecontractDAO;
    private final EmployeeorderDAO employeeorderDAO;
    private final SuborderDAO suborderDAO;

    @GetMapping(path = "/validOrders", produces = APPLICATION_JSON_VALUE)
    @ResponseStatus(OK)
    public List<EmployeeOrderData> getValidEmployeeOrders(
        @RequestParam("refDate") Date refDate,
        @RequestParam("employeeId") long employeeId
    ) {
        if (refDate == null) refDate = DateUtils.today();

        // FIXME check that provided employeeId matches the authenticated user

        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, refDate);
        // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
        // was added to the SuborderDao class!!!
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(ec.getId(), refDate);

        final Date requestedRefDate = refDate; // make final for stream processing
        return suborders.stream()
            .map(s -> {
                Employeeorder eo = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(
                    ec.getId(),
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
