package org.tb.dailyreport;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.common.util.DateUtils;
import org.tb.employee.Employeecontract;

/**
 * Form for showing training in years
 *
 * @author sql
 */
@Getter
@Setter
public class ShowTrainingForm extends ActionForm {
    private static final long serialVersionUID = 1L;

    private final String currentYear = DateUtils.getCurrentYearString();
    private String year;
    private long employeeContractId;

    public String getStartdate() {
        return year + "-01-01";
    }

    public String getEnddate() {
        int nextyear = Integer.parseInt(year) + 1;
        return nextyear + "-01-01";
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract;
        if (null != request.getSession().getAttribute("currentEmployeeContract")) {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            employeeContractId = employeecontract.getId();
        } else {
            employeeContractId = -1;
        }
        year = currentYear;
    }

}
