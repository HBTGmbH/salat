package org.tb.dailyreport.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.employee.domain.Employeecontract;

@Getter
@Setter
public class ShowReleaseForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1069049121593017810L;

    private Long employeeContractId;
    private Long supervisorId;

    private String releaseDate;
    private String acceptanceDate;
    private String reopenDate;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        releaseDate = today().toString();
        acceptanceDate = today().toString();
        if (employeecontract != null) {
            if (employeecontract.getReportReleaseDate() == null) {
                releaseDate = employeecontract.getValidFrom().toString();
            } else {
                releaseDate = employeecontract.getReportReleaseDate().toString();
            }
            if (employeecontract.getReportAcceptanceDate() == null) {
                acceptanceDate = employeecontract.getValidFrom().toString();
            } else {
                acceptanceDate = employeecontract.getReportAcceptanceDate().toString();
            }
        }
        reopenDate = releaseDate;
    }

}
