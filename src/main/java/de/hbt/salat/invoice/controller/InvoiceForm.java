package de.hbt.salat.invoice.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import de.hbt.salat.invoice.domain.InvoiceGrouping;
import de.hbt.salat.invoice.domain.InvoiceTicketDisplay;
import de.hbt.salat.invoice.service.ExcelExportService.InvoiceColumnHeaders;

@Getter
@Setter
public class InvoiceForm implements InvoiceColumnHeaders {

    private Long orderId;
    private Long suborderId;

    /**
     * Bill what is assigned to this budget plan instead of what is booked on one suborder (#915).
     * Exactly one of the two narrows the invoice; a plan may span several suborders, which is why it
     * is the more precise boundary once bookings are assigned explicitly (#908).
     */
    private Long orderBudgetId;

    /**
     * The plan the form was rendered with. Only when the submitted plan differs from it does the
     * period get prefilled from the plan — otherwise every re-render would overwrite dates the user
     * has since changed.
     */
    private Long previousOrderBudgetId;

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
    private boolean showInactive;
    /**
     * The suborders and bookings deselected in the preview (#1499), each once, sent as one field of
     * comma-separated ids. Deselected rather than selected: what is sent then does not grow with the
     * order, and a large one stays below the parameter limit of the container.
     */
    private List<Long> excludedSuborderIds = new ArrayList<>();
    private List<Long> excludedTimereportIds = new ArrayList<>();

    private InvoiceGrouping grouping = InvoiceGrouping.SUBORDER;
    private InvoiceTicketDisplay ticketdisplay = InvoiceTicketDisplay.COLUMN;
    /** With a grouping by ticket: the bookings under each ticket, or only its subtotal. */
    private boolean ticketlinesbox = true;

    /** Time surcharges as positions of their own (#1470); off, each is part of its booking's hours. */
    private boolean surchargesbox = true;

    /** The tickets that get a group of their own (#1390), meaningful only with {@link #ticketSelectionSubmitted}. */
    private List<String> ticketSelection = new ArrayList<>();

    /**
     * Whether {@link #ticketSelection} came from the form. A list nobody ticked arrives exactly like a
     * list that was never sent, and only the latter means "every booked ticket".
     */
    private boolean ticketSelectionSubmitted;

    /**
     * Set by „Gruppierung anwenden“: rebuild with what was deselected and selected, instead of loading
     * the data afresh with everything in it.
     */
    private boolean regroup;
    private String titlesubordertext;
    private String titledatetext;
    private String titleemployeesigntext;
    private String titledescriptiontext;
    private String titletickettext;
    private String titletargethourstext;
    private String titleactualdurationtext;
    private String titleactualhourstext;
    private String titleinvoiceattachment;
    private String customername;
    private String customeraddress = "";
    /** printed below the address as "Endkunde: …" where not empty (#1536) */
    private String endcustomername = "";

    /** Both narrowings at once has no defined meaning, so it is refused rather than resolved. */
    public boolean isScopeAmbiguous() {
        return suborderId != null && orderBudgetId != null;
    }

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
    public String getTicketHeader() {
        return titletickettext;
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
