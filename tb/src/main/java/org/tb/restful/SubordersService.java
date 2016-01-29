package org.tb.restful;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.tb.bdom.Employeecontract;
import org.tb.bdom.Suborder;
import org.tb.persistence.EmployeecontractDAO;
import org.tb.persistence.SuborderDAO;

@Path("/rest/SubordersService")
public class SubordersService {

	private EmployeecontractDAO employeecontractDAO;
	private SuborderDAO suborderDAO;

	@GET
	@Path("/availableSuborders")
	@Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
	public List<SuborderData> getAvailableSuborders(
			@Context HttpServletRequest request,
			@QueryParam("refDate") Date refDate) {
		
		if(refDate == null) refDate = new Date();

		Long employeeId = (Long) request.getSession()
				.getAttribute("employeeId");
		Employeecontract ec = employeecontractDAO
				.getEmployeeContractByEmployeeIdAndDate(employeeId, refDate);
		// The method getSubordersByEmployeeContractIdWithValidEmployeeOrders
		// was added to the SuborderDao class!!!
		List<Suborder> suborders = suborderDAO
				.getSubordersByEmployeeContractIdWithValidEmployeeOrders(
						ec.getId(), refDate);

		List<SuborderData> suborderResult = new ArrayList<SuborderData>(
				suborders.size());

		for (Suborder suborder : suborders) {
			// Filtering valid suborders with not required description
			String suborderLabel = suborder.getCustomerorder().getSign() + "/"
					+ suborder.getSign() + " " + suborder.getShortdescription();
			suborderResult.add(new SuborderData(suborder.getId(),
					suborderLabel, suborder.getCommentnecessary()));
		}

		return suborderResult;
	}

	public void setSuborderDAO(SuborderDAO suborderDAO) {
		this.suborderDAO = suborderDAO;
	}

	public void setEmployeecontractDAO(EmployeecontractDAO employeecontractDAO) {
		this.employeecontractDAO = employeecontractDAO;
	}

}
