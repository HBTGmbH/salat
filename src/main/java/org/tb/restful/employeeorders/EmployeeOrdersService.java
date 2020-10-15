package org.tb.restful.employeeorders;

import org.tb.bdom.Employeecontract;
import org.tb.bdom.Employeeorder;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.EmployeeorderDAO;
import org.tb.persistence.SuborderDAO;
import org.tb.restful.suborders.SuborderData;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Path("/rest/employeeorders")
public class EmployeeOrdersService {

    private EmployeecontractDAO employeecontractDAO;
    private EmployeeorderDAO employeeorderDAO;
    private SuborderDAO suborderDAO;

    public void setEmployeeorderDAO(EmployeeorderDAO employeeorderDAO) {
        this.employeeorderDAO = employeeorderDAO;
    }

    @GET
    @Path("/validOrders")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public List<EmployeeOrderData> getValidEmployeeOrders(
            @Context HttpServletRequest request,
            @QueryParam("refDate") Date refDate) {

        if (refDate == null) refDate = new Date();

        Long employeeId = (Long) request.getSession().getAttribute("employeeId");
        Employeecontract ec = employeecontractDAO.getEmployeeContractByEmployeeIdAndDate(employeeId, refDate);
        // The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
        // was added to the SuborderDao class!!!
        List<Suborder> suborders = suborderDAO.getSubordersByEmployeeContractIdWithValidEmployeeOrders(ec.getId(), refDate);

        List<EmployeeOrderData> employeeorderResult = new ArrayList<>(suborders.size());

        for (Suborder suborder : suborders) {
            // Filtering valid suborders with not required description
            Employeeorder eo = employeeorderDAO.getEmployeeorderByEmployeeContractIdAndSuborderIdAndDate(ec.getId(), suborder.getId(), refDate);
            String suborderLabel = suborder.getCustomerorder().getSign() + "/" + suborder.getSign() + " " + suborder.getShortdescription();
            EmployeeOrderData data = new EmployeeOrderData();
            data.setEmployeeorderId(eo.getId());
            data.setSuborder(new SuborderData(suborder.getId(), suborderLabel, suborder.getCommentnecessary()));
            employeeorderResult.add(data);
        }

        return employeeorderResult;
    }

    public void setSuborderDAO(SuborderDAO suborderDAO) {
        this.suborderDAO = suborderDAO;
    }

    public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
        this.employeecontractDAO = employeecontractDAO;
    }

}
