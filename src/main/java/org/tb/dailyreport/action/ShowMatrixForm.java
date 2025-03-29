package org.tb.dailyreport.action;

import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.upload.FormFile;

@Getter
@Setter
public class ShowMatrixForm extends ActionForm {
    private static final long serialVersionUID = 1L; // -5141807789236654602L;

    private String fromDay;
    private String untilDay;
    private String fromMonth;
    private String untilMonth;
    private String fromYear;
    private String untilYear;
    private FormFile importFile;
    private String importMode;
    private long employeeContractId = -1;
    private String order;
    private String suborder;
    private String matrixview;
    private long orderId;
    private Boolean invoice = Boolean.TRUE;
    private Boolean nonInvoice = Boolean.TRUE;
    private Boolean startAndBreakTime = Boolean.TRUE;

}
