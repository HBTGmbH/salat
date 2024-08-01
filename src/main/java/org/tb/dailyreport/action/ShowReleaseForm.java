package org.tb.dailyreport.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.employee.domain.Employeecontract;

@Getter
@Setter
public class ShowReleaseForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1069049121593017810L;

    private Long employeeContractId;
    private Long supervisorId;

    private LocalDate releaseDate;
    private LocalDate acceptanceDate;
    private LocalDate reopenDate;

    private String releaseDateString;
    private String acceptanceDateString;
    private String reopenDateString;

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        syncDateFields();
        return super.validate(mapping, request);
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        releaseDate = today();
        acceptanceDate = today();
        if (employeecontract != null) {
            if (employeecontract.getReportReleaseDate() == null) {
                releaseDate = employeecontract.getValidFrom();
            } else {
                releaseDate = employeecontract.getReportReleaseDate();
            }
            if (employeecontract.getReportAcceptanceDate() == null) {
                acceptanceDate = employeecontract.getValidFrom();
            } else {
                acceptanceDate = employeecontract.getReportAcceptanceDate();
            }
        }
        reopenDate = releaseDate;
        syncStringFields();
    }

    private void syncDateFields() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        if(releaseDateString != null && !releaseDateString.isEmpty()) {
            releaseDate = YearMonth.parse(releaseDateString, fmt).atEndOfMonth();
        } else {
            releaseDate = null;
        }
        if(acceptanceDateString != null && !acceptanceDateString.isEmpty()) {
            acceptanceDate = YearMonth.parse(acceptanceDateString, fmt).atEndOfMonth();
        } else {
            acceptanceDate = null;
        }
        if(reopenDateString != null && !reopenDateString.isEmpty()) {
            reopenDate = YearMonth.parse(releaseDateString, fmt).atEndOfMonth();
        } else {
            reopenDate = null;
        }
    }

    private void syncStringFields() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        if(releaseDate != null) {
            releaseDateString = fmt.format(releaseDate);
        } else {
            releaseDateString = "";
        }
        if(acceptanceDate != null) {
            acceptanceDateString = fmt.format(acceptanceDate);
        } else {
            acceptanceDateString = "";
        }
        if(reopenDate != null) {
            reopenDateString = fmt.format(reopenDate);
        } else {
            reopenDateString = "";
        }
    }

}
