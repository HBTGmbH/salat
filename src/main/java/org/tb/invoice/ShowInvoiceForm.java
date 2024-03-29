package org.tb.invoice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

@Getter
@Setter
public class ShowInvoiceForm extends ActionForm {

    private static final long serialVersionUID = 1L; // -5141807789236654602L;
    private String invoiceview;
    private String fromDay;
    private String untilDay;
    private String fromMonth;
    private String untilMonth;
    private String fromYear;
    private String untilYear;
    private String order;
    private String suborder;
    private String mwst;
    private String suborderdescription;
    private String layerlimit;
    private boolean timereportsbox;
    private boolean customeridbox;
    private boolean targethoursbox;
    private boolean actualhoursbox;
    private boolean timereportdescriptionbox;
    private boolean employeesignbox;
    private boolean invoicebox;
    private boolean fixedpricebox;
    private int fromWeek;
    private String[] suborderIdArray;
    private String[] timereportIdArray;
    private String titleprinttext;
    private String titlesubordertext;
    private String titlecustomersigntext;
    private String titledatetext;
    private String titleemployeesigntext;
    private String titledescriptiontext;
    private String titletargethourstext;
    private String titleactualdurationtext;
    private String titleactualhourstext;
    private String titleinvoiceattachment;
    private String customername;
    private String customeraddress;
    private Boolean showOnlyValid;

    public void init() {
        timereportsbox = true;
        timereportdescriptionbox = true;
        employeesignbox = true;
        actualhoursbox = true;
        showOnlyValid = true;
    }

    @Override
    public void reset(ActionMapping mapping, HttpServletRequest request) {
        timereportsbox = false;
        customeridbox = false;
        targethoursbox = false;
        timereportdescriptionbox = false;
        employeesignbox = false;
        invoicebox = false;
        fixedpricebox = false;
        actualhoursbox = true; // workaround to stay always true - not a real oprion anymore
        showOnlyValid = false;
    }

}
