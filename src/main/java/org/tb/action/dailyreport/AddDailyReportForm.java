package org.tb.action.dailyreport;

import static org.tb.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.GlobalConstants.SORT_OF_REPORT_WORK;
import static org.tb.util.DateUtils.today;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

/**
 * Form for adding a timereport
 *
 * @author oda
 */
@Getter
@Setter
public class AddDailyReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -1101951628777959966L;

    private long id;
    private String referenceday;
    private String sortOfReport;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Double hours;
    private Double costs;
    private Boolean training;
    private int selectedHourBegin;
    private int selectedMinuteBegin;
    private int selectedHourEnd;
    private int selectedMinuteEnd;
    private int selectedHourDuration;
    private int selectedMinuteDuration;
    private long orderId;
    private long suborderSignId;
    private long suborderDescriptionId;
    private Long employeeContractId;
    private int numberOfSerialDays;
    private String action;

    public AddDailyReportForm() {
        setReferenceday(DateUtils.format(today()));
    }

    public Double getHours() {
        return DateUtils.calculateTime(this.selectedHourBegin,
                this.selectedMinuteBegin,
                this.selectedHourEnd,
                this.selectedMinuteEnd);
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        boolean dateValid = DateUtils.validateDate(referenceday);
        if (!dateValid) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        // end time must be later than begin time when entering hours:minute
        int begin = selectedHourBegin * MINUTES_PER_HOUR + selectedMinuteBegin;
        int end = selectedHourEnd * MINUTES_PER_HOUR + selectedMinuteEnd;
        if (end < begin) {
            errors.add("selectedHourBegin", new ActionMessage("form.timereport.error.endbeforebegin"));
        }

        return errors;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        try {
            Employeecontract loginEmployeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
            employeeContractId = loginEmployeecontract.getId();
        } catch (Exception e) {
            mapping.findForward("login");
        }

        reset();
    }

    public void reset() {
        comment = "";
        order = "";
        suborder = "";
        status = "";
        sortOfReport = SORT_OF_REPORT_WORK;
        selectedHourDuration = 0;
        selectedMinuteDuration = 0;
        selectedHourBegin = 0;
        selectedMinuteBegin = 0;
        selectedHourEnd = 0;
        selectedMinuteEnd = 0;
        referenceday = DateUtils.format(DateUtils.today());
        hours = 8.0;
        costs = 0.0;
        training = false;
        numberOfSerialDays = 0;
    }

}
