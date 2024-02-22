package org.tb.employee.action;

import static org.tb.common.GlobalConstants.VACATION_PER_YEAR;
import static org.tb.common.util.DateUtils.parse;
import static org.tb.common.util.DateUtils.validateDate;
import static org.tb.common.util.DurationUtils.parseDuration;
import static org.tb.common.util.DurationUtils.validateDuration;
import static org.tb.common.util.ValidationUtils.isInRange;
import static org.tb.common.util.ValidationUtils.isPositiveInteger;

import java.time.Duration;
import java.time.LocalDate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionErrors;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.action.ActionMessage;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;

/**
 * Form for adding an employee contract
 *
 * @author oda
 */
@Getter
@Setter
public class AddEmployeeContractForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 4912271204885702837L;

    private String taskdescription;
    private String validFrom;
    private String validUntil;
    private Boolean freelancer;
    private String dailyworkingtime;
    private String yearlyvacation;
    private long employee;
    private long supervisorid;
    private String initialOvertime;
    private String newOvertime;
    private String newOvertimeComment;
    private Boolean hide;
    private String action;

    public LocalDate getValidFromTyped() {
        return parse(validFrom);
    }

    public LocalDate getValidUntilTyped() {
        if(validUntil.isBlank()) return null;
        return parse(validUntil);
    }

    public Duration getDailyworkingtimeTyped() {
        return parseDuration(dailyworkingtime);
    }

    public int getYearlyvacationTyped() {
        return Integer.parseInt(yearlyvacation);
    }

    public Duration getInitialOvertimeTyped() {
        return parseDuration(initialOvertime);
    }

    public Duration getNewOvertimeTyped() {
        return parseDuration(newOvertime);
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        employee = 0;
        supervisorid = 0;
        taskdescription = "";
        validFrom = DateUtils.getCurrentYearString() + "-01-01";
        validUntil = "";
        freelancer = Boolean.FALSE;
        hide = Boolean.FALSE;
        dailyworkingtime = "8:00";
        initialOvertime = "0:00";
        yearlyvacation = String.valueOf(VACATION_PER_YEAR);
        newOvertime = "0:00";
        newOvertimeComment = "";
    }

    // form is used by two different form, so validation must be handled a bit differently
    // TODO split into two different forms and actions maybe?
    @Override
    public ActionErrors validate(ActionMapping mapping, HttpServletRequest request) {
        ActionErrors errors = new ActionErrors();
        if("storeOvertime".equals(request.getParameter("task"))) {
            if (newOvertimeComment.length() > GlobalConstants.EMPLOYEECONTRACT_OVERTIME_COMMENT_MAX_LENGTH) {
                errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.toolong"));
            } else if (newOvertimeComment.isBlank()) {
                errors.add("newOvertimeComment", new ActionMessage("form.employeecontract.error.overtimecomment.missing"));
            }
            if(!validateDuration(newOvertime)) {
                errors.add("newOvertime", new ActionMessage("form.timereport.error.date.wrongformat"));
            } else {
                Duration overtimeMinutes = getNewOvertimeTyped();
                if(overtimeMinutes.isZero()) {
                    errors.add("newOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
                }
            }
        } else {
            if (!validateDate(validFrom)) {
                errors.add("validFrom", new ActionMessage("form.employeecontract.error.validfrom.wrongformat"));
            }

            if(!validUntil.isBlank()) {
                if (!validateDate(validUntil)) {
                    errors.add("validUntil", new ActionMessage("form.employeecontract.error.validuntil.wrongformat"));
                }
            }

            if(!validateDuration(dailyworkingtime)) {
                errors.add("dailyworkingtime", new ActionMessage("form.employeecontract.error.dailyworkingtime.wrongformat"));
            }

            if(!initialOvertime.isBlank()) {
                if(!validateDuration(initialOvertime)) {
                    errors.add("initialOvertime", new ActionMessage("form.employeecontract.error.initialovertime.wrongformat"));
                }
            }

            if(yearlyvacation.isBlank()) {
                errors.add("yearlyvacation", new ActionMessage("form.employeecontract.error.yearlyvacation.wrongformat"));
            } else {
                if(!isPositiveInteger(yearlyvacation)) {
                    errors.add("yearlyvacation", new ActionMessage("form.employeecontract.error.yearlyvacation.wrongformat"));
                } else if(!isInRange(Integer.parseInt(yearlyvacation), 0, GlobalConstants.MAX_VACATION_PER_YEAR)) {
                    errors.add("yearlyvacation", new ActionMessage("form.employeecontract.error.yearlyvacation.wrongformat"));
                }

            }

            if(taskdescription.length() > GlobalConstants.EMPLOYEECONTRACT_TASKDESCRIPTION_MAX_LENGTH) {
                errors.add("taskdescription", new ActionMessage("form.employeecontract.error.taskdescription.toolong"));
            }
        }

        return errors;
    }

}
