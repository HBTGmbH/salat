package org.tb.dailyreport.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

import static java.util.Optional.ofNullable;

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
    }

    public void restoreFromSession(HttpSession session) {
        ofNullable(session.getAttribute("matrix_invoice"))
                .map(Object::toString).map(Boolean::parseBoolean).ifPresent(b -> invoice = b);
        ofNullable(session.getAttribute("matrix_nonInvoice"))
                .map(Object::toString).map(Boolean::parseBoolean).ifPresent(b -> nonInvoice = b);
        ofNullable(session.getAttribute("matrix_startAndBreakTime"))
                .map(Object::toString).map(Boolean::parseBoolean).ifPresent(b -> startAndBreakTime = b);
    }

    public void saveToSession(HttpSession session){
        session.setAttribute("matrix_invoice", invoice);
        session.setAttribute("matrix_nonInvoice", nonInvoice);
        session.setAttribute("matrix_startAndBreakTime", startAndBreakTime);
    }

}
