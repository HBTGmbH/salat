package org.tb.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.helper.TimereportHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Getter
@Setter
public class ShowReleaseForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1069049121593017810L;

    Long employeeContractId;
    Long supervisorId;
    private String day;
    private String month;
    private String year;
    private String acceptanceDay;
    private String acceptanceMonth;
    private String acceptanceYear;
    private String reopenDay;
    private String reopenMonth;
    private String reopenYear;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        TimereportHelper th = new TimereportHelper();
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        Date releaseDate = new Date();
        Date acceptanceDate = new Date();
        if (employeecontract != null) {
            //			employeeContractId = employeecontract.getId();
            releaseDate = employeecontract.getReportReleaseDate();
            acceptanceDate = employeecontract.getReportAcceptanceDate();
            if (releaseDate == null) {
                releaseDate = employeecontract.getValidFrom();
            }
            if (acceptanceDate == null) {
                acceptanceDate = employeecontract.getValidFrom();
            }
        }

        String[] releaseDateArray = th.getDateAsStringArray(releaseDate);
        day = releaseDateArray[0];
        month = releaseDateArray[1];
        year = releaseDateArray[2];
        String[] acceptanceDateArray = th.getDateAsStringArray(acceptanceDate);
        acceptanceDay = acceptanceDateArray[0];
        acceptanceMonth = acceptanceDateArray[1];
        acceptanceYear = acceptanceDateArray[2];
        String[] reopenDateArray = th.getDateAsStringArray(releaseDate);
        reopenDay = reopenDateArray[0];
        reopenMonth = reopenDateArray[1];
        reopenYear = reopenDateArray[2];
    }

}
