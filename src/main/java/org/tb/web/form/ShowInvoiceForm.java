package org.tb.web.form;

import javax.servlet.http.HttpServletRequest;

import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;

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
    private int     fromWeek;
    private String[] suborderIdArray;
    private String[] timereportIdArray;
    private String titleprinttext;
    private String titlesubordertext;
    private String titlecustomersigntext;
    private String titledatetext;
    private String titleemployeesigntext;
    private String titledescriptiontext;
    private String titletargethourstext;
    private String titleactualhourstext;
    private String titleinvoiceattachment;
    private String customername;
    private String customeraddress;
    private Boolean showOnlyValid;
    
    public String getTitleinvoiceattachment() {
        return titleinvoiceattachment;
    }
    
    public void setTitleinvoiceattachment(String titleinvoiceattachment) {
        this.titleinvoiceattachment = titleinvoiceattachment;
    }
    
    public String getTitleactualhourstext() {
        return titleactualhourstext;
    }
    
    public void setTitleactualhourstext(String titleactualhourstext) {
        this.titleactualhourstext = titleactualhourstext;
    }
    
    public String getTitlecustomersigntext() {
        return titlecustomersigntext;
    }
    
    public void setTitlecustomersigntext(String titlecustomersigntext) {
        this.titlecustomersigntext = titlecustomersigntext;
    }
    
    public String getTitledatetext() {
        return titledatetext;
    }
    
    public void setTitledatetext(String titledatetext) {
        this.titledatetext = titledatetext;
    }
    
    public String getTitledescriptiontext() {
        return titledescriptiontext;
    }
    
    public void setTitledescriptiontext(String titledescriptiontext) {
        this.titledescriptiontext = titledescriptiontext;
    }
    
    public String getTitleemployeesigntext() {
        return titleemployeesigntext;
    }
    
    public void setTitleemployeesigntext(String titleemployeesigntext) {
        this.titleemployeesigntext = titleemployeesigntext;
    }
    
    public String getTitleprinttext() {
        return titleprinttext;
    }
    
    public void setTitleprinttext(String titleprinttext) {
        this.titleprinttext = titleprinttext;
    }
    
    public String getTitlesubordertext() {
        return titlesubordertext;
    }
    
    public void setTitlesubordertext(String titlesubordertext) {
        this.titlesubordertext = titlesubordertext;
    }
    
    public String getTitletargethourstext() {
        return titletargethourstext;
    }
    
    public void setTitletargethourstext(String titletargethourstext) {
        this.titletargethourstext = titletargethourstext;
    }
    
    public boolean isCustomeridbox() {
        return customeridbox;
    }
    
    public void setCustomeridbox(boolean customeridbox) {
        this.customeridbox = customeridbox;
    }
    
    public boolean isEmployeesignbox() {
        return employeesignbox;
    }
    
    public void setEmployeesignbox(boolean employeesignbox) {
        this.employeesignbox = employeesignbox;
    }
    
    public String getFromDay() {
        return fromDay;
    }
    
    public void setFromDay(String fromDay) {
        this.fromDay = fromDay;
    }
    
    public String getFromMonth() {
        return fromMonth;
    }
    
    public void setFromMonth(String fromMonth) {
        this.fromMonth = fromMonth;
    }
    
    public String getFromYear() {
        return fromYear;
    }
    
    public void setFromYear(String fromYear) {
        this.fromYear = fromYear;
    }
    
    public String getInvoiceview() {
        return invoiceview;
    }
    
    public void setInvoiceview(String invoiceview) {
        this.invoiceview = invoiceview;
    }
    
    public String getMwst() {
        return mwst;
    }
    
    public void setMwst(String mwst) {
        this.mwst = mwst;
    }
    
    public String getOrder() {
        return order;
    }
    
    public void setOrder(String order) {
        this.order = order;
    }
    
    public String getSuborder() {
        return suborder;
    }
    
    public void setSuborder(String suborder) {
        this.suborder = suborder;
    }
    
    public String getSuborderdescription() {
        return suborderdescription;
    }
    
    public void setSuborderdescription(String suborderdescription) {
        this.suborderdescription = suborderdescription;
    }
    
    public boolean isTargethoursbox() {
        return targethoursbox;
    }
    
    public void setTargethoursbox(boolean targethoursbox) {
        this.targethoursbox = targethoursbox;
    }
    
    public boolean isTimereportdescriptionbox() {
        return timereportdescriptionbox;
    }
    
    public void setTimereportdescriptionbox(boolean timereportdescriptionbox) {
        this.timereportdescriptionbox = timereportdescriptionbox;
    }
    
    public boolean isTimereportsbox() {
        return timereportsbox;
    }
    
    public void setTimereportsbox(boolean timereportsbox) {
        this.timereportsbox = timereportsbox;
    }
    
    public String getUntilDay() {
        return untilDay;
    }
    
    public void setUntilDay(String untilDay) {
        this.untilDay = untilDay;
    }
    
    public String getUntilMonth() {
        return untilMonth;
    }
    
    public void setUntilMonth(String untilMonth) {
        this.untilMonth = untilMonth;
    }
    
    public String getUntilYear() {
        return untilYear;
    }
    
    public void setUntilYear(String untilYear) {
        this.untilYear = untilYear;
    }
    
    @Override
    public void reset(ActionMapping arg0, HttpServletRequest arg1) {
        // TODO Auto-generated method stub
        super.reset(arg0, arg1);
        timereportsbox = false;
        customeridbox = false;
        targethoursbox = false;
        timereportdescriptionbox = false;
        employeesignbox = false;
        invoicebox = false;
        fixedpricebox = false;
        actualhoursbox = false;
        showOnlyValid = false;
    }
    
    public String[] getSuborderIdArray() {
        return suborderIdArray;
    }
    
    public void setSuborderIdArray(String[] suborderIdArray) {
        this.suborderIdArray = suborderIdArray;
    }
    
    public String[] getTimereportIdArray() {
        return timereportIdArray;
    }
    
    public void setTimereportIdArray(String[] timereportIdArray) {
        this.timereportIdArray = timereportIdArray;
    }
    
    public boolean isInvoicebox() {
        return invoicebox;
    }
    
    public void setInvoicebox(boolean invoicebox) {
        this.invoicebox = invoicebox;
    }
    
    public boolean isFixedpricebox() {
        return fixedpricebox;
    }
    
    public void setFixedpricebox(boolean fixedpricebox) {
        this.fixedpricebox = fixedpricebox;
    }
    
    public boolean isActualhoursbox() {
        return actualhoursbox;
    }
    
    public void setActualhoursbox(boolean actualhoursbox) {
        this.actualhoursbox = actualhoursbox;
    }
    
    public String getCustomeraddress() {
        return customeraddress;
    }
    
    public void setCustomeraddress(String customeraddress) {
        this.customeraddress = customeraddress;
    }
    
    public String getCustomername() {
        return customername;
    }
    
    public void setCustomername(String customername) {
        this.customername = customername;
    }
    
    public String getLayerlimit() {
        return layerlimit;
    }
    
    public void setLayerlimit(String layerlimit) {
        this.layerlimit = layerlimit;
    }

	public Boolean getShowOnlyValid() {
		return showOnlyValid == null ? false : showOnlyValid;
	}

	public void setShowOnlyValid(Boolean showOnlyValid) {
		this.showOnlyValid = showOnlyValid;
	}

	public int getFromWeek() {
		return fromWeek;
	}

	public void setFromWeek(int fromWeek) {
		this.fromWeek = fromWeek;
	}
}
