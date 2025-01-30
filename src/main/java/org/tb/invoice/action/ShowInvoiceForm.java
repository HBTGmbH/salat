package org.tb.invoice.action;

import static java.util.stream.StreamSupport.longStream;
import static java.util.stream.StreamSupport.stream;
import static org.tb.common.util.DateUtils.today;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import org.apache.struts.action.ActionForm;
import org.apache.struts.action.ActionMapping;
import org.apache.struts.util.MessageResources;
import org.tb.common.GlobalConstants;
import org.tb.common.util.DateUtils;
import org.tb.invoice.service.ExcelExportService.InvoiceColumnHeaders;

@Getter
@Setter
public class ShowInvoiceForm extends ActionForm implements InvoiceColumnHeaders {

    private static final long serialVersionUID = 1L; // -5141807789236654602L;
    private String invoiceview;
    private String fromDay;
    private String untilDay;
    private String fromMonth;
    private String untilMonth;
    private String fromYear;
    private String untilYear;
    private String orderId;
    private String suborderId;
    private String suborderdescription;
    private boolean timereportsbox;
    private boolean customeridbox;
    private boolean targethoursbox;
    private boolean timereportdescriptionbox;
    private boolean employeesignbox;
    private boolean invoicebox;
    private boolean fixedpricebox;
    private int fromWeek;
    private String[] suborderIdArray;
    private String[] timereportIdArray;
    private String titlesubordertext;
    private String titledatetext;
    private String titleemployeesigntext;
    private String titledescriptiontext;
    private String titletargethourstext;
    private String titleactualdurationtext;
    private String titleactualhourstext;
    private String titleinvoiceattachment;
    private String customername;
    private String customeraddress;
    private boolean showOnlyValid;

    public void init(MessageResources messageResources, Locale locale) {
        timereportsbox = true;
        timereportdescriptionbox = true;
        employeesignbox = true;
        showOnlyValid = true;
        orderId = null;
        suborderId = null;

        LocalDate today = today();
        this.setFromDay("01");
        this.setFromMonth(DateUtils.getMonthShortString(today));
        this.setFromYear(DateUtils.getYearString(today));
        this.setUntilDay(DateUtils.getLastDayOfMonth(today));
        this.setUntilMonth(DateUtils.getMonthShortString(today));
        this.setUntilYear(DateUtils.getYearString(today));
        this.setInvoiceview(GlobalConstants.VIEW_MONTHLY);

        this.setTitleactualhourstext(messageResources.getMessage(locale,"main.invoice.title.actualhours.text"));
        this.setTitleactualdurationtext(messageResources.getMessage(locale,"main.invoice.title.actualduration.text"));
        this.setTitledatetext(messageResources.getMessage(locale,"main.invoice.title.date.text"));
        this.setTitledescriptiontext(messageResources.getMessage(locale,"main.invoice.title.description.text"));
        this.setTitleemployeesigntext(messageResources.getMessage(locale,"main.invoice.title.employeesign.text"));
        this.setTitlesubordertext(messageResources.getMessage(locale,"main.invoice.title.suborder.text"));
        this.setTitletargethourstext(messageResources.getMessage(locale,"main.invoice.title.targethours.text"));
        this.setTitleinvoiceattachment(messageResources.getMessage(locale,"main.invoice.addresshead.text"));
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
        showOnlyValid = false;
        suborderIdArray = new String[0];
        timereportIdArray = new String[0];
    }

    public String getCustomeraddressFormatted() {
        return customeraddress
            .replaceAll("\\r\\n", "<br/>")
            .replaceAll("\\n", "<br/>")
            .replaceAll("\\r", "<br/>");
    }

  @Override
  public String getOrderHeader() {
    return titlesubordertext;
  }

  @Override
  public String getDateHeader() {
    return titledatetext;
  }

  @Override
  public String getEmployeeHeader() {
    return titleemployeesigntext;
  }

  @Override
  public String getTaskDescriptionHeader() {
    return titledescriptiontext;
  }

  @Override
  public String getBudgetHeader() {
    return titletargethourstext;
  }

  @Override
  public String getDurationHeader() {
    return titleactualdurationtext;
  }

  @Override
  public String getHoursHeader() {
    return titleactualhourstext;
  }

  public Long getOrderIdTyped() {
      if(orderId == null || orderId.isBlank()) {
        return null;
      }
      return Long.parseLong(orderId);
  }

  public void setOrderIdTyped(Long orderId) {
      if(orderId == null) {
        this.orderId = null;
      } else {
        this.orderId = orderId.toString();
      }
  }

  public Long getSuborderIdTyped() {
      if(suborderId == null || suborderId.isBlank()) {
        return null;
      }
      return Long.parseLong(suborderId);
  }

  public void setSuborderIdTyped(Long suborderId) {
      if(suborderId == null) {
        this.suborderId = null;
      } else {
        this.suborderId = suborderId.toString();
      }
  }

  public long[] getSuborderIdArrayTyped() {
    return stream(Arrays.spliterator(suborderIdArray), false).filter(id -> id != null && !id.isBlank()).mapToLong(Long::parseLong).toArray();
  }

  public void setSuborderIdArrayTyped(long[] suborderIdArray) {
    this.suborderIdArray = longStream(Arrays.spliterator(suborderIdArray), false).mapToObj(Long::toString).toArray(String[]::new);
  }

  public long[] getTimereportIdArrayTyped() {
    return stream(Arrays.spliterator(timereportIdArray), false).filter(id -> id != null && !id.isBlank()).mapToLong(Long::parseLong).toArray();
  }

  public void setTimereportIdArrayTyped(long[] timereportIdArray) {
    this.timereportIdArray = longStream(Arrays.spliterator(timereportIdArray), false).mapToObj(Long::toString).toArray(String[]::new);
  }

}
