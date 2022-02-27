package org.tb.order;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.validator.GenericValidator;
import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.springframework.stereotype.Component;
import org.tb.common.GlobalConstants;
import org.tb.common.util.OptionItem;
import org.tb.employee.Employee;
import org.tb.employee.EmployeeDAO;

@Component
@RequiredArgsConstructor
public class EditEmployeeOrderContentAction extends EmployeeOrderContentAction<AddEmployeeOrderContentForm> {

    private final EmployeeorderDAO employeeorderDAO;
    private final EmployeeDAO employeeDAO;

    @Override
    protected ActionForward executeAuthenticated(ActionMapping mapping,
        AddEmployeeOrderContentForm contentForm, HttpServletRequest request,
        HttpServletResponse response) throws Exception {

        // remove action info
        request.getSession().removeAttribute("actionInfo");

        if ((GenericValidator.isBlankOrNull(request.getParameter("eoId")))
                || (!GenericValidator.isLong(request.getParameter("eoId")))) {
            request.setAttribute("errorMessage",
                    "Associated employee order not found - please call system administrator.");
            mapping.findForward("error");
        }
        String eoIdString = request.getParameter("eoId");
        Long eoId = Long.valueOf(eoIdString);

        // get associated employee order and content from db, if present
        Employeeorder employeeorder = employeeorderDAO
                .getEmployeeorderById(eoId);
        if (employeeorder == null) {
            request.setAttribute("errorMessage",
                    "Associated employee order not found - please call system administrator.");
            mapping.findForward("error");
        } else {
            Employeeordercontent eoContent = employeeorder.getEmployeeOrderContent();

            // Store employee order and content in session
            request.getSession().setAttribute("eoContent", eoContent);
            request.getSession().setAttribute("currentEmployeeOrder", employeeorder);
            request.getSession().setAttribute("currentEmployeeOrderId", employeeorder.getId());

            // content is editable?
            request.getSession().setAttribute("contentIsEditable", isContentEditable(request.getSession(), employeeorder, eoContent));

            // release authorization
            setReleaseAuthorizationInSession(request.getSession(), employeeorder, eoContent);

            if (eoContent != null) {
                request.getSession().setAttribute("contentStatus",
                        "id " + eoContent.getId());

                // initialize form with values
                contentForm.setContact_contract_customer(eoContent
                        .getContact_contract_customer());
                contentForm.setContact_tech_customer(eoContent
                        .getContact_tech_customer());
                contentForm.setContact_contract_hbt_emp_id(eoContent
                        .getContactContractHbt().getId());
                contentForm.setContact_tech_hbt_emp_id(eoContent
                        .getContactTechHbt().getId());
                contentForm.setAdditional_risks(eoContent.getAdditional_risks());
                contentForm.setArrangement(eoContent.getArrangement());
                contentForm.setBoundary(eoContent.getBoundary());
                contentForm.setDescription(eoContent.getDescription());
                contentForm.setProcedure(eoContent.getProcedure());
                contentForm.setQm_process_id(eoContent.getQm_process_id());
                contentForm.setTask(eoContent.getTask());
            } else {
                request.getSession().setAttribute(
                        "contentStatus",
                        getResources(request).getMessage(getLocale(request),
                                "employeeordercontent.newcontent.text"));

                // form presetting for a new eoc
                contentForm.setContact_contract_customer(employeeorder
                        .getSuborder().getCustomerorder()
                        .getResponsible_customer_contractually());
                contentForm.setContact_tech_customer(employeeorder.getSuborder()
                        .getCustomerorder().getResponsible_customer_technical());
                if (employeeorder.getSuborder().getCustomerorder()
                        .getRespEmpHbtContract() != null) {
                    contentForm.setContact_contract_hbt_emp_id(employeeorder
                            .getSuborder().getCustomerorder()
                            .getRespEmpHbtContract().getId());
                }
                if (employeeorder.getSuborder().getCustomerorder()
                        .getResponsible_hbt() != null) {
                    contentForm.setContact_tech_hbt_emp_id(employeeorder
                            .getSuborder().getCustomerorder().getResponsible_hbt()
                            .getId());
                }

                contentForm.setDescription(getResources(request).getMessage(
                        getLocale(request),
                        "employeeordercontent.presetting.description.text"));
                contentForm.setBoundary(getResources(request).getMessage(
                        getLocale(request),
                        "employeeordercontent.presetting.boundary.text"));
                contentForm.setProcedure(getResources(request).getMessage(
                        getLocale(request),
                        "employeeordercontent.presetting.procedure.text"));

            }
        }

        // build collection of qm processes
        List<OptionItem> processes = new ArrayList<>();
        processes.add(new OptionItem(GlobalConstants.QM_PROCESS_ID_OTHER,
                GlobalConstants.QM_PROCESS_OTHER));
        processes
                .add(new OptionItem(
                        GlobalConstants.QM_PROCESS_ID_PA01A_AUFTRAGSGENERIERUNG_WERKVERTRAG,
                        GlobalConstants.QM_PROCESS_PA01A_AUFTRAGSGENERIERUNG_WERKVERTRAG));
        processes
                .add(new OptionItem(
                        GlobalConstants.QM_PROCESS_ID_PA01B_AUFTRAGSGENERIERUNG_DIENSTLEISTUNGSVERTRAG,
                        GlobalConstants.QM_PROCESS_PA01B_AUFTRAGSGENERIERUNG_DIENSTLEISTUNGSVERTRAG));
        processes
                .add(new OptionItem(
                        GlobalConstants.QM_PROCESS_ID_PA09A_AUFTRAGSDURCHFUEHRUNG_WERKVERTRAG,
                        GlobalConstants.QM_PROCESS_PA09A_AUFTRAGSDURCHFUEHRUNG_WERKVERTRAG));
        processes
                .add(new OptionItem(
                        GlobalConstants.QM_PROCESS_ID_PA09B_AUFTRAGSDURCHFUEHRUNG_DIENSTLEISTUNGSVERTRAG,
                        GlobalConstants.QM_PROCESS_PA09B_AUFTRAGSDURCHFUEHRUNG_DIENSTLEISTUNGSVERTRAG));
        processes
                .add(new OptionItem(
                        GlobalConstants.QM_PROCESS_ID_PA09C_AUFTRAGSDURCHFUEHRUNG_1_MANN_GEWERK,
                        GlobalConstants.QM_PROCESS_PA09C_AUFTRAGSDURCHFUEHRUNG_1_MANN_GEWERK));

        request.getSession().setAttribute("qm_processes", processes);

        // store list of employees in session
        List<Employee> allEmployees = employeeDAO.getEmployees();
        // remove technical user admin
        Employee admin = employeeDAO.getEmployeeBySign("adm");
        allEmployees.remove(admin);

        request.getSession().setAttribute("allEmployees", allEmployees);

        return mapping.findForward("success");
    }

}
