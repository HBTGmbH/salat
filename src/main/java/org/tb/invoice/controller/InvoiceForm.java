package org.tb.invoice.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.tb.invoice.service.ExcelExportService.InvoiceColumnHeaders;

@Getter
@Setter
public class InvoiceForm implements InvoiceColumnHeaders {

    private Long orderId;
    private Long suborderId;
    private String invoiceview = "month";

    /** Monthly mode: value from {@code <input type="month">}, format {@code yyyy-MM}. */
    private String fromYearMonth;

    /** Custom range: from date, bound from {@code <input type="date">} via ISO format. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Custom range: until date, bound from {@code <input type="date">} via ISO format. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate untilDate;

    private String suborderdescription = "longdescription";
    private boolean timereportsbox = true;
    private boolean customeridbox;
    private boolean targethoursbox;
    private boolean timereportdescriptionbox = true;
    private boolean employeesignbox = true;
    private boolean invoicebox;
    private boolean fixedpricebox;
    private boolean showOnlyValid = true;
    private List<Long> suborderIdArray = new ArrayList<>();
    private List<Long> timereportIdArray = new ArrayList<>();
    private String titlesubordertext;
    private String titledatetext;
    private String titleemployeesigntext;
    private String titledescriptiontext;
    private String titletargethourstext;
    private String titleactualdurationtext;
    private String titleactualhourstext;
    private String titleinvoiceattachment;
    private String customername;
    private String customeraddress = "";

    public String getCustomeraddressFormatted() {
        if (customeraddress == null) return "";
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
}
