package org.tb.statusreport;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;

@Getter
@Setter
public class AddStatusReportForm extends ActionForm {
    private static final long serialVersionUID = 1L; // 2991618984884697261L;

    private Long customerOrderId;
    private Long senderId;
    private Long recipientId;
    private Byte overallStatus;
    private Byte sort;
    private Byte phase;
    private String validFrom;
    private String validUntil;
    private String allocator;
    private Byte trend;
    private Byte trendstatus;
    private String needforaction_text;
    private String needforaction_source;
    private Byte needforaction_status;
    private String aim_text;
    private String aim_source;
    private String aim_action;
    private Byte aim_status;
    private String budget_resources_date_text;
    private String budget_resources_date_source;
    private String budget_resources_date_action;
    private Byte budget_resources_date_status;
    private String riskmonitoring_text;
    private String riskmonitoring_source;
    private String riskmonitoring_action;
    private Byte riskmonitoring_status;
    private String changedirective_text;
    private String changedirective_source;
    private String changedirective_action;
    private Byte changedirective_status;
    private String communication_text;
    private String communication_source;
    private String communication_action;
    private Byte communication_status;
    private String improvement_text;
    private String improvement_source;
    private String improvement_action;
    private Byte improvement_status;
    private String customerfeedback_text;
    private String customerfeedback_source;
    private Byte customerfeedback_status;
    private String miscellaneous_text;
    private String miscellaneous_source;
    private String miscellaneous_action;
    private Byte miscellaneous_status;
    private String notes;

}
