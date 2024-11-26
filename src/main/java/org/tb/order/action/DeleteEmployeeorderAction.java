package org.tb.order.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.dailyreport.service.TimereportService;
import org.tb.employee.domain.Employeecontract;
import org.tb.employee.service.EmployeecontractService;
import org.tb.order.domain.Employeeorder;
import org.tb.order.service.EmployeeorderService;

/**
 * action class for deleting an employee order
 *
 * @author oda
 */
@Component
@RequiredArgsConstructor
public class DeleteEmployeeorderAction extends EmployeeOrderAction<ShowEmployeeOrderForm> {

    private final EmployeeorderService employeeorderService;
    private final EmployeecontractService employeecontractService;
    private final TimereportService timereportService;

    @Override
    public ActionForward executeAuthenticated(ActionMapping mapping, ShowEmployeeOrderForm oldEmployeeOrderForm, HttpServletRequest request, HttpServletResponse response) {
        if (!GenericValidator.isLong(request.getParameter("eoId"))) return mapping.getInputForward();

        long eoId = Long.parseLong(request.getParameter("eoId"));
        Employeeorder eo = employeeorderService.getEmployeeorderById(eoId);
        if (eo == null) return mapping.getInputForward();

        employeeorderService.deleteEmployeeorderById(eoId);

        // create form with necessary values
        ShowEmployeeOrderForm employeeOrderForm = new ShowEmployeeOrderForm();
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
        if (employeecontract == null) {
            employeeOrderForm.setEmployeeContractId(-1);
        } else {
            employeeOrderForm.setEmployeeContractId(employeecontract.getId());
        }
        Long orderId = (Long) request.getSession().getAttribute("currentOrderId");
        employeeOrderForm.setOrderId(orderId);

        employeeOrderForm.setShowActualHours(oldEmployeeOrderForm.getShowActualHours());

        refreshEmployeeOrders(request, employeeOrderForm, employeeorderService, employeecontractService, timereportService);

        // back to employee order display jsp
        return mapping.getInputForward();
    }

}
