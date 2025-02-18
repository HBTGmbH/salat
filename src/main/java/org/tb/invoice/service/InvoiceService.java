package org.tb.invoice.service;

import static java.lang.Boolean.TRUE;
import static java.time.Duration.ZERO;
import static java.util.Comparator.comparing;
import static org.tb.common.GlobalConstants.YESNO_YES;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.tb.auth.domain.Authorized;
import org.tb.common.LocalDateRange;
import org.tb.dailyreport.domain.TimereportDTO;
import org.tb.dailyreport.service.TimereportService;
import org.tb.invoice.domain.InvoiceData;
import org.tb.invoice.domain.InvoiceSuborder;
import org.tb.invoice.domain.InvoiceTimereport;
import org.tb.order.domain.Customerorder;
import org.tb.order.domain.comparator.SubOrderComparator;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

@Service
@Transactional
@RequiredArgsConstructor
@Authorized(requiresBackoffice = true)
public class InvoiceService {

  private final CustomerorderService customerorderService;
  private final SuborderService suborderService;
  private final TimereportService timereportService;

  public InvoiceData generateInvoiceData(long customerorderId, Optional<Long> suborderId, LocalDateRange invoiceDateRange, InvoiceOptions options) {
    var customerorder = customerorderService.getCustomerorderById(customerorderId);

    var dateFirst = invoiceDateRange.getFrom();
    var dateLast = invoiceDateRange.getUntil();
    var invoiceSuborders = suborderId
        .map(sid -> List.of(suborderService.getSuborderById(sid)))
        .orElseGet(() -> suborderService.getSubordersByCustomerorderId(customerorderId))
        .stream()
        .filter(suborder -> suborder.getValidity().overlaps(invoiceDateRange))
        .filter(suborder -> options.isShowNonInvoicableSuborders() || suborder.getInvoice() == YESNO_YES)
        .filter(suborder -> options.isShowFixedPriceSuborders() || suborder.getFixedPrice() != TRUE)
        .sorted(SubOrderComparator.INSTANCE)
        .map(suborder -> {
          var timereports = timereportService.getTimereportsByDatesAndSuborderId(dateFirst, dateLast, suborder.getId()).stream()
              .sorted(comparing(TimereportDTO::getReferenceday).thenComparing(TimereportDTO::getEmployeeSign))
              .map(timereport -> new InvoiceTimereport(timereport))
              .toList();
          return new InvoiceSuborder(suborder, timereports, options);
        }).toList();

    var totalDuration = invoiceSuborders.stream()
        .map(InvoiceSuborder::getTotalDuration)
        .reduce(Duration::plus)
        .orElse(ZERO);

    var customerOrderSign = options.isUseCustomerDescriptions() && isCustomerDescriptionAvailable(customerorder) ?
        customerorder.getOrder_customer() :
        customerorder.getSignAndDescription();

    return new InvoiceData(options, invoiceDateRange, customerOrderSign, customerorder.getCustomer(), totalDuration, invoiceSuborders);
  }

  private static boolean isCustomerDescriptionAvailable(Customerorder customerorder) {
    return StringUtils.hasText(customerorder.getOrder_customer());
  }

  @Builder
  @Data
  @AllArgsConstructor
  public static class InvoiceOptions {
    private final boolean showNonInvoicableSuborders;
    private final boolean showFixedPriceSuborders;
    private final boolean showTimereports;
    private final boolean showTaskdescriptions;
    private final boolean showEmployee;
    private final boolean shortDescriptions;
    private final boolean useCustomerDescriptions;
    private final boolean showBudget;
  }

}
