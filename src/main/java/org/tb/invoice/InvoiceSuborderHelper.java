package org.tb.invoice;

import static org.tb.common.util.TimeFormatUtils.decimalFormatMinutes;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.tb.common.util.DurationUtils;
import org.tb.dailyreport.service.TimereportService;
import org.tb.order.domain.Suborder;

public class InvoiceSuborderHelper extends Suborder {

    private static final long serialVersionUID = 1L;
    private final LocalDate fromDate;
    private final LocalDate untilDate;
    private final boolean invoicebox;
    private Suborder suborder;
    private List<InvoiceTimereportHelper> invoiceTimereportViewHelperList;
    private boolean visible;
    private int layer;
    private final long durationInMinutes;

    public InvoiceSuborderHelper(Suborder suborder, TimereportService timereportService, LocalDate fromDate, LocalDate untilDate, boolean invoicebox) {
        this.suborder = suborder;
        this.visible = true;
        this.fromDate = fromDate;
        this.untilDate = untilDate;
        this.invoicebox = invoicebox;

        var visitor = new InvoiceSuborderActualHoursVisitor(timereportService, fromDate, untilDate, invoicebox);
        acceptVisitor(visitor);
        this.durationInMinutes = visitor.getTotalMinutes();
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getActualDurationPrint() {
        return getActualDurationHelper(true);
    }

    public String getActualDuration() {
        return getActualDurationHelper(false);
    }

    public String getActualDurationHelper(boolean print) {
        long totalActualminutesHelper = getTotalActualminutesHelper(print);
        return DurationUtils.format(Duration.ofMinutes(totalActualminutesHelper));
    }

    public long getDurationInMinutes() {
        return durationInMinutes;
    }

    public String getActualHours() {
        return getActualhoursHelper(false);
    }

    public String getActualHoursPrint() {
        return getActualhoursHelper(true);
    }

    private String getActualhoursHelper(boolean print) {
        long actualminutes = getTotalActualminutesHelper(print);
        return decimalFormatMinutes(actualminutes);
    }

    public long getTotalActualminutes() {
        return getTotalActualminutesHelper(false);
    }

    public long getTotalActualminutesPrint() {
        return getTotalActualminutesHelper(true);
    }

    private long getTotalActualminutesHelper(boolean print) {
        return invoiceTimereportViewHelperList.stream()
            .filter(v -> !print || v.isVisible())
            .map(v -> Duration.ofHours(v.getDurationhours()).plusMinutes(v.getDurationminutes()))
            .reduce(Duration.ZERO, Duration::plus)
            .toMinutes();
    }

    public String getDebithoursString() {
        String result = "";
        if (suborder.getDebithours() != null && !suborder.getDebithours().isZero()) {
            result = DurationUtils.format(suborder.getDebithours());
            // add decimal value - helps the backoffice
            result += " (" + DurationUtils.decimalFormat((suborder.getDebithours())) + ")";
        }
        return result;
    }

    public List<InvoiceTimereportHelper> getInvoiceTimereportViewHelperList() {
        return invoiceTimereportViewHelperList;
    }

    public void setInvoiceTimereportViewHelperList(List<InvoiceTimereportHelper> invoiceTimereportViewHelperList) {
        this.invoiceTimereportViewHelperList = invoiceTimereportViewHelperList;
    }

    public String getCompleteOrderSign() {
        return suborder.getCompleteOrderSign();
    }

    public String getCompleteOrderDescription(boolean shortDescription) {
        return suborder.getCompleteOrderDescription(shortDescription);
    }

    @Override
    public Long getId() {
        return suborder.getId();
    }

    @Override
    public String getSuborder_customer() {
        return suborder.getSuborder_customer();
    }

    @Override
    public char getInvoice() {
        return suborder.getInvoice();
    }

    @Override
    public String getSign() {
        return suborder.getCompleteOrderSign();
    }

    @Override
    public String getDescription() {
        return suborder.getDescription();
    }

    @Override
    public Boolean getStandard() {
        return suborder.getStandard();
    }

    @Override
    public Byte getDebithoursunit() {
        return suborder.getDebithoursunit();
    }

    @Override
    public Boolean getHide() {
        return suborder.getHide();
    }

    @Override
    public Boolean getTrainingFlag() {
        return suborder.getTrainingFlag();
    }

    @Override
    public Boolean getFixedPrice() {
        return suborder.getFixedPrice();
    }

    @Override
    public String getShortdescription() {
        return suborder.getShortdescription();
    }

    @Override
    public LocalDate getFromDate() {
        return suborder.getFromDate();
    }

    @Override
    public String getTimeString() {
        return suborder.getTimeString();
    }

    @Override
    public boolean getOpenEnd() {
        return suborder.getOpenEnd();
    }

    @Override
    public LocalDate getUntilDate() {
        return suborder.getUntilDate();
    }

    @Override
    public String getFormattedUntilDate() {
        return suborder.getFormattedUntilDate();
    }

    @Override
    public String getSignAndDescription() {
        return suborder.getSignAndDescription();
    }

    @Override
    public String getSignAndDescriptionWithExpirationDate() {
        return suborder.getSignAndDescriptionWithExpirationDate();
    }

    @Override
    public Duration getDebithours() {
        return suborder.getDebithours();
    }
}
