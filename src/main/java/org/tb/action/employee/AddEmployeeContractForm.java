package org.tb.action.employee;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employee;
import org.tb.util.DateUtils;

/**
 * Form for adding an employee contract
 *
 * @author oda
 */
@Getter
@Setter
public class AddEmployeeContractForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 4912271204885702837L;

    private long id;
    private String taskdescription;
    private String validFrom;
    private String validUntil;
    private Boolean freelancer;
    private Double dailyworkingtime;
    private Integer yearlyvacation;
    private long employee;
    private long supervisorid;
    private String initialOvertime;
    private String newOvertime;
    private String newOvertimeComment;
    private Boolean hide;
    private String action;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        try {
            Employee loginEmployee = (Employee) request.getSession().getAttribute("loginEmployee");
            employee = loginEmployee.getId();
        } catch (Exception e) {
            mapping.findForward("login");
        }
        taskdescription = "";
        validFrom = DateUtils.getCurrentYearString() + "-01-01";
        validUntil = "";
        freelancer = Boolean.FALSE;
        hide = Boolean.FALSE;
        dailyworkingtime = 8.0;
        initialOvertime = "0.0";
        yearlyvacation = 30;
        newOvertime = "0.0";
    }

}
