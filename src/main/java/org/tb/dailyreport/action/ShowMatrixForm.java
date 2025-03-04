package org.tb.dailyreport.action;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
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
    private Long employeeContractId;
    private String order;
    private String suborder;
    private String matrixview;
    private long orderId;
    private Boolean invoice;
    private Boolean nonInvoice;
    private Boolean startAndBreakTime;

    @Override
    public void reset(ActionMapping arg0, HttpServletRequest arg1) {
        invoice = false;
        nonInvoice = false;
        startAndBreakTime = false;
        importFile = null;
        importMode = null;
    }

}
