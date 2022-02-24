package org.tb.dailyreport;

import javax.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

/**
 * Form for updating a single timereport within the 'daily' display
 *
 * @author oda
 */
@Getter
@Setter
public class UpdateDailyReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -3593978643330934662L;

    private String day;
    private String month;
    private String year;
    private String action;
    private String comment;
    private String order;
    private String suborder;
    private String status;
    private Boolean training;
    private int selectedDurationHour;
    private int selectedDurationMinute;
    private long trOrderId;
    private long orderId;
    private long trSuborderId;

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        month = null;
        training = false;
    }

}
