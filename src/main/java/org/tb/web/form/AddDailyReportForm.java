package org.tb.web.form;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.tb.GlobalConstants;
import org.tb.bdom.Employeecontract;
import org.tb.util.DateUtils;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;

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
    private String jiraTicketKey;
    private String newJiraTicketKey;
    private int numberOfSerialDays;
    private String action;

    public AddDailyReportForm() {
        SimpleDateFormat format = new SimpleDateFormat(GlobalConstants.DEFAULT_DATE_FORMAT);
        setReferenceday(format.format(new Date()));
    }

    public Double getHours() {
        return DateUtils.calculateTime(this.selectedHourBegin,
                this.selectedMinuteBegin,
                this.selectedHourEnd,
                this.selectedMinuteEnd);
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
        jiraTicketKey = "-1";
        newJiraTicketKey = "";
        comment = "";
        order = "";
        suborder = "";
        status = "";
        sortOfReport = "W";
        selectedHourDuration = 0;
        selectedMinuteDuration = 0;
        selectedHourBegin = 0;
        selectedMinuteBegin = 0;
        selectedHourEnd = 0;
        selectedMinuteEnd = 0;
        referenceday = DateUtils.getSqlDateString(new java.util.Date()); // 'yyyy-mm-dd'
        hours = 8.0;
        costs = 0.0;
        training = false;
        numberOfSerialDays = 0;
    }

}
