package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.bdom.Employeecontract;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;

/**
 * Form for showing timereports in 'daily' display.
 *
 * @author oda
 */
@Getter
@Setter
public class ShowDailyReportForm extends ActionForm {
    private static final long serialVersionUID = 1L;

    /* day, month, year of startdate */
    private String day;
    private String month;
    private String year;

    /* day, month, year of enddate */
    private String lastday;
    private String lastmonth;
    private String lastyear;

    /* yyyy-MM-dd of startdate */
    private String startdate;
    /* yyyy-MM-dd of enddate */
    private String enddate;

    private long employeeContractId;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Double costs;
    private Boolean training;
    private Boolean showOvertimeUntil;
    private int selectedHourBegin;
    private int selectedMinuteBegin;
    private int selectedHourEnd;
    private int selectedMinuteEnd;
    private int selectedWorkHourBegin;
    private int selectedWorkMinuteBegin;
    private int selectedBreakHour;
    private int selectedBreakMinute;

    private long orderId;
    private long trOrderId;
    private long suborderId;
    private long trSuborderId;

    private String view;
    private Boolean avoidRefresh;
    private Boolean showTraining;
    private Boolean showOnlyValid;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        Employeecontract employeecontract;
        if (null != request.getSession().getAttribute("currentEmployeeContract")) {
            employeecontract = (Employeecontract) request.getSession().getAttribute("currentEmployeeContract");
            employeeContractId = employeecontract.getId();
        } else {
            employeeContractId = -1;
        }

        avoidRefresh = false;
        showTraining = false;
        training = false;
        showOvertimeUntil = false;
        showOnlyValid = false;
    }

    @Nonnull
    public Boolean getShowOnlyValid() {
        return showOnlyValid != null && showOnlyValid;
    }

}
