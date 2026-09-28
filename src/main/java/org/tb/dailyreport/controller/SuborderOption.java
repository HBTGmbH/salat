package org.tb.dailyreport.controller;

import java.time.LocalDate;
import java.util.List;
import org.tb.order.service.CustomerorderService;
import org.tb.order.service.SuborderService;

/**
 * A suborder the booking form offers.
 *
 * @param sign  the complete order sign alone, the key the command palette shows on its chip (#1158)
 * @param label the sign with the short description, as the form lists it
 */
record SuborderOption(Long id, String sign, String label, String subtext, boolean commentNecessary,
                      boolean trainingFlag) {

    /**
     * What the contract can book on the day: the suborders of its employee orders valid then. The
     * booking form offers exactly these, and so does the palette's {@code buchen} (#1158) — one source,
     * so that the palette never proposes a suborder the form would not show.
     */
    static List<SuborderOption> bookable(CustomerorderService customerorderService, SuborderService suborderService,
            long ecId, LocalDate date) {
        return customerorderService.getCustomerordersWithValidEmployeeOrders(ecId, date)
            .stream()
            .flatMap(order -> suborderService.getSuborderSummaries(ecId, order.getId(), date).stream()
                .map(s -> {
                    var desc = s.shortdescription();
                    var label = (desc != null && !desc.isBlank())
                        ? s.completeOrderSign() + " · " + desc
                        : s.completeOrderSign();
                    var subtext = order.getSign() + " · " + order.getShortdescription()
                        + " · " + order.getCustomer().getShortname();
                    return new SuborderOption(s.id(), s.completeOrderSign(), label, subtext, s.commentNecessary(),
                        s.trainingFlag());
                }))
            .toList();
    }
}
