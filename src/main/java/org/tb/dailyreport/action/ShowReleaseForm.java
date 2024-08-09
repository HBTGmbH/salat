package org.tb.dailyreport.action;

import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.employee.domain.Employeecontract;

@Getter
public class ShowReleaseForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 1069049121593017810L;
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

    @Setter
    private Long employeeContractId;
    @Setter
    private Long supervisorId;

    private LocalDate releaseDate;
    private LocalDate acceptanceDate;
    private LocalDate reopenDate;

    private String releaseDateString;
    private String acceptanceDateString;
    private String reopenDateString;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
        var releaseDate = today();
        var acceptanceDate = today();
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
        var reopenDate = releaseDate;

        setReleaseDate(releaseDate);
        setAcceptanceDate(acceptanceDate);
        setReopenDate(reopenDate);
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
        if(releaseDate != null) {
            releaseDateString = fmt.format(releaseDate);
        } else {
            releaseDateString = "";
        }
    }

    public void setAcceptanceDate(LocalDate acceptanceDate) {
        this.acceptanceDate = acceptanceDate;
        if(acceptanceDate != null) {
            acceptanceDateString = fmt.format(acceptanceDate);
        } else {
            acceptanceDateString = "";
        }
    }

    public void setReopenDate(LocalDate reopenDate) {
        this.reopenDate = reopenDate;
        if(reopenDate != null) {
            reopenDateString = fmt.format(reopenDate);
        } else {
            reopenDateString = "";
        }
    }

    public void setReleaseDateString(String releaseDateString) {
        this.releaseDateString = releaseDateString;
        if(releaseDateString != null && !releaseDateString.isEmpty()) {
            releaseDate = YearMonth.parse(releaseDateString, fmt).atEndOfMonth();
        } else {
            releaseDate = null;
        }
    }

    public void setAcceptanceDateString(String acceptanceDateString) {
        this.acceptanceDateString = acceptanceDateString;
        if(acceptanceDateString != null && !acceptanceDateString.isEmpty()) {
            acceptanceDate = YearMonth.parse(acceptanceDateString, fmt).atEndOfMonth();
        } else {
            acceptanceDate = null;
        }
    }

    public void setReopenDateString(String reopenDateString) {
        this.reopenDateString = reopenDateString;
        if(reopenDateString != null && !reopenDateString.isEmpty()) {
            reopenDate = YearMonth.parse(releaseDateString, fmt).atEndOfMonth();
        } else {
            reopenDate = null;
        }
    }
}
