package org.tb.web.form;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing training in years
 *
 * @author sql
 */
public class ShowTrainingForm extends ActionForm {

    private static final long serialVersionUID = 1L;

    private final String currentYear = DateUtils.getCurrentYearString();
    private String year;
    private long employeeContractId;

    //    private final String order = GlobalConstants.ALL_ORDERS;

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getStartdate() {
        String startdate = year + "-01-01";
        return startdate;

    }

    public String getEnddate() {
        int nextyear = Integer.valueOf(year) + 1;
        String enddate = nextyear + "-01-01";
        return enddate;
    }

    public String getCurrentYear() {
        return currentYear;
    }

    public long getEmployeeContractId() {
        return employeeContractId;
    }

    public void setEmployeeContractId(long employeeContractId) {
        this.employeeContractId = employeeContractId;
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
