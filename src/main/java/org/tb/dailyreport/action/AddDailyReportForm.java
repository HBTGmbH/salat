package org.tb.dailyreport.action;

import static org.tb.common.GlobalConstants.DEFAULT_WORK_DAY_START;
import static org.tb.common.GlobalConstants.MINUTES_PER_HOUR;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.common.util.DateUtils;
import org.tb.employee.domain.Employeecontract;

/**
 * Form for adding a timereport
 *
 * @author oda
 */
@Getter
@Setter
@Slf4j
public class AddDailyReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -1101951628777959966L;

    private long id;
    private String referenceday;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Boolean training;
    private long selectedHourBegin;
    private long selectedMinuteBegin;
    private long selectedHourEnd;
    private long selectedMinuteEnd;
    private long selectedHourDuration;
    private long selectedMinuteDuration;
    private int selectedHourBeginDay;
    private int selectedMinuteBeginDay;
    private long orderId;
    private long suborderSignId;
    private long suborderDescriptionId;
    private Long employeeContractId;
    private int numberOfSerialDays;
    private String action;
    private boolean showAllMinutes;

    public AddDailyReportForm() {
        referenceday = DateUtils.format(today());
    }

    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        boolean dateValid = DateUtils.validateDate(referenceday);
        if (!dateValid) {
            errors.add("referenceday", new ActionMessage("form.timereport.error.date.wrongformat"));
        }

        return errors;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        try {
            Employeecontract loginEmployeecontract = (Employeecontract) request.getSession().getAttribute("loginEmployeeContract");
            employeeContractId = loginEmployeecontract.getId();
        } catch (Exception e) {
            log.error("reset threw exception.", e);
        }

        reset();
    }

    public void reset() {
        comment = "";
        order = "";
        suborder = "";
        status = "";
        selectedHourDuration = 0;
        selectedMinuteDuration = 0;
        selectedHourBegin = 0;
        selectedMinuteBegin = 0;
        selectedHourEnd = 0;
        selectedMinuteEnd = 0;
        selectedHourBeginDay = DEFAULT_WORK_DAY_START;
        selectedMinuteBeginDay = 0;
        referenceday = DateUtils.format(DateUtils.today());
        training = false;
        numberOfSerialDays = 0;
        showAllMinutes = false;
    }

    public void recalcDurationFromBeginAndEnd() {
        Duration duration = Duration.ZERO;
        if(selectedHourEnd < selectedHourBegin || selectedHourEnd == selectedHourBegin && selectedMinuteEnd < selectedMinuteBegin) {
            // user selected a time in the morning and a overnight period is the result
            duration = duration.plusHours(24);
        }
        duration = duration.plusHours(selectedHourEnd)
            .plusMinutes(selectedMinuteEnd)
            .minusHours(selectedHourBegin)
            .minusMinutes(selectedMinuteBegin);
        this.selectedHourDuration = duration.toHoursPart();
        this.selectedMinuteDuration = duration.toMinutesPart();
    }

    public void recalcEndFromBeginAndDuration() {
        Duration duration = Duration.ofHours(selectedHourBegin)
            .plusMinutes(selectedMinuteBegin)
            .plusHours(selectedHourDuration)
            .plusMinutes(selectedMinuteDuration);
        this.selectedHourEnd = duration.toHoursPart() % 24; // handle overnight period
        this.selectedMinuteEnd = duration.toMinutesPart();
    }

    public boolean isNewTimeReport() {
        return id == 0;
    }

    public void setAsNewTimereport() {
        id = 0;
    }

}
