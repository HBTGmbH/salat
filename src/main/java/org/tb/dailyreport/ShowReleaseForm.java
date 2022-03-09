package org.tb.dailyreport;

import static org.tb.common.util.DateUtils.getDateAsStringArray;
import static org.tb.common.util.DateUtils.today;

import java.time.LocalDate;
import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.employee.domain.Employeecontract;

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
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        LocalDate releaseDate = today();
        LocalDate acceptanceDate = today();
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

        String[] releaseDateArray = getDateAsStringArray(releaseDate);
        day = releaseDateArray[0];
        month = releaseDateArray[1];
        year = releaseDateArray[2];
        String[] acceptanceDateArray = getDateAsStringArray(acceptanceDate);
        acceptanceDay = acceptanceDateArray[0];
        acceptanceMonth = acceptanceDateArray[1];
        acceptanceYear = acceptanceDateArray[2];
        String[] reopenDateArray = getDateAsStringArray(releaseDate);
        reopenDay = reopenDateArray[0];
        reopenMonth = reopenDateArray[1];
        reopenYear = reopenDateArray[2];
    }

}
